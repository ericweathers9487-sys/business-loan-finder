package com.yourco.lending.leads

import com.yourco.lending.matching.Lead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface SubmitResult {
    /** [testMode] = recorded on the device only; nothing left the phone. */
    data class Sent(val testMode: Boolean) : SubmitResult
    data class Failed(val message: String) : SubmitResult
}

/** Where routed leads go. The app never talks to a lender directly. */
interface LeadSink {
    suspend fun submit(lead: Lead): SubmitResult
}

/**
 * Keeps leads on the device. Used for sample lenders and whenever no backend
 * endpoint is configured, so the full flow works end to end in testing.
 */
class LocalLeadSink : LeadSink {
    private val stored = mutableListOf<Lead>()
    val leads: List<Lead> get() = stored.toList()

    override suspend fun submit(lead: Lead): SubmitResult {
        stored += lead
        return SubmitResult.Sent(testMode = true)
    }
}

/**
 * Posts the lead to your backend, which stores it, writes the audit log,
 * re-runs the eligibility engine, and notifies the lender. Sample leads are
 * refused here as a second safety net.
 */
class HttpLeadSink(private val endpoint: String) : LeadSink {

    override suspend fun submit(lead: Lead): SubmitResult {
        if (lead.card.isSample) return SubmitResult.Failed("Sample leads are never sent to a server.")
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                try {
                    conn.outputStream.use { it.write(toJson(lead).toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code in 200..299) SubmitResult.Sent(testMode = false)
                    else SubmitResult.Failed("The server answered $code. Please try again.")
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { SubmitResult.Failed("Couldn't reach the server. Check your connection and try again.") }
        }
    }

    private fun toJson(lead: Lead): JSONObject {
        val c = lead.card
        return JSONObject()
            .put("card", JSONObject()
                .put("leadId", c.leadId)
                .put("borrowerRef", c.borrowerRef)
                .put("productId", c.productId)
                .put("productName", c.productName)
                .put("businessType", c.businessType)
                .put("state", c.state)
                .put("timeInBusiness", c.timeInBusiness)
                .put("annualRevenue", c.annualRevenue)
                .put("loanPurpose", c.loanPurpose)
                .put("requestedAmount", c.requestedAmount)
                .put("fitScore", c.fitScore)
                .put("keyReasons", JSONArray(c.keyReasons))
                .put("concerns", JSONArray(c.concerns)))
            .put("contact", JSONObject()
                .put("fullName", lead.contact.fullName)
                .put("businessName", lead.contact.businessName)
                .put("email", lead.contact.email.trim())
                .put("phone", lead.contact.phoneDigits))
            .put("consent", JSONObject()
                .put("productId", lead.consent.productId)
                .put("disclosureVersion", lead.consent.disclosureVersion)
                .put("givenAtEpochMillis", lead.consent.givenAtEpochMillis))
            .put("events", JSONArray(lead.events.map {
                JSONObject().put("type", it.type.name).put("atEpochMillis", it.atEpochMillis).put("note", it.note)
            }))
    }
}
