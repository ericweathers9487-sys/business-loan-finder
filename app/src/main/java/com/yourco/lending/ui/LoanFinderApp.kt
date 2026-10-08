package com.yourco.lending.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yourco.lending.matching.Disclosures

@Composable
fun LoanFinderApp(vm: DiscoveryViewModel) {
    val state by vm.state.collectAsState()

    BackHandler(enabled = state.screen != Screen.Welcome) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                // Forward slides in from the right, back from the left.
                val dir = if (targetState.order() >= initialState.order()) 1 else -1
                (fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(tween(300)) { dir * it / 8 }) togetherWith
                    fadeOut(tween(120))
            },
            label = "screen",
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) { screen ->
            when (screen) {
                Screen.Welcome -> WelcomeScreen(onStart = { vm.start() })
                is Screen.Ask -> QuestionScreen(screen, state, vm)
                Screen.Results -> ResultsScreen(state, vm)
                is Screen.Consent -> ConsentScreen(screen, state, vm)
                is Screen.Sent -> SentScreen(screen, vm)
            }
        }
    }
}

private fun Screen.order(): Int = when (this) {
    Screen.Welcome -> 0
    is Screen.Ask -> 1 + question.ordinal
    Screen.Results -> 100
    is Screen.Consent -> 101
    is Screen.Sent -> 102
}

@Composable
private fun SentScreen(screen: Screen.Sent, vm: DiscoveryViewModel) {
    val c = MaterialTheme.colorScheme
    ScreenFrame(onBack = null) {
        item { Spacer(Modifier.height(28.dp)) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(104.dp)
                        .shadow(24.dp, CircleShape, ambientColor = Brand.Mint, spotColor = Brand.Aqua)
                        .clip(CircleShape)
                        .background(Brand.action),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (screen.testMode) "Test lead saved" else "Sent to ${screen.lenderName}",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (screen.testMode) "This lead was saved on this phone only. Nothing was sent to a lender."
                    else "${screen.lenderName} now has your request. They make the final decision.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (!screen.testMode) {
            item {
                Panel {
                    Text("What happens next", style = MaterialTheme.typography.titleMedium)
                    Step(1, "${screen.lenderName} reviews a summary of your answers, without your name or contact info.")
                    Step(2, "If it's a fit, they accept and get your contact details to reach out.")
                    Step(3, "They make every final decision on terms and approval.")
                }
            }
        }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }
        item { GradientButton("Back to my results", onClick = { vm.back() }, icon = null) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { vm.startOver() }) { Text("Start over") }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SolidColor(c.primaryContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = c.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
