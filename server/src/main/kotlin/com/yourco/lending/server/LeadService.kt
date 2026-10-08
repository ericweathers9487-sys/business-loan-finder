package com.yourco.lending.server

import com.yourco.lending.api.LeadApi
import com.yourco.lending.api.LeadSubmission
import com.yourco.lending.matching.BorrowerContact
import com.yourco.lending.matching.Decision
import com.yourco.lending.matching.Disclosures
import com.yourco.lending.matching.DiscoveryAnswers
import com.yourco.lending.matching.EligibilityEngine
import com.yourco.lending.matching.LeadCard
import com.yourco.lending.matching.LeadEventType
import com.yourco.lending.matching.LeadRouter
import com.yourco.lending.matching.LenderProduct
import com.yourco.lending.matching.RoutingDecision
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/** Encrypted together in the database; released to the lender only after they accept. */
@Serializable
data class PersonalData(val contact: BorrowerContact, val answers: DiscoveryAnswers)

@Serializable
data class LenderLeadSummary(
    val leadId: String,
    val status: LeadStatus,
    val createdAtEpochMillis: Long,
    val card: LeadCard,
)

@Serializable
data class LenderLeadView(
    val leadId: String,
    val status: LeadStatus,
    val createdAtEpochMillis: Long,
    val card: LeadCard,
    /** Null until the lead is accepted, and after the borrower's data is erased. */
    val contact: BorrowerContact?,
    val answers: DiscoveryAnswers?,
    val personalDataErased: Boolean,
    val consent: ConsentRecord,
    val events: List<AuditEvent>,
)

sealed interface IntakeResult {
    data class Created(val leadId: String) : IntakeResult
    /** The same submission arrived again (a retry). Nothing new was stored. */
    data class Duplicate(val leadId: String) : IntakeResult
    /** Not stored. [reason] is written for the borrower. */
    data class Held(val reason: String) : IntakeResult
}

sealed interface ActionResult {
    data class Done(val lead: LenderLeadView) : ActionResult
    data object NotFound : ActionResult
    data class Conflict(val message: String) : ActionResult
}

/**
 * The lead business rules. Nothing from the phone is trusted: the server looks
 * up the product itself, re-runs the eligibility engine, and applies the same
 * routing gate as the app. Only leads that pass are stored, and their
 * personal data is encrypted before it reaches the database.
 */
class LeadService(
    products: List<LenderProduct>,
    val lenders: List<LenderAccount>,
    private val store: LeadStore,
    private val cipher: PiiCipher,
    private val notifier: LenderNotifier,
    private val retentionDays: Int = 365,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(LeadService::class.java)
    private val products = products.associateBy { it.id }
    private val router = LeadRouter(clock, newId)

    /** Called after each new lead, so the notification loop can run right away. */
    @Volatile
    var onNewLead: () -> Unit = {}

    // ---- Borrower side ----

    fun submit(sub: LeadSubmission): IntakeResult {
        if (sub.submissionId.length !in 1..MAX_ID_LENGTH || sub.borrowerRef.length !in 1..MAX_ID_LENGTH) {
            return held(sub, "That request couldn't be read.")
        }
        store.leadIdForSubmission(sub.submissionId)?.let { return IntakeResult.Duplicate(it) }

        val product = products[sub.productId]?.takeIf { it.active }
            ?: return held(sub, "This lender option isn't available right now.")
        if (product.isSample) return held(sub, "Sample lenders never receive real leads.")
        if (lenderFor(product.id) == null) return held(sub, "This lender isn't taking requests right now.")
        sub.answers.problems().firstOrNull()?.let { return held(sub, it) }

        val match = EligibilityEngine.evaluate(product, sub.answers)
        if (match.decision != Decision.LIKELY_ELIGIBLE) {
            val why = match.disqualifiers.firstOrNull()?.reason ?: "A few answers are missing."
            return held(sub, "Based on your answers, this lender isn't a fit right now. $why")
        }
        val contact = sub.contact.normalized()
        val lead = when (val d = router.route(match, sub.answers, contact, sub.consent, sub.borrowerRef)) {
            is RoutingDecision.Hold -> return held(sub, d.reason)
            is RoutingDecision.Route -> d.lead
        }

        val leadId = lead.card.leadId
        val now = lead.events.first().atEpochMillis
        val row = StoredLead(
            leadId = leadId,
            submissionId = sub.submissionId,
            productId = product.id,
            status = LeadStatus.NEW,
            createdAt = now,
            notifiedAt = null,
            card = lead.card,
            consent = ConsentRecord(
                productId = product.id,
                disclosureVersion = lead.consent.disclosureVersion,
                givenOnPhoneAtEpochMillis = lead.consent.givenAtEpochMillis,
                receivedAtEpochMillis = now,
                text = Disclosures.consentText(product.lenderName),
            ),
            emailIndex = cipher.emailIndex(contact.email),
            personalData = cipher.encrypt(LeadApi.json.encodeToString(PersonalData(contact, sub.answers)), leadId),
        )
        val created = AuditEvent(LeadEventType.CREATED, now, "borrower-app", "Fit score ${lead.card.fitScore}")
        if (!store.insert(row, created)) {
            // Lost a race with a retry of the same submission.
            return IntakeResult.Duplicate(store.leadIdForSubmission(sub.submissionId) ?: return held(sub, "Please try again."))
        }
        log.info("Lead {} created for {}", leadId, product.id)
        onNewLead()
        return IntakeResult.Created(leadId)
    }

    // ---- Lender side ----

    fun lenderForKey(apiKey: String): LenderAccount? {
        val hash = sha256Hex(apiKey)
        return lenders.firstOrNull { sameHash(it.apiKeySha256, hash) }
    }

    fun leadsFor(lender: LenderAccount, status: LeadStatus?, limit: Int = 200): List<LenderLeadSummary> =
        store.list(lender.productIds, status, limit).map { LenderLeadSummary(it.leadId, it.status, it.createdAt, it.card) }

    /** Null when the lead doesn't exist or belongs to another lender; callers can't tell which. */
    fun leadFor(lender: LenderAccount, leadId: String): LenderLeadView? = owned(lender, leadId)?.let(::view)

    fun accept(lender: LenderAccount, leadId: String): ActionResult =
        act(lender, leadId, LeadStatus.ACCEPTED, from = setOf(LeadStatus.NEW), note = "")

    fun reject(lender: LenderAccount, leadId: String, reason: String): ActionResult =
        act(lender, leadId, LeadStatus.REJECTED, from = setOf(LeadStatus.NEW), note = reason.trim().take(MAX_NOTE_LENGTH))

    fun markFunded(lender: LenderAccount, leadId: String): ActionResult =
        act(lender, leadId, LeadStatus.FUNDED, from = setOf(LeadStatus.ACCEPTED), note = "")

    private fun act(lender: LenderAccount, leadId: String, to: LeadStatus, from: Set<LeadStatus>, note: String): ActionResult {
        val row = owned(lender, leadId) ?: return ActionResult.NotFound
        if (row.status == to) return ActionResult.Done(view(row))
        if (row.status !in from) return ActionResult.Conflict(conflictMessage(row.status, to))
        if (to == LeadStatus.ACCEPTED && row.personalData == null) {
            return ActionResult.Conflict("The borrower's details were deleted, so this lead can't be accepted.")
        }
        val event = AuditEvent(eventFor(to), clock(), "lender:${lender.name}", note)
        val changed = store.transition(leadId, from, to, event, requirePersonalData = to == LeadStatus.ACCEPTED)
        val now = store.get(leadId) ?: return ActionResult.NotFound
        return when {
            changed || now.status == to -> ActionResult.Done(view(now))
            else -> ActionResult.Conflict(conflictMessage(now.status, to))
        }
    }

    // ---- Notifications ----

    /** Tells lenders about new leads through their webhook. Failed deliveries are retried on the next pass. */
    suspend fun deliverPendingNotifications() {
        for (row in store.awaitingNotification(createdSince = clock() - NOTIFY_WINDOW_MS)) {
            val url = lenderFor(row.productId)?.webhookUrl ?: continue
            if (notifier.notify(url, NewLeadNotice(leadId = row.leadId, card = row.card))) {
                store.markNotified(row.leadId, AuditEvent(LeadEventType.SENT, clock(), "server", "Webhook delivered"))
            } else {
                log.warn("Webhook for lead {} failed; will retry", row.leadId)
            }
        }
    }

    // ---- Privacy ----

    /** Handles a borrower's deletion request. Returns how many leads were erased. */
    fun erasePersonalDataForEmail(email: String): Int =
        store.leadIdsByEmailIndex(cipher.emailIndex(email)).count {
            store.erasePersonalData(it, AuditEvent(LeadEventType.PERSONAL_DATA_DELETED, clock(), "admin", "Borrower deletion request"))
        }

    /** Erases contact details and answers older than the retention limit. */
    fun applyRetention(): Int {
        val cutoff = clock() - retentionDays * DAY_MS
        return store.leadIdsWithPersonalDataBefore(cutoff).count {
            store.erasePersonalData(
                it,
                AuditEvent(LeadEventType.PERSONAL_DATA_DELETED, clock(), "server", "Retention limit of $retentionDays days"),
            )
        }
    }

    /** Startup warnings for setups that would quietly drop leads. */
    fun configWarnings(): List<String> = buildList {
        products.values.filter { it.active && !it.isSample && lenderFor(it.id) == null }.forEach {
            add("No lender account lists product ${it.id}, so its leads will be held.")
        }
        lenders.flatMap { l -> l.productIds.filter { it !in products }.map { l.name to it } }.forEach { (name, id) ->
            add("$name lists product $id, which isn't in the catalog.")
        }
        if (products.values.none { it.active && !it.isSample }) {
            add("The catalog has only sample products, so every lead will be held until real lenders are added.")
        }
    }

    // ---- helpers ----

    private fun lenderFor(productId: String) = lenders.firstOrNull { productId in it.productIds }

    private fun owned(lender: LenderAccount, leadId: String) =
        store.get(leadId)?.takeIf { it.productId in lender.productIds }

    private fun view(row: StoredLead): LenderLeadView {
        val released = row.status == LeadStatus.ACCEPTED || row.status == LeadStatus.FUNDED
        val personal = if (released) row.personalData?.let {
            LeadApi.json.decodeFromString<PersonalData>(cipher.decrypt(it, row.leadId))
        } else null
        return LenderLeadView(
            leadId = row.leadId,
            status = row.status,
            createdAtEpochMillis = row.createdAt,
            card = row.card,
            contact = personal?.contact,
            answers = personal?.answers,
            personalDataErased = row.personalData == null,
            consent = row.consent,
            events = store.events(row.leadId),
        )
    }

    private fun held(sub: LeadSubmission, reason: String): IntakeResult.Held {
        log.info("Lead held for {}: {}", sub.productId.filter { it.isLetterOrDigit() || it in "-_." }.take(64), reason)
        return IntakeResult.Held(reason)
    }

    private fun conflictMessage(current: LeadStatus, to: LeadStatus) = when {
        current == LeadStatus.REJECTED -> "This lead was rejected."
        to == LeadStatus.REJECTED -> "This lead was already accepted."
        to == LeadStatus.FUNDED -> "Accept the lead before marking it funded."
        else -> "This lead is ${current.name.lowercase()}."
    }

    private fun eventFor(status: LeadStatus) = when (status) {
        LeadStatus.NEW -> LeadEventType.CREATED
        LeadStatus.ACCEPTED -> LeadEventType.ACCEPTED
        LeadStatus.REJECTED -> LeadEventType.REJECTED
        LeadStatus.FUNDED -> LeadEventType.FUNDED
    }

    companion object {
        const val MAX_ID_LENGTH = 64
        const val MAX_NOTE_LENGTH = 500
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** Webhook retries stop after this long; the lead stays available in the lender API. */
        const val NOTIFY_WINDOW_MS = 3 * DAY_MS
    }
}
