package com.yourco.lending.matching

/*
 * Everything the borrower tells us during discovery. Every choice is typed so
 * the UI shows selection cards, never free text, and the engine never has to
 * guess what "landscaping/snow removal llc" means.
 *
 * Beta vertical: home services.
 */

enum class BusinessType(val label: String) {
    HVAC("HVAC"),
    PLUMBING("Plumbing"),
    ELECTRICAL("Electrical"),
    ROOFING("Roofing"),
    LANDSCAPING("Landscaping & lawn care"),
    CLEANING("Cleaning & janitorial"),
    PEST_CONTROL("Pest control"),
    REMODELING("Remodeling / general contractor"),
    OTHER_HOME_SERVICES("Other home services"),
}

enum class LoanPurpose(val label: String, val description: String) {
    WORKING_CAPITAL("Working capital", "Payroll, materials, or covering slow-paying jobs"),
    EQUIPMENT("Equipment or vehicles", "Trucks, vans, tools, or machinery"),
    EXPANSION("Expansion", "New crew, new location, or a new service line"),
    MATERIALS("Materials & inventory", "Stock up on parts and supplies"),
    REFINANCE("Refinance debt", "Replace existing loans or cash advances"),
}

/**
 * A band answer. Borrowers rarely know exact figures, so most numeric answers
 * are ranges. [high] is inclusive; null means "and up".
 */
interface Band {
    val low: Long
    val high: Long?
}

enum class TimeInBusiness(val label: String, override val low: Long, override val high: Long?) : Band {
    UNDER_6_MONTHS("Less than 6 months", 0, 5),
    MONTHS_6_TO_12("6 to 12 months", 6, 11),
    YEARS_1_TO_2("1 to 2 years", 12, 23),
    YEARS_2_TO_3("2 to 3 years", 24, 35),
    YEARS_3_TO_5("3 to 5 years", 36, 59),
    YEARS_5_PLUS("5+ years", 60, null),
}

enum class RevenueBand(val label: String, override val low: Long, override val high: Long?) : Band {
    UNDER_100K("Under \$100k", 0, 99_999),
    K100_TO_250K("\$100k – \$250k", 100_000, 249_999),
    K250_TO_500K("\$250k – \$500k", 250_000, 499_999),
    K500_TO_1M("\$500k – \$1M", 500_000, 999_999),
    M1_TO_2_5M("\$1M – \$2.5M", 1_000_000, 2_499_999),
    OVER_2_5M("\$2.5M+", 2_500_000, null),
}

enum class CreditBand(val label: String, override val low: Long, override val high: Long?) : Band {
    EXCELLENT("720 or higher", 720, 850),
    GOOD("680 – 719", 680, 719),
    FAIR("640 – 679", 640, 679),
    BUILDING("600 – 639", 600, 639),
    LOW("Below 600", 300, 599),
}

/** The questions, in the order the app asks them. */
enum class Question(val prompt: String) {
    BUSINESS_TYPE("What kind of home services business is it?"),
    STATE("Which state is the business in?"),
    TIME_IN_BUSINESS("How long has the business been operating?"),
    ANNUAL_REVENUE("About how much revenue did the business bring in over the last 12 months?"),
    LOAN_PURPOSE("What will you use the money for?"),
    REQUESTED_AMOUNT("How much do you need?"),
    CREDIT("What's the owner's personal credit score?"),
    MONTHLY_DEBT("How much does the business pay each month on existing loans or advances?"),
}

data class DiscoveryAnswers(
    val businessType: BusinessType? = null,
    /** Two-letter USPS code, e.g. "AL". */
    val state: String? = null,
    val timeInBusiness: TimeInBusiness? = null,
    val annualRevenue: RevenueBand? = null,
    val loanPurpose: LoanPurpose? = null,
    /** Whole dollars. */
    val requestedAmount: Long? = null,
    /** null = not answered or "not sure". */
    val credit: CreditBand? = null,
    /** Whole dollars per month. 0 is a valid answer. */
    val monthlyDebtPayments: Long? = null,
) {
    fun isAnswered(q: Question): Boolean = when (q) {
        Question.BUSINESS_TYPE -> businessType != null
        Question.STATE -> state != null
        Question.TIME_IN_BUSINESS -> timeInBusiness != null
        Question.ANNUAL_REVENUE -> annualRevenue != null
        Question.LOAN_PURPOSE -> loanPurpose != null
        Question.REQUESTED_AMOUNT -> requestedAmount != null && requestedAmount > 0
        Question.CREDIT -> credit != null
        Question.MONTHLY_DEBT -> monthlyDebtPayments != null && monthlyDebtPayments >= 0
    }
}
