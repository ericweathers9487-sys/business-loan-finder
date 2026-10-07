package com.yourco.lending.matching

/**
 * One lender product, expressed as a strict rule set. Lenders will edit these
 * from a product config screen later; every field maps to one control there.
 *
 * Mandatory rules disqualify. Soft ("preferred") rules only lower the fit
 * score. A null rule means the lender has no requirement for it.
 */
data class LenderProduct(
    val id: String,
    val lenderName: String,
    val productName: String,
    val active: Boolean = true,

    // ---- Mandatory rules ----
    val minMonthsInBusiness: Int? = null,
    val minAnnualRevenue: Long? = null,
    val minRequestedAmount: Long = 0,
    val maxRequestedAmount: Long,
    /** Cap on the amount as a share of annual revenue, e.g. 0.15 = 15%. */
    val maxAmountPctOfRevenue: Double? = null,
    val minCreditScore: Int? = null,
    /** Annual existing debt payments ÷ annual revenue, e.g. 0.30 = 30%. */
    val maxDebtRatio: Double? = null,
    /** Empty = all types allowed. */
    val allowedBusinessTypes: Set<BusinessType> = emptySet(),
    /** Empty = all purposes allowed. */
    val allowedLoanPurposes: Set<LoanPurpose> = emptySet(),
    /** Empty = nationwide. */
    val allowedStates: Set<String> = emptySet(),
    val excludedStates: Set<String> = emptySet(),
    val termMonths: IntRange,

    // ---- Soft rules ----
    val preferredMinMonthsInBusiness: Int? = null,
    val preferredMinAnnualRevenue: Long? = null,
    val preferredMinCreditScore: Int? = null,

    // ---- Routing ----
    /** Leads below this fit score are never sent to this lender. */
    val minFitScoreToRoute: Int = 70,
    val underwritingNotes: String = "",
    /** Sample products exist for testing only and must never receive real leads. */
    val isSample: Boolean = false,
) {
    init {
        require(maxRequestedAmount >= minRequestedAmount) {
            "maxRequestedAmount must be >= minRequestedAmount for $id"
        }
        require(minFitScoreToRoute in 0..100) { "minFitScoreToRoute must be 0..100 for $id" }
        require(termMonths.first > 0) { "termMonths must be positive for $id" }
    }

    val displayName: String get() = "$lenderName · $productName"
}
