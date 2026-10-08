package com.yourco.lending.server

import com.sun.net.httpserver.HttpServer
import com.yourco.lending.api.LeadApi
import com.yourco.lending.matching.LeadCard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import javax.crypto.AEADBadTagException

class SecurityTest {

    private val cipher = PiiCipher(ByteArray(32) { 7 })

    @Test
    fun personalDataRoundTripsAndIsBoundToItsLead() {
        val token = cipher.encrypt("Dana Quintero", "lead-1")
        assertFalse(token.contains("Dana"))
        assertEquals("Dana Quintero", cipher.decrypt(token, "lead-1"))
        // Fresh random IV each time.
        assertNotEquals(token, cipher.encrypt("Dana Quintero", "lead-1"))
        // Moved to another row, it no longer decrypts.
        try {
            cipher.decrypt(token, "lead-2")
            fail("Decrypted under the wrong lead ID")
        } catch (_: AEADBadTagException) {
        }
        // A different key can't read it.
        try {
            PiiCipher(ByteArray(32) { 8 }).decrypt(token, "lead-1")
            fail("Decrypted with the wrong key")
        } catch (_: AEADBadTagException) {
        }
    }

    @Test
    fun emailIndexIgnoresCaseAndSpacesAndHidesTheEmail() {
        val idx = cipher.emailIndex("Dana.Q@example.com")
        assertEquals(idx, cipher.emailIndex("  dana.q@EXAMPLE.com "))
        assertNotEquals(idx, cipher.emailIndex("dana@example.com"))
        assertFalse(idx.contains("dana"))
        // Keyed: someone without the key can't compute it from a guessed email.
        assertNotEquals(idx, PiiCipher(ByteArray(32) { 8 }).emailIndex("Dana.Q@example.com"))
    }

    @Test
    fun generatedKeysAreUsable() {
        val key = newApiKey()
        assertTrue(key.startsWith("blf_") && key.length > 40)
        assertTrue(isSha256Hex(sha256Hex(key)))
        PiiCipher(java.util.Base64.getDecoder().decode(newEncryptionKey()))
    }

    // ---- Config ----

    private val goodLenders = """[{"name":"Lender A","apiKeySha256":"${sha256Hex("k")}","productIds":["p-a"],"webhookUrl":"https://hooks.example.com/a"}]"""
    private val goodEnv = mapOf("PII_ENCRYPTION_KEY" to newEncryptionKey(), "LENDERS_JSON" to goodLenders)

    private fun configError(env: Map<String, String>): String =
        try {
            ServerConfig.fromEnv(env)
            fail("Config was accepted")
            ""
        } catch (e: ConfigException) {
            e.message.orEmpty()
        }

    @Test
    fun configReadsTheEnvironment() {
        val c = ServerConfig.fromEnv(goodEnv + ("RETENTION_DAYS" to "90"))
        assertEquals(8080, c.port)
        assertEquals("data/leads.db", c.databasePath)
        assertEquals(90, c.retentionDays)
        assertEquals(setOf("p-a"), c.lenders.single().productIds)
        assertEquals(null, c.adminKeySha256)
    }

    @Test
    fun configRefusesUnsafeSetups() {
        assertTrue(configError(goodEnv - "PII_ENCRYPTION_KEY").contains("PII_ENCRYPTION_KEY"))
        assertTrue(configError(goodEnv + ("PII_ENCRYPTION_KEY" to "dG9vIHNob3J0")).contains("32 random bytes"))
        assertTrue(configError(goodEnv - "LENDERS_JSON").contains("LENDERS_JSON"))
        assertTrue(configError(goodEnv + ("LENDERS_JSON" to "nope")).contains("LENDERS_JSON"))
        assertTrue(configError(goodEnv + ("LENDERS_JSON" to goodLenders.replace("https://", "http://"))).contains("https"))
        assertTrue(configError(goodEnv + ("LENDERS_JSON" to goodLenders.replace(sha256Hex("k"), "k"))).contains("64 hex"))
        val twoOwners = """[
            {"name":"A","apiKeySha256":"${sha256Hex("a")}","productIds":["p"]},
            {"name":"B","apiKeySha256":"${sha256Hex("b")}","productIds":["p"]}]"""
        assertTrue(configError(goodEnv + ("LENDERS_JSON" to twoOwners)).contains("both"))
        assertTrue(configError(goodEnv + ("ADMIN_API_KEY_SHA256" to "short")).contains("ADMIN_API_KEY_SHA256"))
        assertTrue(configError(goodEnv + ("RETENTION_DAYS" to "0")).contains("RETENTION_DAYS"))
    }

    // ---- Webhook ----

    @Test
    fun webhookPostsTheNoticeAsJson() = runBlocking {
        var received = ""
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/hook") { ex ->
                received = ex.requestBody.readBytes().decodeToString()
                ex.sendResponseHeaders(204, -1)
                ex.close()
            }
            start()
        }
        try {
            val card = LeadCard(
                "lead-1", "b-1", "p-a", "A · Equipment", "HVAC", "AL", "5+ years", "\$1M – \$2.5M",
                "Equipment or vehicles", 150_000, 95, listOf("Funds HVAC businesses."), emptyList(), false,
            )
            val ok = WebhookNotifier().notify("http://127.0.0.1:${http.address.port}/hook", NewLeadNotice(leadId = "lead-1", card = card))
            assertTrue(ok)
            val notice = LeadApi.json.decodeFromString<NewLeadNotice>(received)
            assertEquals("lead.created", notice.event)
            assertEquals(card, notice.card)

            assertFalse(WebhookNotifier().notify("http://127.0.0.1:${http.address.port}/missing", NewLeadNotice(leadId = "x", card = card)))
        } finally {
            http.stop(0)
        }
    }
}
