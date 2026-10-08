package com.yourco.lending.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourco.lending.BuildConfig
import com.yourco.lending.catalog.SampleProducts
import com.yourco.lending.leads.HttpLeadSink
import com.yourco.lending.leads.LeadSink
import com.yourco.lending.leads.LocalLeadSink
import com.yourco.lending.leads.SubmitResult
import com.yourco.lending.matching.BorrowerConsent
import com.yourco.lending.matching.BorrowerContact
import com.yourco.lending.matching.CreditBand
import com.yourco.lending.matching.Disclosures
import com.yourco.lending.matching.DiscoveryAnswers
import com.yourco.lending.matching.DiscoveryResult
import com.yourco.lending.matching.EligibilityEngine
import com.yourco.lending.matching.LeadRouter
import com.yourco.lending.matching.LenderProduct
import com.yourco.lending.matching.Question
import com.yourco.lending.matching.RoutingDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Screen {
    data object Welcome : Screen
    /** [returnToResults] = came from the results page to fill one gap. */
    data class Ask(val question: Question, val returnToResults: Boolean = false) : Screen
    data object Results : Screen
    data class Consent(val productId: String) : Screen
    data class Sent(val lenderName: String, val testMode: Boolean) : Screen
}

data class UiState(
    val screen: Screen = Screen.Welcome,
    val answers: DiscoveryAnswers = DiscoveryAnswers(),
    val creditNotSure: Boolean = false,
    val result: DiscoveryResult? = null,
    val contact: BorrowerContact = BorrowerContact("", "", "", ""),
    val consentChecked: Boolean = false,
    /** New for each visit to a consent screen; reused if the borrower retries a failed send. */
    val submissionId: String = "",
    val showContactErrors: Boolean = false,
    val sending: Boolean = false,
    val sendError: String? = null,
) {
    fun answered(q: Question): Boolean =
        answers.isAnswered(q) || (q == Question.CREDIT && creditNotSure)
}

class DiscoveryViewModel(
    private val products: List<LenderProduct> = SampleProducts.all,
    private val router: LeadRouter = LeadRouter(),
    private val localSink: LocalLeadSink = LocalLeadSink(),
    private val remoteSink: LeadSink? =
        BuildConfig.LEAD_ENDPOINT.takeIf { it.isNotBlank() }?.let { HttpLeadSink(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val borrowerRef = "b-" + UUID.randomUUID().toString().take(8)
    private val questions = Question.entries

    /** True when a lead for this product would stay on the phone instead of reaching the lender. */
    fun staysOnDevice(product: LenderProduct): Boolean = product.isSample || remoteSink == null

    fun start() = go(Screen.Ask(questions.first()))

    fun answer(transform: (DiscoveryAnswers) -> DiscoveryAnswers) {
        _state.update { it.copy(answers = transform(it.answers)) }
    }

    fun setCredit(band: CreditBand?) {
        _state.update {
            it.copy(answers = it.answers.copy(credit = band), creditNotSure = band == null)
        }
    }

    /** Moves past the current question once it has an answer. */
    fun next() {
        val s = _state.value
        val ask = s.screen as? Screen.Ask ?: return
        if (!s.answered(ask.question)) return
        val i = questions.indexOf(ask.question)
        if (ask.returnToResults || i == questions.lastIndex) showResults()
        else go(Screen.Ask(questions[i + 1]))
    }

    /** Returns false when there's nowhere left to go back to. */
    fun back(): Boolean {
        when (val sc = _state.value.screen) {
            Screen.Welcome -> return false
            is Screen.Ask -> {
                val i = questions.indexOf(sc.question)
                go(
                    when {
                        sc.returnToResults -> Screen.Results
                        i == 0 -> Screen.Welcome
                        else -> Screen.Ask(questions[i - 1])
                    }
                )
            }
            Screen.Results -> go(Screen.Ask(questions.last()))
            is Screen.Consent -> go(Screen.Results)
            is Screen.Sent -> go(Screen.Results)
        }
        return true
    }

    fun editAnswers() = go(Screen.Ask(questions.first()))

    fun answerMissing(q: Question) = go(Screen.Ask(q, returnToResults = true))

    fun chooseLender(productId: String) {
        _state.update {
            it.copy(
                screen = Screen.Consent(productId),
                consentChecked = false,
                submissionId = UUID.randomUUID().toString(),
                showContactErrors = false,
                sendError = null,
            )
        }
    }

    fun updateContact(transform: (BorrowerContact) -> BorrowerContact) {
        _state.update { it.copy(contact = transform(it.contact), sendError = null) }
    }

    fun setConsent(checked: Boolean) {
        _state.update { it.copy(consentChecked = checked, sendError = null) }
    }

    fun send() {
        val s = _state.value
        val screen = s.screen as? Screen.Consent ?: return
        if (s.sending) return
        val match = s.result?.matches?.firstOrNull { it.product.id == screen.productId } ?: return
        if (!s.contact.isComplete()) {
            _state.update { it.copy(showContactErrors = true) }
            return
        }
        val consent = if (s.consentChecked) {
            BorrowerConsent(match.product.id, Disclosures.VERSION, System.currentTimeMillis())
        } else null

        when (val decision = router.route(match, s.answers, s.contact, consent, borrowerRef)) {
            is RoutingDecision.Hold -> _state.update { it.copy(sendError = decision.reason) }
            is RoutingDecision.Route -> {
                val sink: LeadSink =
                    if (decision.lead.card.isSample || remoteSink == null) localSink else remoteSink
                _state.update { it.copy(sending = true, sendError = null) }
                viewModelScope.launch {
                    when (val r = sink.submit(decision.lead, s.submissionId)) {
                        is SubmitResult.Sent -> _state.update {
                            it.copy(sending = false, screen = Screen.Sent(match.product.lenderName, r.testMode))
                        }
                        is SubmitResult.Failed -> _state.update {
                            it.copy(sending = false, sendError = r.message)
                        }
                    }
                }
            }
        }
    }

    fun startOver() {
        _state.value = UiState()
    }

    private fun showResults() {
        _state.update {
            it.copy(
                result = EligibilityEngine.discover(it.answers, products),
                screen = Screen.Results,
                sendError = null,
            )
        }
    }

    private fun go(screen: Screen) {
        _state.update { it.copy(screen = screen) }
    }
}
