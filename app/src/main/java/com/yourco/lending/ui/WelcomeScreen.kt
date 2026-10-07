package com.yourco.lending.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourco.lending.R
import com.yourco.lending.matching.Disclosures

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.privacy_policy_url)

    ScreenFrame(onBack = null) {
        item {
            Spacer(Modifier.height(24.dp))
            PageTitle(
                text = "See which business loans you're likely to fit",
                supporting = "Answer 8 quick questions about your home services business. " +
                    "We'll show likely options, amounts, and the reasons behind each one. " +
                    "No credit check, and nothing affects your credit score.",
            )
        }
        item {
            Pill(
                text = "Beta · home services businesses",
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        item {
            Column {
                MarkedLine("✓", "Takes about 2 minutes", MaterialTheme.colorScheme.primary)
                MarkedLine("✓", "Plain reasons for every result", MaterialTheme.colorScheme.primary)
                MarkedLine("✓", "You choose if and when a lender sees your info", MaterialTheme.colorScheme.primary)
            }
        }
        item {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text("Get started") }
        }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }
        item { FinePrint(Disclosures.HOW_SHARING_WORKS) }
        item {
            TextButton(onClick = { runCatching { uriHandler.openUri(privacyUrl) } }) {
                Text("Privacy policy")
            }
        }
    }
}
