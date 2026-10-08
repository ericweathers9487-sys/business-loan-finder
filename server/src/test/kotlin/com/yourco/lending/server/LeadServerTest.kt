package com.yourco.lending.server

import com.yourco.lending.api.ApiError
import com.yourco.lending.api.LeadApi
import com.yourco.lending.api.LeadReceipt
import com.yourco.lending.matching.CreditBand
import com.yourco.lending.matching.LeadEventType
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager
import java.sql.SQLException

class LeadServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var f: Fixture

    @Before
    fun setUp() {
        f = Fixture(tmp.root)
    }

    @After
    fun tearDown() {
        f.store.close()
    }

    private fun server(leadsPerMinute: Int = 1_000, test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { leadServer(f.service, f.adminKeySha256, backgroundJobs = false, leadsPerMinute = leadsPerMinute) }
        test()
    }

    private suspend fun HttpClient.submit(body: String): HttpResponse = post("/leads") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpClient.lender(key: String, path: String, body: String? = null): HttpResponse =
        if (body == null && !path.endsWith("/accept") && !path.endsWith("/funded")) {
            get(path) { bearerAuth(key) }
        } else {
            post(path) {
                bearerAuth(key)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        }

    private suspend fun HttpResponse.receipt() = LeadApi.json.decodeFromString<LeadReceipt>(bodyAsText())
    private suspend fun HttpResponse.error() = LeadApi.json.decodeFromString<ApiError>(bodyAsText()).error
    private suspend fun HttpResponse.view() = LeadApi.json.decodeFromString<LenderLeadView>(bodyAsText())
    private suspend fun HttpResponse.list() = LeadApi.json.decodeFromString<List<LenderLeadSummary>>(bodyAsText())

    // ---- Intake ----

    @Test
    fun eligibleLeadIsStoredEncryptedAndShowsOnlyTheCardUntilAccepted() = server {
        val res = client.submit(submission().json())
        assertEquals(HttpStatusCode.Created, res.status)
        val id = res.receipt().leadId

        // At rest: none of the borrower's details are readable in the database files.
        val db = f.databaseBytes()
        listOf("Dana", "Quintero", "example.com", "2055550142", "EXCELLENT").forEach {
            assertFalse("'$it' is readable in the database", db.contains(it, ignoreCase = true))
        }

        val listed = client.lender(KEY_A, "/lender/leads").list()
        assertEquals(listOf(id), listed.map { it.leadId })
        assertEquals(LeadStatus.NEW, listed[0].status)
        assertEquals("HVAC", listed[0].card.businessType)

        val before = client.lender(KEY_A, "/lender/leads/$id").view()
        assertNull(before.contact)
        assertNull(before.answers)
        assertFalse(before.personalDataErased)
        assertTrue(before.consent.text.contains("Sample Lender A"))

        val accepted = client.lender(KEY_A, "/lender/leads/$id/accept")
        assertEquals(HttpStatusCode.OK, accepted.status)
        val after = accepted.view()
        assertEquals(LeadStatus.ACCEPTED, after.status)
        assertEquals("Dana.Q@example.com", after.contact?.email)
        assertEquals("2055550142", after.contact?.phone)
        assertEquals(CreditBand.EXCELLENT, after.answers?.credit)
        assertEquals(listOf(LeadEventType.CREATED, LeadEventType.ACCEPTED), after.events.map { it.type })
        assertEquals("lender:Lender A", after.events.last().actor)
    }

    @Test
    fun retryWithTheSameSubmissionIdReturnsTheSameLead() = server {
        val first = client.submit(submission().json())
        val again = client.submit(submission().json())
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.OK, again.status)
        assertEquals(first.receipt().leadId, again.receipt().leadId)
        assertEquals(1, client.lender(KEY_A, "/lender/leads").list().size)
    }

    @Test
    fun serverReRunsTheEngineInsteadOfTrustingThePhone() = server {
        // Lender A needs a 640 credit score. The phone can claim anything; the server checks.
        val res = client.submit(submission(answers = strongHvac.copy(credit = CreditBand.LOW)).json())
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(res.error().contains("credit score of 640"))
        assertTrue(client.lender(KEY_A, "/lender/leads").list().isEmpty())
        assertFalse(f.databaseBytes().contains("Dana"))
    }

    @Test
    fun heldLeadsAreNotStored() = server {
        val cases = listOf(
            submission(productId = STILL_SAMPLE) to "Sample lenders",
            submission(productId = "no-such-product") to "isn't available",
            submission(consent = submission().consent.copy(disclosureVersion = "old")) to "older disclosures",
            submission(consent = submission().consent.copy(productId = PRODUCT_C)) to "different lender",
            submission(answers = strongHvac.copy(state = "ZZ")) to "Unknown state",
            submission(contact = dana.copy(email = "nope")) to "incomplete",
            submission(contact = dana.copy(fullName = "x".repeat(500))) to "incomplete",
        )
        cases.forEachIndexed { i, (sub, expected) ->
            val res = client.submit(sub.copy(submissionId = "held-$i").json())
            assertEquals("case $i", HttpStatusCode.UnprocessableEntity, res.status)
            assertTrue("case $i: ${res.error()}", res.error().contains(expected))
        }
        assertTrue(client.lender(KEY_A, "/lender/leads").list().isEmpty())
        assertTrue(client.lender(KEY_C, "/lender/leads").list().isEmpty())
    }

    @Test
    fun malformedAndOversizedRequestsAreRefused() = server {
        assertEquals(HttpStatusCode.BadRequest, client.submit("{not json").status)
        assertEquals(HttpStatusCode.BadRequest, client.submit("""{"productId":"x"}""").status)
        val credit = submission().json().replace("\"EXCELLENT\"", "\"AMAZING\"")
        assertEquals(HttpStatusCode.BadRequest, client.submit(credit).status)
        val huge = submission(contact = dana.copy(businessName = "x".repeat(20_000))).json()
        assertEquals(HttpStatusCode.PayloadTooLarge, client.submit(huge).status)
    }

    @Test
    fun submissionsAreRateLimited() = server(leadsPerMinute = 2) {
        repeat(2) { assertEquals(HttpStatusCode.Created, client.submit(submission(submissionId = "r-$it").json()).status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.submit(submission(submissionId = "r-3").json()).status)
    }

    @Test
    fun responsesAreNeverCached() = server {
        val res = client.submit(submission().json())
        assertEquals("no-store", res.headers[HttpHeaders.CacheControl])
    }

    // ---- Lender access ----

    @Test
    fun lendersNeedAKeyAndSeeOnlyTheirOwnLeads() = server {
        val id = client.submit(submission().json()).receipt().leadId

        assertEquals(HttpStatusCode.Unauthorized, client.get("/lender/leads").status)
        assertEquals(HttpStatusCode.Unauthorized, client.lender("wrong-key", "/lender/leads").status)

        assertTrue(client.lender(KEY_C, "/lender/leads").list().isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.lender(KEY_C, "/lender/leads/$id").status)
        assertEquals(HttpStatusCode.NotFound, client.lender(KEY_C, "/lender/leads/$id/accept").status)

        // The failed attempt by the other lender left no trace on the lead.
        val view = client.lender(KEY_A, "/lender/leads/$id").view()
        assertEquals(LeadStatus.NEW, view.status)
        assertEquals(listOf(LeadEventType.CREATED), view.events.map { it.type })
    }

    @Test
    fun leadLifecycleFollowsTheRules() = server {
        val a = client.submit(submission(submissionId = "s-a").json()).receipt().leadId
        val b = client.submit(submission(submissionId = "s-b").json()).receipt().leadId

        // Funded needs accepted first.
        assertEquals(HttpStatusCode.Conflict, client.lender(KEY_A, "/lender/leads/$a/funded").status)
        // Accept is safe to repeat.
        assertEquals(HttpStatusCode.OK, client.lender(KEY_A, "/lender/leads/$a/accept").status)
        assertEquals(HttpStatusCode.OK, client.lender(KEY_A, "/lender/leads/$a/accept").status)
        assertEquals(HttpStatusCode.Conflict, client.lender(KEY_A, "/lender/leads/$a/reject", """{"reason":"no"}""").status)
        val funded = client.lender(KEY_A, "/lender/leads/$a/funded").view()
        assertEquals(LeadStatus.FUNDED, funded.status)
        assertNotNull(funded.contact)
        assertEquals(
            listOf(LeadEventType.CREATED, LeadEventType.ACCEPTED, LeadEventType.FUNDED),
            funded.events.map { it.type },
        )

        // Rejecting needs a reason, keeps contact details hidden, and is final.
        assertEquals(HttpStatusCode.BadRequest, client.lender(KEY_A, "/lender/leads/$b/reject", """{"reason":" "}""").status)
        val rejected = client.lender(KEY_A, "/lender/leads/$b/reject", """{"reason":"Outside our area"}""").view()
        assertEquals(LeadStatus.REJECTED, rejected.status)
        assertNull(rejected.contact)
        assertEquals("Outside our area", rejected.events.last().note)
        assertEquals(HttpStatusCode.Conflict, client.lender(KEY_A, "/lender/leads/$b/accept").status)

        assertEquals(listOf(b), client.lender(KEY_A, "/lender/leads?status=rejected").list().map { it.leadId })
        assertEquals(HttpStatusCode.BadRequest, client.lender(KEY_A, "/lender/leads?status=bogus").status)
    }

    // ---- Notifications ----

    @Test
    fun webhookGetsTheCardOnlyAndRetriesUntilDelivered() = server {
        val id = client.submit(submission().json()).receipt().leadId
        client.submit(submission(productId = PRODUCT_C, submissionId = "s-c").json()) // Lender C has no webhook.

        f.notifier.succeed = false
        f.service.deliverPendingNotifications()
        assertTrue(f.notifier.sent.isEmpty())

        f.notifier.succeed = true
        f.service.deliverPendingNotifications()
        f.service.deliverPendingNotifications()
        assertEquals(1, f.notifier.sent.size)
        val (url, notice) = f.notifier.sent.single()
        assertEquals("https://hooks.example.com/a", url)
        assertEquals(id, notice.leadId)
        val wire = LeadApi.json.encodeToString(notice)
        assertFalse(wire.contains("Dana") || wire.contains("example.com") || wire.contains("0142"))

        val events = client.lender(KEY_A, "/lender/leads/$id").view().events.map { it.type }
        assertEquals(listOf(LeadEventType.CREATED, LeadEventType.SENT), events)
    }

    // ---- Privacy ----

    @Test
    fun deletionRequestErasesPersonalDataButKeepsTheAuditLog() = server {
        val id = client.submit(submission().json()).receipt().leadId
        client.lender(KEY_A, "/lender/leads/$id/accept")

        val noAuth = client.post("/admin/erase-personal-data") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"dana.q@example.com"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.lender(KEY_A, "/admin/erase-personal-data", """{"email":"dana.q@example.com"}""").status,
        )

        // Matching ignores case and stray spaces.
        val erased = client.lender(ADMIN_KEY, "/admin/erase-personal-data", """{"email":"  DANA.Q@example.com "}""")
        assertEquals(HttpStatusCode.OK, erased.status)
        assertEquals("""{"leadsErased":1}""", erased.bodyAsText())

        val view = client.lender(KEY_A, "/lender/leads/$id").view()
        assertTrue(view.personalDataErased)
        assertNull(view.contact)
        assertEquals("HVAC", view.card.businessType)
        assertEquals(LeadEventType.PERSONAL_DATA_DELETED, view.events.last().type)
        assertEquals("admin", view.events.last().actor)
    }

    @Test
    fun erasedLeadsCantBeAccepted() = server {
        val id = client.submit(submission().json()).receipt().leadId
        assertEquals(1, f.service.erasePersonalDataForEmail("dana.q@example.com"))
        val res = client.lender(KEY_A, "/lender/leads/$id/accept")
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.error().contains("deleted"))
    }

    @Test
    fun retentionErasesPersonalDataAfterTheLimit() = server {
        client.submit(submission().json())
        f.now += 364 * LeadService.DAY_MS
        assertEquals(0, f.service.applyRetention())
        f.now += 2 * LeadService.DAY_MS
        assertEquals(1, f.service.applyRetention())
        assertEquals(0, f.service.applyRetention())
    }

    @Test
    fun auditLogCantBeEditedOrDeleted() = server {
        client.submit(submission().json())
        DriverManager.getConnection("jdbc:sqlite:${f.dbFile.path}").use { c ->
            for (sql in listOf("UPDATE lead_events SET note = 'edited'", "DELETE FROM lead_events")) {
                try {
                    c.createStatement().use { it.executeUpdate(sql) }
                    throw AssertionError("Audit log accepted: $sql")
                } catch (e: SQLException) {
                    assertTrue(e.message.orEmpty().contains("append-only"))
                }
            }
        }
    }
}
