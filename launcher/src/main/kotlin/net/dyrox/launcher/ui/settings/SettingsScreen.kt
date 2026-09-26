package net.dyrox.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.LauncherInfo
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.core.settings.ClientIdSource
import net.dyrox.launcher.core.settings.LauncherSettingsStore
import net.dyrox.launcher.ui.accounts.SecondaryButton
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.Panel
import net.dyrox.launcher.ui.components.dyroxTextFieldColors
import net.dyrox.launcher.ui.platform.DesktopActions
import net.dyrox.launcher.ui.theme.DyroxColors

private const val AZURE_APP_REGISTRATIONS = "https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade"
private const val MOJANG_APP_REVIEW_FORM = "https://aka.ms/mce-reviewappid"
private const val MOJANG_APP_REGISTRATION_HELP = "https://aka.ms/AppRegInfo"

@Composable
fun SettingsScreen(core: LauncherCore, modifier: Modifier = Modifier) {
    val settings by core.settings.settings.collectAsState()
    var clientId by remember(settings.microsoftClientId) { mutableStateOf(settings.microsoftClientId) }
    val trimmed = clientId.trim()
    val valid = trimmed.isEmpty() || LauncherSettingsStore.isValidClientId(trimmed)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", color = DyroxColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Panel(Modifier.fillMaxWidth(), title = "Microsoft sign-in") {
            Text(
                "Microsoft accounts are signed in through your own Azure app registration. " +
                    "Mojang has to approve its app ID for Minecraft before sign-in works.",
                color = DyroxColors.TextSecondary,
                fontSize = 13.sp,
            )
            Step("1", "Azure portal → App registrations → New registration. Supported account types: \"Personal Microsoft accounts only\".")
            Step("2", "Authentication → add platform \"Mobile and desktop applications\" with redirect URI http://localhost, and turn on \"Allow public client flows\".")
            Step("3", "Submit the Application (client) ID with Mojang's app review form. Until it's approved, sign-in stops with \"Invalid app registration\".")
            Step("4", "Paste the Application (client) ID below.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Open Azure portal", { DesktopActions.openUrl(AZURE_APP_REGISTRATIONS) })
                SecondaryButton("Mojang app review form", { DesktopActions.openUrl(MOJANG_APP_REVIEW_FORM) })
                SecondaryButton("Why is this needed?", { DesktopActions.openUrl(MOJANG_APP_REGISTRATION_HELP) })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Azure application (client) ID") },
                    placeholder = { Text("00000000-0000-0000-0000-000000000000", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    isError = !valid,
                    enabled = core.settings.clientIdSource != ClientIdSource.ENVIRONMENT,
                    shape = RoundedCornerShape(10.dp),
                    colors = dyroxTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                AccentButton(
                    "Save",
                    onClick = {
                        // The Save button disabling itself and the status line below are the confirmation.
                        core.settings.update { it.copy(microsoftClientId = trimmed) }
                    },
                    enabled = valid && trimmed != settings.microsoftClientId,
                )
            }
            val status = when (core.settings.clientIdSource) {
                ClientIdSource.ENVIRONMENT -> "Using the client ID from the DYROX_MS_CLIENT_ID environment variable."
                ClientIdSource.SETTINGS -> "Microsoft sign-in is enabled."
                ClientIdSource.NONE -> "Microsoft sign-in is disabled. Offline accounts still work."
            }
            Text(if (valid) status else "That doesn't look like an application ID (expected a GUID).",
                color = if (valid) DyroxColors.TextMuted else DyroxColors.Danger, fontSize = 12.sp)
        }

        Panel(Modifier.fillMaxWidth(), title = "Account security") {
            InfoRow("Storage", "accounts.vault, encrypted with AES-256-GCM")
            InfoRow("Key protection", core.accounts.keyProtection)
            InfoRow("Stored secrets", "Microsoft refresh token and Minecraft access token. No passwords, ever.")
            InfoRow("Export", "Account exports contain names and UUIDs only, never tokens.")
        }

        Panel(Modifier.fillMaxWidth(), title = "About") {
            InfoRow("Version", LauncherInfo.version)
            InfoRow("Data folder", core.paths.root.toString())
            TextButton(onClick = { DesktopActions.openFolder(core.paths.root) }) { Text("Open data folder", color = DyroxColors.Accent) }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = DyroxColors.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text, color = DyroxColors.TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = DyroxColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(0.25f))
        Text(value, color = DyroxColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(0.75f))
    }
}
