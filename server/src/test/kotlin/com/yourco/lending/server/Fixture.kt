package com.yourco.lending.server

import com.yourco.lending.api.LeadApi
import com.yourco.lending.api.LeadSubmission
import com.yourco.lending.catalog.SampleProducts
import com.yourco.lending.matching.BorrowerConsent
import com.yourco.lending.matching.BorrowerContact
import com.yourco.lending.matching.BusinessType
import com.yourco.lending.matching.CreditBand
import com.yourco.lending.matching.Disclosures
import com.yourco.lending.matching.DiscoveryAnswers
import com.yourco.lending.matching.LoanPurpose
import com.yourco.lending.matching.RevenueBand
import com.yourco.lending.matching.TimeInBusiness
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

const val PRODUCT_A = "sample-a-equipment"
const val PRODUCT_C = "sample-c-term-loan"
const val STILL_SAMPLE = "still-a-sample"
const val KEY_A = "blf_test_key_lender_a"
const val KEY_C = "blf_test_key_lender_c"
const val ADMIN_KEY = "blf_test_admin_key"

val strongHvac = DiscoveryAnswers(
    businessType = BusinessType.HVAC,
    state = "AL",
    timeInBusiness = TimeInBusiness.YEARS_5_PLUS,
    annualRevenue = RevenueBand.M1_TO_2_5M,
    loanPurpose = LoanPurpose.EQUIPMENT,
    requestedAmount = 150_000,
    credit = CreditBand.EXCELLENT,
    monthlyDebtPayments = 2_000,
)

val dana = BorrowerContact("Dana Quintero", "Quintero Heating & Air", "Dana.Q@example.com", "(205) 555-0142")

fun submission(
    productId: String = PRODUCT_A,
    submissionId: String = "sub-1",
    answers: DiscoveryAnswers = strongHvac,
    contact: BorrowerContact = dana,
    consent: BorrowerConsent = BorrowerConsent(productId, Disclosures.VERSION, 1_000L),
) = LeadSubmission(submissionId, "b-1", productId, answers, contact, consent)

fun LeadSubmission.json() = LeadApi.json.encodeToString(this)

/** Records webhook calls; can be told to fail. */
class FakeNotifier : LenderNotifier {
    val sent = mutableListOf<Pair<String, NewLeadNotice>>()
    var succeed = true
    override suspend fun notify(webhookUrl: String, notice: NewLeadNotice): Boolean {
        if (!succeed) return false
        sent += webhookUrl to notice
        return true
    }
}

/** A server's worth of parts on a temp database, with sample rules promoted to "real" products. */
class Fixture(dir: File) {
    val dbFile = File(dir, "data/leads.db")
    var now = 1_700_000_000_000L
    private val ids = AtomicInteger()

    val lenderA = LenderAccount("Lender A", sha256Hex(KEY_A), setOf(PRODUCT_A), "https://hooks.example.com/a")
    val lenderC = LenderAccount("Lender C", sha256Hex(KEY_C), setOf(PRODUCT_C, STILL_SAMPLE))
    val notifier = FakeNotifier()
    val store = LeadStore(dbFile.path)
    val cipher = PiiCipher(ByteArray(32) { it.toByte() })

    val service = LeadService(
        products = SampleProducts.all.map { it.copy(isSample = false) } +
            SampleProducts.all.last().copy(id = STILL_SAMPLE),
        lenders = listOf(lenderA, lenderC),
        store = store,
        cipher = cipher,
        notifier = notifier,
        retentionDays = 365,
        clock = { now },
        newId = { "lead-${ids.incrementAndGet()}" },
    )

    val adminKeySha256 = sha256Hex(ADMIN_KEY)

    /** Everything SQLite has written so far, including the write-ahead log. */
    fun databaseBytes(): String =
        listOf(dbFile, File(dbFile.path + "-wal"))
            .filter { it.exists() }
            .joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }
}
