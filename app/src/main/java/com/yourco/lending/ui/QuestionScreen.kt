package com.yourco.lending.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourco.lending.matching.BusinessType
import com.yourco.lending.matching.CreditBand
import com.yourco.lending.matching.EligibilityEngine
import com.yourco.lending.matching.LoanPurpose
import com.yourco.lending.matching.Question
import com.yourco.lending.matching.RevenueBand
import com.yourco.lending.matching.TimeInBusiness

@Composable
fun QuestionScreen(screen: Screen.Ask, state: UiState, vm: DiscoveryViewModel) {
    val q = screen.question
    val all = Question.entries
    val index = all.indexOf(q)
    val a = state.answers

    ScreenFrame(onBack = { vm.back() }, progress = (index + 1f) / all.size) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Question ${index + 1} of ${all.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PageTitle(q.prompt, hintFor(q))
            }
        }

        when (q) {
            Question.BUSINESS_TYPE -> items(BusinessType.entries) { t ->
                ChoiceCard(t.label, selected = a.businessType == t, onClick = {
                    vm.answer { it.copy(businessType = t) }
                    vm.next()
                })
            }

            Question.STATE -> items(US_STATES) { (code, name) ->
                ChoiceCard(name, selected = a.state == code, onClick = {
                    vm.answer { it.copy(state = code) }
                    vm.next()
                })
            }

            Question.TIME_IN_BUSINESS -> items(TimeInBusiness.entries) { t ->
                ChoiceCard(t.label, selected = a.timeInBusiness == t, onClick = {
                    vm.answer { it.copy(timeInBusiness = t) }
                    vm.next()
                })
            }

            Question.ANNUAL_REVENUE -> items(RevenueBand.entries) { r ->
                ChoiceCard(r.label, selected = a.annualRevenue == r, onClick = {
                    vm.answer { it.copy(annualRevenue = r) }
                    vm.next()
                })
            }

            Question.LOAN_PURPOSE -> items(LoanPurpose.entries) { p ->
                ChoiceCard(p.label, subtitle = p.description, selected = a.loanPurpose == p, onClick = {
                    vm.answer { it.copy(loanPurpose = p) }
                    vm.next()
                })
            }

            Question.REQUESTED_AMOUNT -> item {
                AmountInput(
                    key = q,
                    initial = a.requestedAmount,
                    label = "Amount needed",
                    quickPicks = listOf(10_000L, 25_000L, 50_000L, 100_000L, 250_000L),
                    allowZero = false,
                    maxValue = 5_000_000,
                    onSubmit = { v ->
                        vm.answer { it.copy(requestedAmount = v) }
                        vm.next()
                    },
                )
            }

            Question.CREDIT -> {
                items(CreditBand.entries) { b ->
                    ChoiceCard(b.label, selected = a.credit == b, onClick = {
                        vm.setCredit(b)
                        vm.next()
                    })
                }
                item {
                    ChoiceCard(
                        "Not sure",
                        subtitle = "We'll show what we can. Some lenders need this to check your fit.",
                        selected = state.creditNotSure,
                        onClick = {
                            vm.setCredit(null)
                            vm.next()
                        },
                    )
                }
            }

            Question.MONTHLY_DEBT -> item {
                AmountInput(
                    key = q,
                    initial = a.monthlyDebtPayments,
                    label = "Monthly payments",
                    quickPicks = listOf(0L, 500L, 1_000L, 2_500L, 5_000L),
                    allowZero = true,
                    maxValue = 1_000_000,
                    onSubmit = { v ->
                        vm.answer { it.copy(monthlyDebtPayments = v) }
                        vm.next()
                    },
                )
            }
        }
    }
}

@Composable
private fun AmountInput(
    key: Question,
    initial: Long?,
    label: String,
    quickPicks: List<Long>,
    allowZero: Boolean,
    maxValue: Long,
    onSubmit: (Long) -> Unit,
) {
    var text by rememberSaveable(key) { mutableStateOf(initial?.toString() ?: "") }
    val value = text.toLongOrNull()
    val valid = value != null && (if (allowZero) value >= 0 else value > 0) && value <= maxValue
    val submit: () -> Unit = { if (valid && value != null) onSubmit(value) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() }.take(9) },
            label = { Text(label) },
            prefix = { Text("$") },
            singleLine = true,
            isError = text.isNotEmpty() && !valid,
            supportingText = {
                when {
                    valid && value != null -> Text(EligibilityEngine.money(value))
                    text.isEmpty() -> Text(if (allowZero) "Enter 0 if none" else "Whole dollars")
                    value != null && value > maxValue -> Text("Enter ${EligibilityEngine.money(maxValue)} or less")
                    else -> Text("Enter an amount above \$0")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickPicks.forEach { pick ->
                OutlinedButton(onClick = { text = pick.toString() }) {
                    Text(if (pick == 0L) "None" else shortMoney(pick))
                }
            }
        }
        Button(
            onClick = { submit() },
            enabled = valid,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) { Text("Continue") }
    }
}

private fun shortMoney(v: Long): String = when {
    v >= 1_000_000 && v % 1_000_000 == 0L -> "\$${v / 1_000_000}M"
    v >= 1_000 && v % 1_000 == 0L -> "\$${v / 1_000}k"
    v >= 1_000 && v % 100 == 0L -> "\$${v / 1_000}.${(v % 1_000) / 100}k"
    else -> EligibilityEngine.money(v)
}

private fun hintFor(q: Question): String = when (q) {
    Question.BUSINESS_TYPE -> "Pick the closest match."
    Question.STATE -> "Where the business operates."
    Question.TIME_IN_BUSINESS -> "Count from when the business started taking paying customers."
    Question.ANNUAL_REVENUE -> "Total sales before expenses. A rough range is fine."
    Question.LOAN_PURPOSE -> "Lenders fund different things, so this changes your matches."
    Question.REQUESTED_AMOUNT -> "A rough number is fine. You can change it later."
    Question.CREDIT -> "Your best guess is fine. We don't check your credit."
    Question.MONTHLY_DEBT -> "Include loan and cash-advance payments. Don't include rent or payroll."
}
