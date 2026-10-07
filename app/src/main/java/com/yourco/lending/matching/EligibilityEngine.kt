package com.yourco.lending.matching

import java.util.Locale

enum class Decision(val label: String) {
    LIKELY_ELIGIBLE("Likely eligible"),
    LIKELY_NOT_ELIGIBLE("Likely not eligible"),
    NEED_MORE_INFO("More info needed"),
}

/** A rule the borrower fails, plus what would change the outcome when we know. */
data class Disqualifier(val reason: String, val fix: String?)

/** One visible adjustment to the fit score. The score is the sum of these. */
data class ScoreFactor(val points: Int, val reason: String)

data class ProductMatch(
    val product: LenderProduct,
    val decision: Decision,
    /** 0–100. Always 0 unless [decision] is LIKELY_ELIGIBLE. */
    val fitScore: Int,
    /** Rules the borrower clearly meets. */
    val strengths: List<String>,
    /** Borderline or soft-rule issues. They lower the score but don't disqualify. */
    val concerns: List<String>,
    val disqualifiers: List<Disqualifier>,
    val missing: Set<Question>,
    val scoreFactors: List<ScoreFactor>,
    /** What this product could likely offer this borrower, or null if nothing. */
    val likelyRange: LongRange?,
)

data class DiscoveryResult(
    val decision: Decision,
    /** Likely-eligible products, best fit first. */
    val matches: List<ProductMatch>,
    /** Products we can't judge yet because answers are missing. */
    val pending: List<ProductMatch>,
    /** Products the borrower doesn't fit, closest first. */
    val nearMisses: List<ProductMatch>,
    /** Questions that would unlock [pending], in ask order. */
    val missingQuestions: List<Question>,
    /** Combined likely range across [matches]. */
    val likelyRange: LongRange?,
    val nextStep: String,
)

/**
 * Deterministic and explainable: the same answers always give the same result,
 * and every point of the fit score has a written reason. Pure Kotlin with no
 * Android dependencies, so the backend can run the exact same code. The server
 * must re-run it before routing a lead; never trust a result sent by the app.
 */
object EligibilityEngine {

    const val BASE_SCORE = 60

    fun discover(answers: DiscoveryAnswers, products: List<LenderProduct>): DiscoveryResult {
        val evaluated = products.filter { it.active }.map { evaluate(it, answers) }

        val matches = evaluated
            .filter { it.decision == Decision.LIKELY_ELIGIBLE }
            .sortedWith(compareByDescending<ProductMatch> { it.fitScore }.thenBy { it.product.displayName })
        val pending = evaluated.filter { it.decision == Decision.NEED_MORE_INFO }
        val nearMisses = evaluated
            .filter { it.decision == Decision.LIKELY_NOT_ELIGIBLE }
            .sortedWith(
                compareBy<ProductMatch> { it.disqualifiers.size }
                    .thenByDescending { m -> m.disqualifiers.count { it.fix != null } }
                    .thenBy { it.product.displayName }
            )

        val decision = when {
            matches.isNotEmpty() -> Decision.LIKELY_ELIGIBLE
            pending.isNotEmpty() -> Decision.NEED_MORE_INFO
            else -> Decision.LIKELY_NOT_ELIGIBLE
        }

        val missingQuestions = pending.flatMap { it.missing }.distinct().sortedBy { it.ordinal }

        val ranges = matches.mapNotNull { it.likelyRange }
        val likelyRange = if (ranges.isEmpty()) null
        else ranges.minOf { it.first }..ranges.maxOf { it.last }

        val nextStep = when (decision) {
            Decision.LIKELY_ELIGIBLE ->
                "Pick a match to share your details with that lender. They make the final decision and may contact you."
            Decision.NEED_MORE_INFO -> {
                val q = missingQuestions.size
                val p = pending.size
                "Answer $q more question${plural(q)} so we can check $p lender option${plural(p)}."
            }
            Decision.LIKELY_NOT_ELIGIBLE -> {
                val closest = nearMisses.firstOrNull()
                val onlyFix = closest?.disqualifiers?.singleOrNull()?.fix
                when {
                    evaluated.isEmpty() -> "No lenders are available in the beta right now. Check back soon."
                    closest != null && onlyFix != null ->
                        "Closest option: ${closest.product.displayName}. $onlyFix"
                    else -> "None of the current lenders fit yet. The reasons below show what would change that."
                }
            }
        }

        return DiscoveryResult(decision, matches, pending, nearMisses, missingQuestions, likelyRange, nextStep)
    }

    fun evaluate(product: LenderProduct, a: DiscoveryAnswers): ProductMatch {
        val strengths = mutableListOf<String>()
        val concerns = mutableListOf<String>()
        val disq = mutableListOf<Disqualifier>()
        val missing = linkedSetOf<Question>()
        val factors = mutableListOf<ScoreFactor>()

        fun concern(text: String) {
            concerns += text
            factors += ScoreFactor(-10, text)
        }

        // ---- Business type ----
        if (product.allowedBusinessTypes.isNotEmpty()) {
            val t = a.businessType
            when {
                t == null -> missing += Question.BUSINESS_TYPE
                t !in product.allowedBusinessTypes ->
                    disq += Disqualifier("This product doesn't fund ${t.label} businesses.", null)
                else -> strengths += "Funds ${t.label} businesses."
            }
        }

        // ---- Geography ----
        if (product.allowedStates.isNotEmpty() || product.excludedStates.isNotEmpty()) {
            val st = a.state
            when {
                st == null -> missing += Question.STATE
                (product.allowedStates.isNotEmpty() && st !in product.allowedStates) || st in product.excludedStates ->
                    disq += Disqualifier("This product isn't available in $st.", null)
                else -> strengths += "Available in $st."
            }
        }

        // ---- Loan purpose ----
        if (product.allowedLoanPurposes.isNotEmpty()) {
            val p = a.loanPurpose
            when {
                p == null -> missing += Question.LOAN_PURPOSE
                p !in product.allowedLoanPurposes ->
                    disq += Disqualifier("This product doesn't fund ${p.label.lowercase()}.", null)
                else -> strengths += "Funds ${p.label.lowercase()}."
            }
        }

        // ---- Time in business ----
        val tib = a.timeInBusiness
        val minMonths = product.minMonthsInBusiness
        if (minMonths == null) {
            factors += ScoreFactor(10, "No minimum time in business")
        } else if (tib == null) {
            missing += Question.TIME_IN_BUSINESS
        } else when (tib.atLeast(minMonths.toLong())) {
            Cmp.PASS -> {
                strengths += "Time in business (${tib.label.lowercase()}) meets the ${months(minMonths)} minimum."
                if (tib.low >= minMonths + 24) factors += ScoreFactor(10, "Well past the minimum time in business")
            }
            Cmp.FAIL -> disq += Disqualifier(
                "Needs at least ${months(minMonths)} in business. You reported ${tib.label.lowercase()}.",
                "You may qualify once the business reaches ${months(minMonths)}.",
            )
            Cmp.BORDERLINE -> concern("Needs ${months(minMonths)} in business; your range may fall short.")
        }

        // ---- Annual revenue ----
        val rev = a.annualRevenue
        val minRev = product.minAnnualRevenue
        if (minRev == null) {
            factors += ScoreFactor(10, "No revenue minimum")
        } else if (rev == null) {
            missing += Question.ANNUAL_REVENUE
        } else when (rev.atLeast(minRev)) {
            Cmp.PASS -> {
                strengths += "Revenue (${rev.label}) meets the ${money(minRev)} minimum."
                if (rev.low >= minRev * 2) factors += ScoreFactor(10, "Revenue is at least double the minimum")
            }
            Cmp.FAIL -> disq += Disqualifier(
                "Needs at least ${money(minRev)} in annual revenue. You reported ${rev.label}.",
                "Annual revenue of ${money(minRev)} or more would open this option.",
            )
            Cmp.BORDERLINE -> concern("Needs ${money(minRev)} in annual revenue; your range may fall short.")
        }

        // ---- Requested amount (including any revenue-based cap) ----
        val req = a.requestedAmount?.takeIf { it > 0 }
        var revenueCap: Long? = null
        if (req == null) {
            missing += Question.REQUESTED_AMOUNT
        } else if (req < product.minRequestedAmount) {
            disq += Disqualifier(
                "The smallest amount for this product is ${money(product.minRequestedAmount)}.",
                "Request ${money(product.minRequestedAmount)} or more.",
            )
        } else if (req > product.maxRequestedAmount) {
            disq += Disqualifier(
                "The largest amount for this product is ${money(product.maxRequestedAmount)}. You asked for ${money(req)}.",
                "Lower your request to ${money(product.maxRequestedAmount)} or less.",
            )
        } else {
            var amountOk = true
            val pct = product.maxAmountPctOfRevenue
            if (pct != null) {
                if (rev == null) {
                    missing += Question.ANNUAL_REVENUE
                    amountOk = false
                } else {
                    val capLow = (rev.low * pct).toLong()
                    val capHigh = rev.high?.let { (it * pct).toLong() }
                    when {
                        req <= capLow -> revenueCap = capLow
                        capHigh != null && req > capHigh -> {
                            amountOk = false
                            disq += Disqualifier(
                                "This lender caps loans at ${percent(pct)} of annual revenue, about ${money(capHigh)} for your revenue range.",
                                "Lower your request to about ${money(roundDown(capHigh))}.",
                            )
                        }
                        else -> {
                            amountOk = false
                            concern("Your request may be above this lender's cap of ${percent(pct)} of annual revenue.")
                        }
                    }
                }
            }
            if (amountOk) {
                val effectiveMax = minOf(product.maxRequestedAmount, revenueCap ?: Long.MAX_VALUE)
                strengths += "${money(req)} is within this product's ${money(product.minRequestedAmount)}–${money(effectiveMax)} range."
                when {
                    req * 2 <= effectiveMax -> factors += ScoreFactor(5, "Request is well within the limit")
                    req * 10 >= effectiveMax * 9 -> factors += ScoreFactor(-5, "Request is close to the limit")
                }
            }
        }

        // ---- Credit ----
        val credit = a.credit
        val minCredit = product.minCreditScore
        if (minCredit == null) {
            factors += ScoreFactor(10, "No minimum credit score")
        } else if (credit == null) {
            missing += Question.CREDIT
        } else when (credit.atLeast(minCredit.toLong())) {
            Cmp.PASS -> {
                strengths += "Credit (${credit.label}) meets the $minCredit minimum."
                if (credit.low >= minCredit + 60) factors += ScoreFactor(10, "Credit is well above the minimum")
            }
            Cmp.FAIL -> disq += Disqualifier(
                "Needs a personal credit score of $minCredit or higher. You reported ${credit.label.lowercase()}.",
                "A score of $minCredit or higher would open this option.",
            )
            Cmp.BORDERLINE -> concern("Needs a $minCredit credit score; your range may fall short.")
        }

        // ---- Existing debt vs revenue ----
        val maxRatio = product.maxDebtRatio
        if (maxRatio == null) {
            factors += ScoreFactor(5, "No limit on existing debt")
        } else {
            val debt = a.monthlyDebtPayments?.takeIf { it >= 0 }
            if (debt == null) missing += Question.MONTHLY_DEBT
            if (rev == null) missing += Question.ANNUAL_REVENUE
            if (debt != null && rev != null) {
                val annualDebt = debt * 12.0
                // Worst case divides by the bottom of the revenue band, best case by the top.
                val worst = when {
                    annualDebt == 0.0 -> 0.0
                    rev.low == 0L -> Double.POSITIVE_INFINITY
                    else -> annualDebt / rev.low
                }
                val best = rev.high?.let { annualDebt / it } ?: 0.0
                when {
                    worst <= maxRatio -> {
                        strengths += "Existing debt payments are within this lender's limit."
                        if (worst <= maxRatio / 2) factors += ScoreFactor(5, "Existing debt is low compared with revenue")
                    }
                    best > maxRatio -> disq += Disqualifier(
                        "Existing debt payments are too high compared with revenue (limit: ${percent(maxRatio)} of revenue per year).",
                        "Paying down or refinancing existing debt would help.",
                    )
                    else -> concern("Existing debt payments may be above this lender's limit.")
                }
            }
        }

        // ---- Soft rules: lower the score, never disqualify ----
        product.preferredMinMonthsInBusiness?.let { p ->
            if (tib != null && tib.low < p) concern("Below this lender's preferred ${months(p)} in business.")
        }
        product.preferredMinAnnualRevenue?.let { p ->
            if (rev != null && rev.low < p) concern("Below this lender's preferred ${money(p)} in annual revenue.")
        }
        product.preferredMinCreditScore?.let { p ->
            if (credit != null && credit.low < p) concern("Below this lender's preferred credit score of $p.")
        }

        val decision = when {
            disq.isNotEmpty() -> Decision.LIKELY_NOT_ELIGIBLE
            missing.isNotEmpty() -> Decision.NEED_MORE_INFO
            else -> Decision.LIKELY_ELIGIBLE
        }
        val score = if (decision == Decision.LIKELY_ELIGIBLE) {
            (BASE_SCORE + factors.sumOf { it.points }).coerceIn(0, 100)
        } else 0

        val likelyRange = if (decision == Decision.LIKELY_ELIGIBLE) {
            val high = minOf(product.maxRequestedAmount, revenueCap ?: Long.MAX_VALUE)
            if (high >= product.minRequestedAmount) product.minRequestedAmount..high else null
        } else null

        return ProductMatch(
            product = product,
            decision = decision,
            fitScore = score,
            strengths = strengths,
            concerns = concerns,
            disqualifiers = disq,
            missing = missing,
            scoreFactors = if (decision == Decision.LIKELY_ELIGIBLE) factors else emptyList(),
            likelyRange = likelyRange,
        )
    }

    // ---- helpers ----

    private enum class Cmp { PASS, FAIL, BORDERLINE }

    /** Is every value in this band at or above [min]? */
    private fun Band.atLeast(min: Long): Cmp {
        val top = high
        return when {
            low >= min -> Cmp.PASS
            top != null && top < min -> Cmp.FAIL
            else -> Cmp.BORDERLINE
        }
    }

    fun money(v: Long): String = "$" + String.format(Locale.US, "%,d", v)

    fun moneyRange(r: LongRange): String =
        if (r.first == r.last) money(r.first) else "${money(r.first)} – ${money(r.last)}"

    private fun percent(ratio: Double): String = "${Math.round(ratio * 100)}%"

    private fun months(m: Int): String = when {
        m % 12 == 0 && m >= 12 -> "${m / 12} year${plural(m / 12)}"
        else -> "$m months"
    }

    /** Rounds down to a friendly number: nearest $1,000, or $100 under $10k. */
    private fun roundDown(v: Long): Long = if (v >= 10_000) v / 1_000 * 1_000 else v / 100 * 100

    private fun plural(n: Int) = if (n == 1) "" else "s"
}
