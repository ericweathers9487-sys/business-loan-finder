package com.yourco.lending.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourco.lending.matching.Decision
import com.yourco.lending.matching.Disclosures
import com.yourco.lending.matching.DiscoveryResult
import com.yourco.lending.matching.EligibilityEngine
import com.yourco.lending.matching.ProductMatch
import com.yourco.lending.matching.Question

@Composable
fun ResultsScreen(state: UiState, vm: DiscoveryViewModel) {
    val r = state.result ?: return

    ScreenFrame(onBack = { vm.back() }) {
        item { DecisionBanner(r) }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }

        if (r.missingQuestions.isNotEmpty()) {
            item { MissingInfoCard(r, onAnswer = { vm.answerMissing(r.missingQuestions.first()) }) }
        }

        if (r.matches.isNotEmpty()) {
            item { SectionHeader("Your matches") }
            items(r.matches, key = { "match-" + it.product.id }) { m ->
                MatchCard(m, onChoose = { vm.chooseLender(m.product.id) })
            }
        }

        if (r.nearMisses.isNotEmpty()) {
            item {
                SectionHeader(if (r.matches.isEmpty()) "Why these lenders don't fit yet" else "Not a fit right now")
            }
            items(r.nearMisses, key = { "miss-" + it.product.id }) { m -> NearMissCard(m) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.editAnswers() }) { Text("Edit answers") }
                TextButton(onClick = { vm.startOver() }) { Text("Start over") }
            }
        }
    }
}

@Composable
private fun DecisionBanner(r: DiscoveryResult) {
    val c = MaterialTheme.colorScheme
    val (container, onContainer) = when (r.decision) {
        Decision.LIKELY_ELIGIBLE -> c.primaryContainer to c.onPrimaryContainer
        Decision.NEED_MORE_INFO -> c.tertiaryContainer to c.onTertiaryContainer
        Decision.LIKELY_NOT_ELIGIBLE -> c.surfaceVariant to c.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.decision.label, style = MaterialTheme.typography.headlineSmall)
            r.likelyRange?.let {
                Text("Likely range: ${EligibilityEngine.moneyRange(it)}", style = MaterialTheme.typography.titleMedium)
            }
            Text(r.nextStep, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MissingInfoCard(r: DiscoveryResult, onAnswer: () -> Unit) {
    val n = r.missingQuestions.size
    val p = r.pending.size
    OutlinedPanel {
        Text(
            "Answer $n more question${if (n == 1) "" else "s"} to check $p more option${if (p == 1) "" else "s"}",
            style = MaterialTheme.typography.titleSmall,
        )
        if (Question.CREDIT in r.missingQuestions) {
            FinePrint("Tip: many banks and credit card apps show your credit score for free.")
        }
        Button(onClick = onAnswer) { Text("Answer now") }
    }
}

@Composable
private fun MatchCard(m: ProductMatch, onChoose: () -> Unit) {
    var showWhy by rememberSaveable(m.product.id) { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val p = m.product

    OutlinedPanel {
        LenderHeader(m)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Fit score ${m.fitScore} / 100", style = MaterialTheme.typography.labelLarge)
            val progress = m.fitScore / 100f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        m.likelyRange?.let {
            Text("Likely amount: ${EligibilityEngine.moneyRange(it)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Terms: ${p.termMonths.first}–${p.termMonths.last} months", style = MaterialTheme.typography.bodyMedium)

        m.strengths.forEach { MarkedLine("✓", it, c.primary) }
        m.concerns.forEach { MarkedLine("!", it, c.tertiary) }

        TextButton(onClick = { showWhy = !showWhy }) {
            Text(if (showWhy) "Hide score details" else "Why this score?")
        }
        if (showWhy) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ScoreRow("Starting score", EligibilityEngine.BASE_SCORE, signed = false)
                m.scoreFactors.forEach { ScoreRow(it.reason, it.points) }
                val raw = EligibilityEngine.BASE_SCORE + m.scoreFactors.sumOf { it.points }
                if (raw != m.fitScore) FinePrint("Scores are capped between 0 and 100.")
            }
        }

        if (m.fitScore >= p.minFitScoreToRoute) {
            Button(
                onClick = onChoose,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Share my details with ${p.lenderName}") }
        } else {
            FinePrint("${p.lenderName} is only taking fit scores of ${p.minFitScoreToRoute} or higher right now.")
        }
    }
}

@Composable
private fun NearMissCard(m: ProductMatch) {
    val c = MaterialTheme.colorScheme
    OutlinedPanel {
        LenderHeader(m)
        m.disqualifiers.forEach { d ->
            MarkedLine("×", d.reason, c.error)
            d.fix?.let {
                Text(
                    "What would change this: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp),
                )
            }
        }
    }
}

@Composable
private fun LenderHeader(m: ProductMatch) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(m.product.lenderName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (m.product.isSample) Pill("Sample lender", c.tertiaryContainer, c.onTertiaryContainer)
    }
    Text(m.product.productName, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
}

@Composable
private fun ScoreRow(label: String, points: Int, signed: Boolean = true) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            if (signed && points > 0) "+$points" else "$points",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun OutlinedPanel(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}
