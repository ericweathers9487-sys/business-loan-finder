package com.yourco.lending.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
        item { DecisionHero(r) }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL, icon = Icons.Filled.Info) }

        if (r.missingQuestions.isNotEmpty()) {
            item { MissingInfoCard(r, onAnswer = { vm.answerMissing(r.missingQuestions.first()) }) }
        }

        if (r.matches.isNotEmpty()) {
            item { SectionHeader("Your matches", r.matches.size) }
            items(r.matches, key = { "match-" + it.product.id }) { m ->
                MatchCard(m, onChoose = { vm.chooseLender(m.product.id) })
            }
        }

        val oneAway = r.nearMisses.filter { it.disqualifiers.size == 1 && it.disqualifiers.first().fix != null }
        if (oneAway.isNotEmpty()) {
            item { OneChangeAwayCard(oneAway) }
        }

        if (r.nearMisses.isNotEmpty()) {
            item {
                SectionHeader(if (r.matches.isEmpty()) "Why these lenders don't fit yet" else "Not a fit right now", r.nearMisses.size)
            }
            items(r.nearMisses, key = { "miss-" + it.product.id }) { m -> NearMissCard(m) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.editAnswers() }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Edit answers")
                }
                TextButton(onClick = { vm.startOver() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Start over")
                }
            }
        }
    }
}

@Composable
private fun DecisionHero(r: DiscoveryResult) {
    val (brush, glow) = when (r.decision) {
        Decision.LIKELY_ELIGIBLE -> Brand.hero to Brand.Mint
        Decision.NEED_MORE_INFO -> Brand.heroAmber to Color(0xFFFBBF24)
        Decision.LIKELY_NOT_ELIGIBLE -> Brand.heroSlate to Brand.Aqua
    }
    HeroPanel(brush, glow = glow) {
        Text(
            "YOUR RESULTS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        Text(r.decision.label, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        r.likelyRange?.let {
            Column {
                Text("Likely range", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Text(compactRange(it), style = MaterialTheme.typography.displaySmall.copy(brush = Brand.glow))
            }
        }
        Text(r.nextStep, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.matches.isNotEmpty()) GlassChip(count(r.matches.size, "match", "matches"))
            if (r.pending.isNotEmpty()) GlassChip("${r.pending.size} to check")
            if (r.nearMisses.isNotEmpty()) GlassChip("${r.nearMisses.size} not a fit")
        }
    }
}

@Composable
private fun MissingInfoCard(r: DiscoveryResult, onAnswer: () -> Unit) {
    val n = r.missingQuestions.size
    val p = r.pending.size
    val c = MaterialTheme.colorScheme
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Info, tint = c.tertiary, background = SolidColor(c.tertiaryContainer))
            Text(
                "Answer $n more question${if (n == 1) "" else "s"} to check $p more option${if (p == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        if (Question.CREDIT in r.missingQuestions) {
            FinePrint("Tip: many banks and credit card apps show your credit score for free.")
        }
        GradientButton("Answer now", onClick = onAnswer)
    }
}

@Composable
private fun MatchCard(m: ProductMatch, onChoose: () -> Unit) {
    var showWhy by rememberSaveable(m.product.id) { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (showWhy) 180f else 0f, label = "arrow")
    val c = MaterialTheme.colorScheme
    val p = m.product

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ScoreRing(m.fitScore)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.lenderName, style = MaterialTheme.typography.titleLarge)
                Text(p.productName, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                if (p.isSample) Pill("Sample lender", c.tertiaryContainer, c.onTertiaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            m.likelyRange?.let { StatTile("Likely amount", compactRange(it), Modifier.weight(1f)) }
            StatTile("Term", "${p.termMonths.first}–${p.termMonths.last} mo", Modifier.weight(1f))
        }
        // Concerns always show; strengths past the first few wait behind the toggle so cards stay scannable.
        val shownStrengths = m.strengths.take(TOP_STRENGTHS)
        val moreStrengths = m.strengths.drop(TOP_STRENGTHS)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shownStrengths.forEach { ReasonRow(ReasonKind.GOOD, it) }
            m.concerns.forEach { ReasonRow(ReasonKind.WATCH, it) }
            AnimatedVisibility(showWhy && moreStrengths.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moreStrengths.forEach { ReasonRow(ReasonKind.GOOD, it) }
                }
            }
        }

        TextButton(onClick = { showWhy = !showWhy }) {
            Text(
                when {
                    showWhy -> "Hide details"
                    moreStrengths.isNotEmpty() -> "${moreStrengths.size} more reasons and the score"
                    else -> "Why this score?"
                }
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp).rotate(arrow))
        }
        AnimatedVisibility(showWhy) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ScoreRow("Starting score", EligibilityEngine.BASE_SCORE, signed = false)
                m.scoreFactors.forEach { ScoreRow(it.reason, it.points) }
                val raw = EligibilityEngine.BASE_SCORE + m.scoreFactors.sumOf { it.points }
                if (raw != m.fitScore) FinePrint("Scores are capped between 0 and 100.")
            }
        }

        if (m.fitScore >= p.minFitScoreToRoute) {
            GradientButton("Share with ${p.lenderName}", onClick = onChoose)
        } else {
            FinePrint("${p.lenderName} is only taking fit scores of ${p.minFitScoreToRoute} or higher right now.")
        }
    }
}

/** Lenders that a single change would open, with that change. The quickest path to more options. */
@Composable
private fun OneChangeAwayCard(misses: List<ProductMatch>) {
    val c = MaterialTheme.colorScheme
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Star, tint = c.primary, background = SolidColor(c.primaryContainer))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("One change away", style = MaterialTheme.typography.titleMedium)
                Text(
                    count(misses.size, "lender", "lenders") + " could fit with one change.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
        }
        misses.forEach { m ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(m.product.lenderName, style = MaterialTheme.typography.titleSmall)
                Text(m.disqualifiers.first().fix.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NearMissCard(m: ProductMatch) {
    var expanded by rememberSaveable("miss-" + m.product.id) { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    Panel(muted = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.product.lenderName, style = MaterialTheme.typography.titleMedium)
                Text(m.product.productName, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            if (m.product.isSample) Pill("Sample lender", c.tertiaryContainer, c.onTertiaryContainer)
        }
        // The first reason is the one most worth knowing; the rest open on request.
        val shown = if (expanded) m.disqualifiers else m.disqualifiers.take(1)
        shown.forEach { d ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReasonRow(ReasonKind.BLOCKER, d.reason)
                d.fix?.let {
                    Text(
                        "What would change this: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.primary,
                        modifier = Modifier.padding(start = 30.dp),
                    )
                }
            }
        }
        val hidden = m.disqualifiers.size - 1
        if (hidden > 0) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else count(hidden, "more reason", "more reasons"))
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, points: Int, signed: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            if (signed && points > 0) "+$points" else "$points",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = when {
                !signed -> c.onSurface
                points > 0 -> c.primary
                points < 0 -> c.tertiary
                else -> c.onSurfaceVariant
            },
        )
    }
}

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

private const val TOP_STRENGTHS = 3
