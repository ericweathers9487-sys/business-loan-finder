package com.yourco.lending.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourco.lending.R
import com.yourco.lending.matching.Disclosures

@Composable
fun ConsentScreen(screen: Screen.Consent, state: UiState, vm: DiscoveryViewModel) {
    val match = state.result?.matches?.firstOrNull { it.product.id == screen.productId } ?: return
    val product = match.product
    val contact = state.contact
    val problems = if (state.showContactErrors) contact.problems() else emptyList()
    val uriHandler = LocalUriHandler.current
    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val c = MaterialTheme.colorScheme

    ScreenFrame(onBack = { vm.back() }) {
        item {
            PageTitle(
                text = "Share with ${product.lenderName}",
                supporting = "${product.productName}. Fit score ${match.fitScore} / 100.",
            )
        }
        if (product.isSample) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = c.tertiaryContainer,
                    contentColor = c.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(Disclosures.SAMPLE_LENDER_NOTE, modifier = Modifier.padding(12.dp))
                }
            }
        }
        item { FinePrint(Disclosures.HOW_SHARING_WORKS) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactField("Your full name", contact.fullName, KeyboardType.Text, KeyboardCapitalization.Words) { v ->
                    vm.updateContact { it.copy(fullName = v) }
                }
                ContactField("Business name", contact.businessName, KeyboardType.Text, KeyboardCapitalization.Words) { v ->
                    vm.updateContact { it.copy(businessName = v) }
                }
                ContactField("Email", contact.email, KeyboardType.Email, KeyboardCapitalization.None) { v ->
                    vm.updateContact { it.copy(email = v) }
                }
                ContactField("Mobile phone", contact.phone, KeyboardType.Phone, KeyboardCapitalization.None) { v ->
                    vm.updateContact { it.copy(phone = v) }
                }
                problems.forEach { Text(it, color = c.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.consentChecked,
                        onValueChange = { vm.setConsent(it) },
                        role = Role.Checkbox,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(checked = state.consentChecked, onCheckedChange = null)
                Text(
                    Disclosures.consentText(product.lenderName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                )
            }
        }
        item {
            TextButton(onClick = { runCatching { uriHandler.openUri(privacyUrl) } }) {
                Text("Read our privacy policy")
            }
        }

        state.sendError?.let { err ->
            item { Text(err, color = c.error, style = MaterialTheme.typography.bodyMedium) }
        }

        item {
            Button(
                onClick = { vm.send() },
                enabled = state.consentChecked && !state.sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(if (state.sending) "Sending…" else "Send to ${product.lenderName}")
            }
        }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }
    }
}

@Composable
private fun ContactField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    capitalization: KeyboardCapitalization,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(120)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
