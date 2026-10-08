package com.yourco.lending.server

import com.yourco.lending.api.LeadApi
import com.yourco.lending.matching.LeadCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * What a lender's webhook receives for a new lead: the anonymized card only.
 * Contact details are released through the lender API after they accept.
 * Deliveries can repeat if a response gets lost, so receivers should de-duplicate on [leadId].
 */
@Serializable
data class NewLeadNotice(val event: String = "lead.created", val leadId: String, val card: LeadCard)

fun interface LenderNotifier {
    /** True when the lender's endpoint answered 2xx. */
    suspend fun notify(webhookUrl: String, notice: NewLeadNotice): Boolean
}

class WebhookNotifier(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : LenderNotifier {

    override suspend fun notify(webhookUrl: String, notice: NewLeadNotice): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder(URI(webhookUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(LeadApi.json.encodeToString(notice)))
                .build()
            http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
        }.getOrDefault(false)
    }
}
