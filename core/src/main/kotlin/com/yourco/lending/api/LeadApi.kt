package com.yourco.lending.api

import com.yourco.lending.matching.BorrowerConsent
import com.yourco.lending.matching.BorrowerContact
import com.yourco.lending.matching.DiscoveryAnswers
import com.yourco.lending.matching.Lead
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What the app sends to POST /leads, and what it gets back. Both sides compile
 * this file, so the format can't drift between them.
 *
 * The app sends what the borrower said, never its own verdict. The server
 * looks up the product, re-runs the eligibility engine on [answers], and makes
 * its own routing decision.
 */

@Serializable
data class LeadSubmission(
    /**
     * Made once per consent screen visit. Retrying a send that timed out reuses
     * it, so the server returns the first lead instead of creating a second.
     */
    val submissionId: String,
    /** Anonymous per-session reference; never tied to the borrower's identity on the phone. */
    val borrowerRef: String,
    val productId: String,
    val answers: DiscoveryAnswers,
    val contact: BorrowerContact,
    val consent: BorrowerConsent,
) {
    companion object {
        fun from(lead: Lead, submissionId: String) = LeadSubmission(
            submissionId = submissionId,
            borrowerRef = lead.card.borrowerRef,
            productId = lead.card.productId,
            answers = lead.answers,
            contact = lead.contact.normalized(),
            consent = lead.consent,
        )
    }
}

/** 201 (new lead) or 200 (same submissionId seen before). */
@Serializable
data class LeadReceipt(val leadId: String)

/** Any non-2xx answer. [error] is written for the borrower and safe to show as is. */
@Serializable
data class ApiError(val error: String)

object LeadApi {
    /** Older and newer app versions may add fields; neither side should choke on them. */
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
