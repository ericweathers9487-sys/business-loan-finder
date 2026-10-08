package com.yourco.lending.api

import com.yourco.lending.matching.BorrowerConsent
import com.yourco.lending.matching.BorrowerContact
import com.yourco.lending.matching.BusinessType
import com.yourco.lending.matching.CreditBand
import com.yourco.lending.matching.Disclosures
import com.yourco.lending.matching.DiscoveryAnswers
import com.yourco.lending.matching.LeadCard
import com.yourco.lending.matching.LeadEvent
import com.yourco.lending.matching.LeadEventType
import com.yourco.lending.matching.Lead
import com.yourco.lending.matching.LoanPurpose
import com.yourco.lending.matching.RevenueBand
import com.yourco.lending.matching.TimeInBusiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadApiTest {

    private val answers = DiscoveryAnswers(
        businessType = BusinessType.HVAC,
        state = "AL",
        timeInBusiness = TimeInBusiness.YEARS_5_PLUS,
        annualRevenue = RevenueBand.M1_TO_2_5M,
        loanPurpose = LoanPurpose.EQUIPMENT,
        requestedAmount = 150_000,
        credit = CreditBand.EXCELLENT,
        monthlyDebtPayments = 2_000,
    )

    private val lead = Lead(
        card = LeadCard(
            leadId = "on-phone-1", borrowerRef = "b-1", productId = "p-1", productName = "L · P",
            businessType = "HVAC", state = "AL", timeInBusiness = "5+ years", annualRevenue = "\$1M – \$2.5M",
            loanPurpose = "Equipment or vehicles", requestedAmount = 150_000, fitScore = 90,
            keyReasons = emptyList(), concerns = emptyList(), isSample = false,
        ),
        answers = answers,
        contact = BorrowerContact(" Dana Smith ", "Smith HVAC ", " owner@example.com", "(205) 555-0142"),
        consent = BorrowerConsent("p-1", Disclosures.VERSION, 1_000L),
        events = listOf(LeadEvent(LeadEventType.CREATED, 1_000L)),
    )

    @Test
    fun submissionCarriesRawAnswersAndNormalizedContact() {
        val sub = LeadSubmission.from(lead, "s-1")
        assertEquals("s-1", sub.submissionId)
        assertEquals("p-1", sub.productId)
        assertEquals(answers, sub.answers)
        assertEquals(BorrowerContact("Dana Smith", "Smith HVAC", "owner@example.com", "2055550142"), sub.contact)
    }

    @Test
    fun submissionRoundTripsThroughJson() {
        val sub = LeadSubmission.from(lead, "s-1")
        val text = LeadApi.json.encodeToString(sub)
        // Enums travel by name, so the server reads the same bands the phone used.
        assertTrue(text.contains("\"credit\":\"EXCELLENT\""))
        // The phone's own verdict is not part of the submission.
        assertFalse(text.contains("fitScore"))
        assertEquals(sub, LeadApi.json.decodeFromString<LeadSubmission>(text))
    }

    @Test
    fun unknownFieldsFromNewerAppsAreIgnored() {
        val text = LeadApi.json.encodeToString(LeadSubmission.from(lead, "s-1"))
            .replaceFirst("{", "{\"addedLater\":true,")
        assertEquals("s-1", LeadApi.json.decodeFromString<LeadSubmission>(text).submissionId)
    }

    @Test
    fun answerProblemsCatchValuesTheAppCantProduce() {
        assertTrue(answers.problems().isEmpty())
        assertFalse(answers.copy(state = "ZZ").problems().isEmpty())
        assertFalse(answers.copy(requestedAmount = 0).problems().isEmpty())
        assertFalse(answers.copy(monthlyDebtPayments = -1).problems().isEmpty())
        assertTrue(DiscoveryAnswers().problems().isEmpty())
    }

    @Test
    fun overlongContactFieldsAreRejected() {
        val c = lead.contact.normalized()
        assertTrue(c.isComplete())
        assertFalse(c.copy(fullName = "x".repeat(BorrowerContact.MAX_FIELD_LENGTH + 1)).isComplete())
    }
}
