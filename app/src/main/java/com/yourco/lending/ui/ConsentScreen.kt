package com.yourco.lending.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yourco.lending.R
import com.yourco.lending.matching.BorrowerContact
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ScoreRing(match.fitScore, size = 60.dp, stroke = 6.dp)
                Column(Modifier.weight(1f)) {
                    PageTitle(text = "Share with ${product.lenderName}", eyebrow = product.productName)
                }
            }
        }
        if (vm.staysOnDevice(product)) {
            item {
                Callout(
                    icon = Icons.Filled.Info,
                    text = if (product.isSample) Disclosures.SAMPLE_LENDER_NOTE
                    else "Test mode: this build isn't connected to a server. Your details stay on this phone and nothing is sent to ${product.lenderName}.",
                    tint = c.tertiary,
                    container = c.tertiaryContainer,
                    onContainer = c.onTertiaryContainer,
                )
            }
        } else {
            item {
                Callout(
                    icon = Icons.Filled.Lock,
                    text = "Your details are encrypted and go only to ${product.lenderName}. " +
                        "They see your name and contact info only if they accept your request.",
                    tint = c.primary,
                    container = c.primaryContainer,
                    onContainer = c.onPrimaryContainer,
                )
            }
            item { FinePrint(Disclosures.HOW_SHARING_WORKS) }
        }

        item {
            Panel {
                Text("Your contact details", style = MaterialTheme.typography.titleMedium)
                ContactField("Your full name", contact.fullName, Icons.Outlined.Person, KeyboardType.Text, KeyboardCapitalization.Words) { v ->
                    vm.updateContact { it.copy(fullName = v) }
                }
                ContactField("Business name", contact.businessName, Icons.Outlined.Home, KeyboardType.Text, KeyboardCapitalization.Words) { v ->
                    vm.updateContact { it.copy(businessName = v) }
                }
                ContactField("Email", contact.email, Icons.Outlined.Email, KeyboardType.Email, KeyboardCapitalization.None) { v ->
                    vm.updateContact { it.copy(email = v) }
                }
                ContactField("Mobile phone", contact.phone, Icons.Outlined.Phone, KeyboardType.Phone, KeyboardCapitalization.None) { v ->
                    vm.updateContact { it.copy(phone = v) }
                }
                problems.forEach { Text(it, color = c.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = c.surface,
                border = if (state.consentChecked) BorderStroke(2.dp, Brand.action) else BorderStroke(1.dp, c.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .toggleable(
                            value = state.consentChecked,
                            onValueChange = { vm.setConsent(it) },
                            role = Role.Checkbox,
                        )
                        .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = state.consentChecked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = c.primary),
                        modifier = Modifier.padding(12.dp),
                    )
                    Text(
                        Disclosures.consentText(product.lenderName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        item {
            TextButton(onClick = { runCatching { uriHandler.openUri(privacyUrl) } }) {
                Text("Read our privacy policy")
            }
        }

        state.sendError?.let { err ->
            item {
                Callout(
                    icon = Icons.Filled.Warning,
                    text = err,
                    tint = c.error,
                    container = c.errorContainer,
                    onContainer = c.onErrorContainer,
                )
            }
        }

        item {
            GradientButton(
                text = if (state.sending) "Sending…" else "Send to ${product.lenderName}",
                onClick = { vm.send() },
                enabled = state.consentChecked,
                loading = state.sending,
                icon = Icons.AutoMirrored.Filled.Send,
            )
        }
        item { FinePrint(Disclosures.NOT_AN_APPROVAL) }
    }
}

@Composable
private fun Callout(
    icon: ImageVector,
    text: String,
    tint: Color,
    container: Color,
    onContainer: Color,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = container, contentColor = onContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContactField(
    label: String,
    value: String,
    icon: ImageVector,
    keyboardType: KeyboardType,
    capitalization: KeyboardCapitalization,
    onChange: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(BorrowerContact.MAX_FIELD_LENGTH)) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = c.outline),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
