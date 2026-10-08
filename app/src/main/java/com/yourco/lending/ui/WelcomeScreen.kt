package com.yourco.lending.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yourco.lending.R
import com.yourco.lending.matching.Disclosures
import com.yourco.lending.matching.Question

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val c = MaterialTheme.colorScheme
    val questionCount = Question.entries.size

    ScreenFrame(onBack = null) {
        item {
            HeroPanel(Brand.hero) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBadge(Icons.Filled.Home, tint = Color.White, background = Brand.action, size = 36.dp)
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    GlassChip("Beta")
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Find the loans you're likely to fit.",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                )
                Text(
                    "Answer $questionCount quick questions about your home services business. " +
                        "See likely options, amounts, and the reasons behind each one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassStat("2 min", "to finish", Modifier.weight(1f).fillMaxHeight())
                    GlassStat("$questionCount", "questions", Modifier.weight(1f).fillMaxHeight())
                    GlassStat("0", "credit checks", Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        item {
            Panel {
                Feature(
                    Icons.Filled.Search,
                    "Likely matches, explained",
                    "Every result shows the reasons behind it, and what would change it.",
                )
                Feature(
                    Icons.Filled.Lock,
                    "Your details stay yours",
                    "A lender sees your contact info only if you pick them. It's encrypted on the way and in storage.",
                )
                Feature(
                    Icons.Filled.ThumbUp,
                    "No effect on your credit",
                    "We never check your credit. Your best guesses are fine.",
                )
            }
        }
        item { GradientButton("Check my options", onClick = onStart) }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }
        item { FinePrint(Disclosures.HOW_SHARING_WORKS, icon = Icons.Filled.Lock) }
        item {
            TextButton(onClick = { runCatching { uriHandler.openUri(privacyUrl) } }) {
                Text("Privacy policy", color = c.primary)
            }
        }
    }
}

@Composable
private fun GlassStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(brush = Brand.glow))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp)) {
            IconBadge(icon, tint = c.primary, background = SolidColor(c.primaryContainer))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}
