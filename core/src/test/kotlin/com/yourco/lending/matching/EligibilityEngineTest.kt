package com.yourco.lending.matching

import com.yourco.lending.catalog.SampleProducts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EligibilityEngineTest {

    private val products = SampleProducts.all

    private val strongHvac = DiscoveryAnswers(
        businessType = BusinessType.HVAC,
        state = "AL",
        timeInBusiness = TimeInBusiness.YEARS_5_PLUS,
        annualRevenue = RevenueBand.M1_TO_2_5M,
        loanPurpose = LoanPurpose.EQUIPMENT,
        requestedAmount = 150_000,
        credit = CreditBand.EXCELLENT,
        monthlyDebtPayments = 2_000,
    )

    @Test
    fun strongBorrowerIsLikelyEligibleWithRankedMatches() {
        val r = EligibilityEngine.discover(strongHvac, products)

        assertEquals(Decision.LIKELY_ELIGIBLE, r.decision)
        assertEquals(listOf("sample-a-equipment", "sample-c-term-loan"), r.matches.map { it.product.id })
        assertEquals(95, r.matches[0].fitScore)
        assertEquals(85, r.matches[1].fitScore)
        assertEquals(10_000L..250_000L, r.likelyRange)
        // B doesn't fund equipment; D doesn't fund HVAC.
        assertEquals(setOf("sample-b-working-capital", "sample-d-microloan"), r.nearMisses.map { it.product.id }.toSet())
    }

    @Test
    fun everyScorePointHasAWrittenReason() {
        val r = EligibilityEngine.discover(strongHvac, products)
        for (m in r.matches) {
            val expected = (EligibilityEngine.BASE_SCORE + m.scoreFactors.sumOf { it.points }).coerceIn(0, 100)
            assertEquals("score for ${m.product.id}", expected, m.fitScore)
            assertTrue(m.scoreFactors.all { it.reason.isNotBlank() })
            assertTrue(m.strengths.isNotEmpty())
        }
    }

    @Test
    fun notSureAboutCreditAsksForMoreInfo() {
        val answers = DiscoveryAnswers(
            businessType = BusinessType.HVAC,
            state = "AL",
            timeInBusiness = TimeInBusiness.YEARS_1_TO_2,
            annualRevenue = RevenueBand.K250_TO_500K,
            loanPurpose = LoanPurpose.WORKING_CAPITAL,
            requestedAmount = 30_000,
            credit = null,
            monthlyDebtPayments = 0,
        )
        val r = EligibilityEngine.discover(answers, products)

        assertEquals(Decision.NEED_MORE_INFO, r.decision)
        assertEquals(listOf(Question.CREDIT), r.missingQuestions)
        assertEquals(listOf("sample-b-working-capital"), r.pending.map { it.product.id })
        assertEquals("Answer 1 more question so we can check 1 lender option.", r.nextStep)
        assertNull(r.likelyRange)
    }

    @Test
    fun notEligibleExplainsReasonsAndClosestFix() {
        val answers = DiscoveryAnswers(
            businessType = BusinessType.LANDSCAPING,
            state = "TX",
            timeInBusiness = TimeInBusiness.YEARS_3_TO_5,
            annualRevenue = RevenueBand.K100_TO_250K,
            loanPurpose = LoanPurpose.WORKING_CAPITAL,
            requestedAmount = 60_000,
            credit = CreditBand.GOOD,
            monthlyDebtPayments = 500,
        )
        val r = EligibilityEngine.discover(answers, products)

        assertEquals(Decision.LIKELY_NOT_ELIGIBLE, r.decision)
        assertTrue(r.matches.isEmpty())
        assertEquals(
            listOf("sample-b-working-capital", "sample-c-term-loan", "sample-a-equipment", "sample-d-microloan"),
            r.nearMisses.map { it.product.id },
        )
        assertEquals(
            "Closest option: Sample Lender B · Working capital line. Lower your request to about \$37,000.",
            r.nextStep,
        )
        val d = r.nearMisses.first { it.product.id == "sample-d-microloan" }
        assertEquals(2, d.disqualifiers.size)
        assertTrue(d.disqualifiers.any { it.reason.contains("isn't available in TX") })
        assertTrue(r.nearMisses.all { it.fitScore == 0 && it.scoreFactors.isEmpty() })
    }

    @Test
    fun borderlineCreditLowersScoreButDoesNotDisqualify() {
        val product = LenderProduct(
            id = "t-650",
            lenderName = "Test",
            productName = "650 minimum",
            minCreditScore = 650,
            maxRequestedAmount = 100_000,
            termMonths = 12..36,
        )
        val m = EligibilityEngine.evaluate(product, strongHvac.copy(requestedAmount = 40_000, credit = CreditBand.FAIR))

        assertEquals(Decision.LIKELY_ELIGIBLE, m.decision)
        assertEquals(1, m.concerns.size)
        assertTrue(m.scoreFactors.any { it.points == -10 })
        // 60 base + 10 no time minimum + 10 no revenue minimum + 5 well within limit
        // - 10 borderline credit + 5 no debt limit = 80
        assertEquals(80, m.fitScore)
    }

    @Test
    fun revenueCapShrinksLikelyRange() {
        val m = EligibilityEngine.evaluate(
            products.first { it.id == "sample-c-term-loan" },
            strongHvac.copy(annualRevenue = RevenueBand.K500_TO_1M, requestedAmount = 100_000),
        )
        assertEquals(Decision.LIKELY_ELIGIBLE, m.decision)
        // 25% of the $500k band floor = $125k cap, below the $500k product max.
        assertEquals(25_000L..125_000L, m.likelyRange)
    }

    @Test
    fun inactiveProductsAreIgnored() {
        val r = EligibilityEngine.discover(strongHvac, products.map { it.copy(active = false) })
        assertEquals(Decision.LIKELY_NOT_ELIGIBLE, r.decision)
        assertEquals("No lenders are available in the beta right now. Check back soon.", r.nextStep)
    }

    @Test
    fun productRejectsInvertedAmounts() {
        try {
            LenderProduct(id = "bad", lenderName = "x", productName = "y",
                minRequestedAmount = 10, maxRequestedAmount = 5, termMonths = 1..2)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun moneyFormatting() {
        assertEquals("\$1,234,567", EligibilityEngine.money(1_234_567))
        assertEquals("\$5,000 – \$100,000", EligibilityEngine.moneyRange(5_000L..100_000L))
    }

    // ---- Lead routing ----

    private val contact = BorrowerContact("Eric Sample", "Sample HVAC LLC", "owner@example.com", "(205) 555-0142")
    private val router = LeadRouter(clock = { 1_000L }, newId = { "lead-1" })

    private fun topMatch() = EligibilityEngine.discover(strongHvac, products).matches.first()

    private fun consentFor(m: ProductMatch, version: String = Disclosures.VERSION) =
        BorrowerConsent(m.product.id, version, 999L)

    @Test
    fun routesOnlyWithConsentForThatLender() {
        val m = topMatch()
        assertTrue(router.route(m, strongHvac, contact, null, "b-1") is RoutingDecision.Hold)
        val wrong = BorrowerConsent("some-other-product", Disclosures.VERSION, 1L)
        assertTrue(router.route(m, strongHvac, contact, wrong, "b-1") is RoutingDecision.Hold)
        val stale = consentFor(m, version = "old")
        assertTrue(router.route(m, strongHvac, contact, stale, "b-1") is RoutingDecision.Hold)
    }

    @Test
    fun holdsIncompleteContact() {
        val m = topMatch()
        val bad = contact.copy(phone = "555-01")
        val d = router.route(m, strongHvac, bad, consentFor(m), "b-1")
        assertEquals(RoutingDecision.Hold("Contact details are incomplete."), d)
    }

    @Test
    fun holdsLeadsBelowLenderThreshold() {
        val m = topMatch().let { it.copy(product = it.product.copy(minFitScoreToRoute = 99)) }
        val d = router.route(m, strongHvac, contact, consentFor(m), "b-1")
        assertTrue(d is RoutingDecision.Hold)
        assertTrue((d as RoutingDecision.Hold).reason.contains("below this lender's minimum of 99"))
    }

    @Test
    fun routedLeadHasReadableAnonymizedCard() {
        val m = topMatch()
        val d = router.route(m, strongHvac, contact, consentFor(m), "b-1")
        assertTrue(d is RoutingDecision.Route)
        val lead = (d as RoutingDecision.Route).lead
        assertEquals("lead-1", lead.card.leadId)
        assertEquals("b-1", lead.card.borrowerRef)
        assertEquals("HVAC", lead.card.businessType)
        assertEquals(150_000L, lead.card.requestedAmount)
        assertEquals(95, lead.card.fitScore)
        assertTrue(lead.card.keyReasons.size in 1..3)
        assertTrue(lead.card.isSample)
        assertEquals(listOf(LeadEvent(LeadEventType.CREATED, 1_000L)), lead.events)
        // The card itself never carries contact details.
        assertFalse(lead.card.toString().contains("owner@example.com"))
    }

    @Test
    fun contactValidation() {
        assertTrue(contact.isComplete())
        assertTrue(contact.copy(phone = "1-205-555-0142").isComplete())
        assertFalse(contact.copy(email = "nope").isComplete())
        assertFalse(contact.copy(fullName = " ").isComplete())
        assertNotNull(contact.problems())
    }
}
