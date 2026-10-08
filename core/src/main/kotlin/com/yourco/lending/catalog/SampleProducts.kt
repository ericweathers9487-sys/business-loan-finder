package com.yourco.lending.catalog

import com.yourco.lending.matching.BusinessType
import com.yourco.lending.matching.LenderProduct
import com.yourco.lending.matching.LoanPurpose

/**
 * Placeholder lender products for the home services beta.
 *
 * These are NOT real lenders and the numbers are illustrative. Replace each one
 * with a pilot lender's actual criteria before any real borrower sees results.
 * They are flagged isSample = true so they can never receive a real lead.
 */
object SampleProducts {

    private const val NOTE = "Sample rules for testing. Replace with a real pilot lender's criteria."

    val all: List<LenderProduct> = listOf(
        LenderProduct(
            id = "sample-a-equipment",
            lenderName = "Sample Lender A",
            productName = "Equipment financing",
            minMonthsInBusiness = 12,
            minAnnualRevenue = 100_000,
            minRequestedAmount = 10_000,
            maxRequestedAmount = 250_000,
            minCreditScore = 640,
            maxDebtRatio = 0.35,
            allowedLoanPurposes = setOf(LoanPurpose.EQUIPMENT),
            termMonths = 24..72,
            preferredMinMonthsInBusiness = 24,
            underwritingNotes = NOTE,
            isSample = true,
        ),
        LenderProduct(
            id = "sample-b-working-capital",
            lenderName = "Sample Lender B",
            productName = "Working capital line",
            minMonthsInBusiness = 6,
            minAnnualRevenue = 100_000,
            minRequestedAmount = 5_000,
            maxRequestedAmount = 100_000,
            maxAmountPctOfRevenue = 0.15,
            minCreditScore = 600,
            maxDebtRatio = 0.40,
            allowedLoanPurposes = setOf(LoanPurpose.WORKING_CAPITAL, LoanPurpose.MATERIALS),
            termMonths = 6..18,
            underwritingNotes = NOTE,
            isSample = true,
        ),
        LenderProduct(
            id = "sample-c-term-loan",
            lenderName = "Sample Lender C",
            productName = "Growth term loan",
            minMonthsInBusiness = 24,
            minAnnualRevenue = 250_000,
            minRequestedAmount = 25_000,
            maxRequestedAmount = 500_000,
            maxAmountPctOfRevenue = 0.25,
            minCreditScore = 680,
            maxDebtRatio = 0.30,
            allowedLoanPurposes = setOf(
                LoanPurpose.EXPANSION, LoanPurpose.EQUIPMENT,
                LoanPurpose.WORKING_CAPITAL, LoanPurpose.REFINANCE,
            ),
            termMonths = 12..60,
            preferredMinAnnualRevenue = 500_000,
            minFitScoreToRoute = 75,
            underwritingNotes = NOTE,
            isSample = true,
        ),
        LenderProduct(
            id = "sample-d-microloan",
            lenderName = "Sample Lender D",
            productName = "Starter microloan",
            minRequestedAmount = 1_000,
            maxRequestedAmount = 50_000,
            minCreditScore = 600,
            maxDebtRatio = 0.45,
            allowedBusinessTypes = setOf(
                BusinessType.LANDSCAPING, BusinessType.CLEANING,
                BusinessType.PEST_CONTROL, BusinessType.OTHER_HOME_SERVICES,
            ),
            allowedStates = setOf("AL", "GA", "MS", "TN"),
            termMonths = 12..72,
            minFitScoreToRoute = 65,
            underwritingNotes = NOTE,
            isSample = true,
        ),
    )
}
