package com.yourco.lending.server

import com.yourco.lending.api.ApiError
import com.yourco.lending.api.LeadApi
import com.yourco.lending.api.LeadReceipt
import com.yourco.lending.api.LeadSubmission
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
data class RejectRequest(val reason: String)

@Serializable
data class EraseRequest(val email: String)

@Serializable
data class EraseResult(val leadsErased: Int)

private data object Admin

private val LEADS_LIMIT = RateLimitName("leads")
private const val LENDER_AUTH = "lender"
private const val ADMIN_AUTH = "admin"

/** Lead submissions are a few hundred bytes. Anything far larger is junk. */
private const val MAX_BODY_BYTES = 16L * 1024

/**
 * The HTTP API.
 *
 *   POST /leads                         the app (no login; rate limited)
 *   GET  /lender/leads[?status=new]     lender login: list leads, anonymized
 *   GET  /lender/leads/{id}             one lead; contact details only once accepted
 *   POST /lender/leads/{id}/accept      releases contact details
 *   POST /lender/leads/{id}/reject      {"reason": "..."}
 *   POST /lender/leads/{id}/funded
 *   POST /admin/erase-personal-data     admin login: {"email": "..."} for deletion requests
 *   GET  /health
 */
fun Application.leadServer(
    service: LeadService,
    adminKeySha256: String?,
    backgroundJobs: Boolean = true,
    leadsPerMinute: Int = 30,
) {
    val log = LoggerFactory.getLogger("LeadServer")

    install(ContentNegotiation) { json(LeadApi.json) }
    install(RequestBodyLimit) { bodyLimit { MAX_BODY_BYTES } }
    // Logs method, path, and status only. Request bodies (personal data) are never logged.
    install(CallLogging)

    install(StatusPages) {
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge, ApiError("That request is too large."))
        }
        // Parse errors can quote the request body, so the cause is deliberately not logged.
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("That request couldn't be read.")) }
        exception<ContentTransformationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("That request couldn't be read.")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("That request couldn't be read.")) }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on {}", call.request.local.uri, cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong on our side. Please try again."))
        }
    }

    install(RateLimit) {
        register(LEADS_LIMIT) {
            rateLimiter(limit = leadsPerMinute, refillPeriod = 1.minutes)
            // Behind a hosting proxy this is the proxy's address, so the limit acts as a
            // global cap. That's deliberate: client-supplied forwarding headers can be faked.
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    install(Authentication) {
        bearer(LENDER_AUTH) {
            authenticate { credential -> service.lenderForKey(credential.token) }
        }
        bearer(ADMIN_AUTH) {
            authenticate { credential ->
                Admin.takeIf { adminKeySha256 != null && sameHash(adminKeySha256, sha256Hex(credential.token)) }
            }
        }
    }

    // Responses can carry personal data; no proxy or browser should keep a copy.
    install(createApplicationPlugin("NoStore") {
        onCall { call -> call.response.header(HttpHeaders.CacheControl, "no-store") }
    })

    routing {
        get("/health") { call.respondText("ok") }

        rateLimit(LEADS_LIMIT) {
            post("/leads") {
                val submission = call.receive<LeadSubmission>()
                when (val r = withContext(Dispatchers.IO) { service.submit(submission) }) {
                    is IntakeResult.Created -> call.respond(HttpStatusCode.Created, LeadReceipt(r.leadId))
                    is IntakeResult.Duplicate -> call.respond(HttpStatusCode.OK, LeadReceipt(r.leadId))
                    is IntakeResult.Held -> call.respond(HttpStatusCode.UnprocessableEntity, ApiError(r.reason))
                }
            }
        }

        authenticate(LENDER_AUTH) {
            route("/lender/leads") {
                get {
                    val lender = call.lender()
                    val status = call.request.queryParameters["status"]?.let { s ->
                        LeadStatus.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
                            ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Unknown status."))
                    }
                    call.respond(withContext(Dispatchers.IO) { service.leadsFor(lender, status) })
                }
                get("/{id}") {
                    val lead = withContext(Dispatchers.IO) { service.leadFor(call.lender(), call.leadId()) }
                    if (lead == null) call.respond(HttpStatusCode.NotFound, ApiError("No such lead."))
                    else call.respond(lead)
                }
                post("/{id}/accept") {
                    call.respondAction(withContext(Dispatchers.IO) { service.accept(call.lender(), call.leadId()) })
                }
                post("/{id}/reject") {
                    val reason = call.receive<RejectRequest>().reason
                    if (reason.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ApiError("Give a reason."))
                    call.respondAction(withContext(Dispatchers.IO) { service.reject(call.lender(), call.leadId(), reason) })
                }
                post("/{id}/funded") {
                    call.respondAction(withContext(Dispatchers.IO) { service.markFunded(call.lender(), call.leadId()) })
                }
            }
        }

        authenticate(ADMIN_AUTH) {
            post("/admin/erase-personal-data") {
                val email = call.receive<EraseRequest>().email
                if (email.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, ApiError("Give the borrower's email."))
                val n = withContext(Dispatchers.IO) { service.erasePersonalDataForEmail(email) }
                log.info("Erased personal data from {} lead(s) on request", n)
                call.respond(EraseResult(n))
            }
        }
    }

    if (backgroundJobs) startBackgroundJobs(service)
}

private fun Application.startBackgroundJobs(service: LeadService) {
    val log = LoggerFactory.getLogger("LeadServer")
    val wake = Channel<Unit>(Channel.CONFLATED)
    service.onNewLead = { wake.trySend(Unit) }

    // One loop delivers webhooks, so a lead is never notified twice at once.
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                service.deliverPendingNotifications()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Notification pass failed", e)
            }
            withTimeoutOrNull(60.seconds) { wake.receive() }
        }
    }

    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val n = service.applyRetention()
                if (n > 0) log.info("Retention: erased personal data from {} lead(s)", n)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Retention pass failed", e)
            }
            delay(6.hours)
        }
    }
}

private fun ApplicationCall.lender(): LenderAccount = principal<LenderAccount>()!!

private fun ApplicationCall.leadId(): String = parameters["id"].orEmpty()

private suspend fun ApplicationCall.respondAction(result: ActionResult) = when (result) {
    is ActionResult.Done -> respond(result.lead)
    ActionResult.NotFound -> respond(HttpStatusCode.NotFound, ApiError("No such lead."))
    is ActionResult.Conflict -> respond(HttpStatusCode.Conflict, ApiError(result.message))
}
