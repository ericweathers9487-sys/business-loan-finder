package com.yourco.lending.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
import com.yourco.lending.matching.US_STATES

@Composable
fun QuestionScreen(screen: Screen.Ask, state: UiState, vm: DiscoveryViewModel) {
    val q = screen.question
    val all = Question.entries
    val index = all.indexOf(q)
    val a = state.answers

    ScreenFrame(onBack = { vm.back() }, progress = (index + 1f) / all.size, stepLabel = "${index + 1} of ${all.size}") {
        item {
            PageTitle(q.prompt, supporting = hintFor(q), eyebrow = sectionFor(q))
            Spacer(Modifier.height(6.dp))
        }

        when (q) {
            Question.BUSINESS_TYPE -> items(BusinessType.entries) { t ->
                ChoiceCard(t.label, selected = a.businessType == t, onClick = {
                    vm.answer { it.copy(businessType = t) }
                    vm.next()
                })
            }

            Question.STATE -> items(US_STATES.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (code, name) ->
                        ChoiceCard(name, selected = a.state == code, compact = true, modifier = Modifier.weight(1f), onClick = {
                            vm.answer { it.copy(state = code) }
                            vm.next()
                        })
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
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
                ChoiceCard(p.label, subtitle = p.description, icon = iconFor(p), selected = a.loanPurpose == p, onClick = {
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

    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new -> text = new.filter { it.isDigit() }.take(9) },
            label = { Text(label) },
            prefix = { Text("$", style = MaterialTheme.typography.headlineMedium, color = c.onSurfaceVariant) },
            visualTransformation = ThousandsSeparators,
            textStyle = MaterialTheme.typography.headlineMedium,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.outline),
            singleLine = true,
            isError = text.isNotEmpty() && !valid,
            supportingText = {
                when {
                    valid || text.isEmpty() -> Text(if (allowZero) "Enter 0 if none" else "Whole dollars")
                    value != null && value > maxValue -> Text("Enter ${EligibilityEngine.money(maxValue)} or less")
                    else -> Text("Enter an amount above \$0")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickPicks.forEach { pick ->
                QuickPick(if (pick == 0L) "None" else compactMoney(pick), selected = value == pick) { text = pick.toString() }
            }
        }
        Spacer(Modifier.height(4.dp))
        GradientButton("Continue", onClick = submit, enabled = valid)
    }
}

@Composable
private fun QuickPick(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) c.primaryContainer else c.surface,
        contentColor = if (selected) c.onPrimaryContainer else c.onSurface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.outlineVariant),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

/** Shows 150000 as 150,000 while typing, keeping the cursor in the right place. */
private object ThousandsSeparators : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = digits.reversed().chunked(3).joinToString(",").reversed()
        val toOut = IntArray(digits.length + 1)
        val toIn = IntArray(out.length + 1)
        var i = 0
        out.forEachIndexed { t, ch ->
            toIn[t] = i
            if (ch != ',') toOut[i++] = t
        }
        toOut[digits.length] = out.length
        toIn[out.length] = digits.length
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = toOut[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int) = toIn[offset.coerceIn(0, out.length)]
        })
    }
}

private fun sectionFor(q: Question): String = when (q) {
    Question.BUSINESS_TYPE, Question.STATE, Question.TIME_IN_BUSINESS, Question.ANNUAL_REVENUE -> "Your business"
    Question.LOAN_PURPOSE, Question.REQUESTED_AMOUNT -> "Your loan"
    Question.CREDIT, Question.MONTHLY_DEBT -> "Your finances"
}

private fun iconFor(p: LoanPurpose): ImageVector = when (p) {
    LoanPurpose.WORKING_CAPITAL -> Icons.Filled.Person
    LoanPurpose.EQUIPMENT -> Icons.Filled.Build
    LoanPurpose.EXPANSION -> Icons.Filled.Add
    LoanPurpose.MATERIALS -> Icons.Filled.ShoppingCart
    LoanPurpose.REFINANCE -> Icons.Filled.Refresh
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
