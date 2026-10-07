package com.yourco.lending.matching

import java.util.UUID

/**
 * Every borrower-facing disclosure in one place, with a version. Consent is
 * tied to the version the borrower actually saw; changing any text here means
 * bumping [VERSION], which forces fresh consent.
 *
 * Draft wording. Have a lawyer review it before launch, especially the
 * contact consent (phone calls and texts carry their own federal rules).
 */
object Disclosures {
    const val VERSION = "2026-10-beta-1"

    const val NOT_AN_APPROVAL =
        "This is loan discovery, not a loan application or approval. Results show likely fits " +
            "based on what you told us. Lenders make every final decision."

    const val HOW_SHARING_WORKS =
        "We only share your information with a lender you choose. When you do, that lender " +
            "receives your answers and contact details and may contact you about financing."

    fun consentText(lenderName: String) =
        "I agree to share my business information and contact details with $lenderName so they " +
            "can contact me by phone, text, or email about financing. I understand this is not a " +
            "loan approval and that $lenderName makes its own decision."

    const val SAMPLE_LENDER_NOTE =
        "Test mode: this is a sample lender used for the beta. Nothing is sent to a real lender."
}

data class BorrowerContact(
    val fullName: String,
    val businessName: String,
    val email: String,
    val phone: String,
) {
    val phoneDigits: String get() = phone.filter { it.isDigit() }

    fun problems(): List<String> = buildList {
        if (fullName.isBlank()) add("Enter your name.")
        if (businessName.isBlank()) add("Enter the business name.")
        if (!EMAIL.matches(email.trim())) add("Enter a valid email.")
        val d = phoneDigits
        if (!(d.length == 10 || (d.length == 11 && d.startsWith("1")))) add("Enter a 10-digit US phone number.")
    }

    fun isComplete(): Boolean = problems().isEmpty()

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Consent to share with one specific lender product, on one disclosure version. */
data class BorrowerConsent(
    val productId: String,
    val disclosureVersion: String,
    val givenAtEpochMillis: Long,
)

/**
 * What a lender sees first: readable in under ten seconds and anonymized.
 * Contact details travel separately and should only be released to the
 * lender after they accept the lead.
 */
data class LeadCard(
    val leadId: String,
    val borrowerRef: String,
    val productId: String,
    val productName: String,
    val businessType: String,
    val state: String,
    val timeInBusiness: String,
    val annualRevenue: String,
    val loanPurpose: String,
    val requestedAmount: Long,
    val fitScore: Int,
    val keyReasons: List<String>,
    val concerns: List<String>,
    val isSample: Boolean,
)

enum class LeadEventType { CREATED, SENT, ACCEPTED, REJECTED, FUNDED }

/** Audit trail entry. The backend appends these; the app only writes CREATED. */
data class LeadEvent(val type: LeadEventType, val atEpochMillis: Long, val note: String = "")

data class Lead(
    val card: LeadCard,
    val contact: BorrowerContact,
    val consent: BorrowerConsent,
    val events: List<LeadEvent>,
)

sealed interface RoutingDecision {
    data class Route(val lead: Lead) : RoutingDecision
    data class Hold(val reason: String) : RoutingDecision
}

/**
 * The gate between a match and a lender. A lead only goes out when the
 * borrower consented to that exact lender on the current disclosures, their
 * contact details are complete, and the match clears the lender's own
 * fit-score bar. Anything else is held, with the reason.
 */
class LeadRouter(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun route(
        match: ProductMatch,
        answers: DiscoveryAnswers,
        contact: BorrowerContact?,
        consent: BorrowerConsent?,
        borrowerRef: String,
    ): RoutingDecision {
        val product = match.product
        if (consent == null) return RoutingDecision.Hold("The borrower hasn't agreed to share their information.")
        if (consent.productId != product.id) return RoutingDecision.Hold("Consent was given for a different lender.")
        if (consent.disclosureVersion != Disclosures.VERSION) {
            return RoutingDecision.Hold("Consent was given on older disclosures. Ask again.")
        }
        if (contact == null || !contact.isComplete()) return RoutingDecision.Hold("Contact details are incomplete.")
        if (match.decision != Decision.LIKELY_ELIGIBLE) {
            return RoutingDecision.Hold("Only likely-eligible matches are sent to lenders.")
        }
        if (match.fitScore < product.minFitScoreToRoute) {
            return RoutingDecision.Hold(
                "Fit score ${match.fitScore} is below this lender's minimum of ${product.minFitScoreToRoute}."
            )
        }

        val now = clock()
        val card = LeadCard(
            leadId = newId(),
            borrowerRef = borrowerRef,
            productId = product.id,
            productName = product.displayName,
            businessType = answers.businessType?.label ?: "Not provided",
            state = answers.state ?: "Not provided",
            timeInBusiness = answers.timeInBusiness?.label ?: "Not provided",
            annualRevenue = answers.annualRevenue?.label ?: "Not provided",
            loanPurpose = answers.loanPurpose?.label ?: "Not provided",
            requestedAmount = answers.requestedAmount ?: 0,
            fitScore = match.fitScore,
            keyReasons = match.strengths.take(3),
            concerns = match.concerns,
            isSample = product.isSample,
        )
        return RoutingDecision.Route(
            Lead(card, contact, consent, listOf(LeadEvent(LeadEventType.CREATED, now)))
        )
    }
}
