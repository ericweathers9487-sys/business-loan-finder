package com.yourco.lending.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourco.lending.matching.Disclosures

@Composable
fun LoanFinderApp(vm: DiscoveryViewModel) {
    val state by vm.state.collectAsState()

    BackHandler(enabled = state.screen != Screen.Welcome) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            when (val s = state.screen) {
                Screen.Welcome -> WelcomeScreen(onStart = { vm.start() })
                is Screen.Ask -> QuestionScreen(s, state, vm)
                Screen.Results -> ResultsScreen(state, vm)
                is Screen.Consent -> ConsentScreen(s, state, vm)
                is Screen.Sent -> SentScreen(s, vm)
            }
        }
    }
}

@Composable
private fun SentScreen(screen: Screen.Sent, vm: DiscoveryViewModel) {
    ScreenFrame(onBack = null) {
        item { Spacer(Modifier.height(24.dp)) }
        if (screen.testMode) {
            item {
                PageTitle(
                    text = "Test lead saved",
                    supporting = "This lead was saved on this phone only. Nothing was sent to a lender.",
                )
            }
        } else {
            item {
                PageTitle(
                    text = "Sent to ${screen.lenderName}",
                    supporting = "${screen.lenderName} now has your request and may contact you about financing. " +
                        "They make the final decision.",
                )
            }
        }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }
        item {
            Button(
                onClick = { vm.back() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Back to my results") }
        }
        item { TextButton(onClick = { vm.startOver() }) { Text("Start over") } }
    }
}
