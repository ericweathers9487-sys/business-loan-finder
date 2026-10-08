package com.yourco.lending.leads

import com.yourco.lending.api.ApiError
import com.yourco.lending.api.LeadApi
import com.yourco.lending.api.LeadSubmission
import com.yourco.lending.matching.Lead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed interface SubmitResult {
    /** [testMode] = recorded on the device only; nothing left the phone. */
    data class Sent(val testMode: Boolean) : SubmitResult
    data class Failed(val message: String) : SubmitResult
}

/** Where routed leads go. The app never talks to a lender directly. */
interface LeadSink {
    /** [submissionId] stays the same across retries of one send, so the server can drop duplicates. */
    suspend fun submit(lead: Lead, submissionId: String): SubmitResult
}

/**
 * Keeps leads in memory on the device, never on disk. Used for sample lenders
 * and whenever no backend endpoint is configured, so the full flow works end
 * to end in testing.
 */
class LocalLeadSink : LeadSink {
    private val stored = mutableListOf<Lead>()
    val leads: List<Lead> get() = stored.toList()

    override suspend fun submit(lead: Lead, submissionId: String): SubmitResult {
        stored += lead
        return SubmitResult.Sent(testMode = true)
    }
}

/**
 * Posts the borrower's answers, contact details, and consent to your backend
 * over HTTPS. The backend re-runs the eligibility engine, stores the lead
 * encrypted, writes the audit log, and notifies the lender. Sample leads are
 * refused here as a second safety net.
 */
class HttpLeadSink(private val endpoint: String) : LeadSink {

    init {
        require(endpoint.startsWith("https://")) { "Lead endpoint must use https://" }
    }

    override suspend fun submit(lead: Lead, submissionId: String): SubmitResult {
        if (lead.card.isSample) return SubmitResult.Failed("Sample leads are never sent to a server.")
        val body = LeadApi.json.encodeToString(LeadSubmission.from(lead, submissionId))
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
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code in 200..299) SubmitResult.Sent(testMode = false)
                    else SubmitResult.Failed(serverMessage(conn) ?: "The server answered $code. Please try again.")
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { SubmitResult.Failed("Couldn't reach the server. Check your connection and try again.") }
        }
    }

    /** The server writes its error text for borrowers, e.g. why it held the lead. */
    private fun serverMessage(conn: HttpURLConnection): String? = runCatching {
        conn.errorStream?.bufferedReader()?.use { LeadApi.json.decodeFromString<ApiError>(it.readText()).error }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
