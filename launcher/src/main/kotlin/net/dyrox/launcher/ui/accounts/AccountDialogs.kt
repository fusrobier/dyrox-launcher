package net.dyrox.launcher.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.DialogActions
import net.dyrox.launcher.ui.components.ModalDialog
import net.dyrox.launcher.ui.components.dyroxTextFieldColors
import net.dyrox.launcher.ui.platform.DesktopActions
import net.dyrox.launcher.ui.theme.DyroxColors
import net.dyrox.shared.auth.OfflineProfiles

/** Renders whichever account dialog is open. Placed at the window root so the backdrop covers everything. */
@Composable
fun AccountDialogs(vm: AccountsViewModel, onOpenSettings: () -> Unit) {
    val dialog = vm.dialog

    val nameDialog = dialog as? AccountDialog.AddOffline ?: (dialog as? AccountDialog.Rename)
    ModalDialog(
        visible = nameDialog != null,
        title = if (dialog is AccountDialog.Rename) "Rename offline account" else "Add offline account",
        onDismiss = vm::closeDialog,
    ) {
        val initial = (dialog as? AccountDialog.Rename)?.account?.username.orEmpty()
        var name by remember(dialog) { mutableStateOf(initial) }
        val valid = OfflineProfiles.isValidName(name.trim())
        Text(
            "Offline accounts work in singleplayer and on offline-mode servers. Online servers will reject them.",
            color = DyroxColors.TextSecondary,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(16) },
            label = { Text("Username") },
            singleLine = true,
            isError = name.isNotEmpty() && !valid,
            supportingText = { Text("3–16 characters: letters, digits and _", fontSize = 11.sp) },
            shape = RoundedCornerShape(10.dp),
            colors = dyroxTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        DialogActions {
            TextButton(onClick = vm::closeDialog) { Text("Cancel", color = DyroxColors.TextSecondary) }
            AccentButton(
                text = if (dialog is AccountDialog.Rename) "Rename" else "Add",
                onClick = {
                    when (dialog) {
                        is AccountDialog.Rename -> vm.rename(dialog.account, name)
                        else -> vm.addOffline(name)
                    }
                },
                enabled = valid,
            )
        }
    }

    val remove = dialog as? AccountDialog.ConfirmRemove
    ModalDialog(visible = remove != null, title = "Remove account?", onDismiss = vm::closeDialog) {
        Text(
            "${remove?.account?.username.orEmpty()} and its stored sign-in will be deleted from this PC. " +
                "You can add it again later.",
            color = DyroxColors.TextSecondary,
            fontSize = 13.sp,
        )
        DialogActions {
            TextButton(onClick = vm::closeDialog) { Text("Cancel", color = DyroxColors.TextSecondary) }
            AccentButton("Remove", onClick = { remove?.let { vm.remove(it.account) } }, danger = true)
        }
    }

    val login = dialog as? AccountDialog.MicrosoftLogin
    val busy = login?.state.let { it is MicrosoftLoginState.StartingDeviceCode || it is MicrosoftLoginState.WaitingForDeviceCode || it is MicrosoftLoginState.WaitingForBrowser }
    ModalDialog(
        visible = login != null,
        title = "Sign in with Microsoft",
        // While a sign-in is running only "Cancel" closes the dialog, so it can't be dismissed by a stray click.
        onDismiss = if (busy) null else vm::closeDialog,
        width = 520.dp,
    ) {
        when (val state = login?.state) {
            null -> Unit
            MicrosoftLoginState.ChooseMethod -> ChooseMethod(vm, onOpenSettings)
            MicrosoftLoginState.StartingDeviceCode -> Waiting("Requesting a sign-in code…")
            is MicrosoftLoginState.WaitingForDeviceCode -> DeviceCode(state)
            is MicrosoftLoginState.WaitingForBrowser -> {
                Waiting("Finish signing in in your browser. This window updates automatically.")
                if (state.url != null) {
                    TextButton(onClick = { DesktopActions.openUrl(state.url) }) { Text("Browser didn't open? Open it again", color = DyroxColors.Accent) }
                }
            }
            is MicrosoftLoginState.Failed -> {
                Text(state.message, color = DyroxColors.Danger, fontSize = 13.sp)
                DialogActions {
                    TextButton(onClick = vm::closeDialog) { Text("Close", color = DyroxColors.TextSecondary) }
                    AccentButton("Try again", onClick = vm::startMicrosoftLogin)
                }
                return@ModalDialog
            }
        }
        if (busy) {
            DialogActions { TextButton(onClick = vm::closeDialog) { Text("Cancel", color = DyroxColors.TextSecondary) } }
        }
    }
}

@Composable
private fun ChooseMethod(vm: AccountsViewModel, onOpenSettings: () -> Unit) {
    if (!vm.microsoftLoginAvailable) {
        Text(
            "Microsoft sign-in needs an Azure app ID that Mojang has approved for Minecraft. Add yours in Settings.",
            color = DyroxColors.TextSecondary,
            fontSize = 13.sp,
        )
        DialogActions {
            TextButton(onClick = vm::closeDialog) { Text("Close", color = DyroxColors.TextSecondary) }
            AccentButton("Open Settings", onClick = {
                vm.closeDialog()
                onOpenSettings()
            })
        }
        return
    }
    Text("How do you want to sign in?", color = DyroxColors.TextSecondary, fontSize = 13.sp)
    MethodOption("Browser", "Opens Microsoft's sign-in page in your default browser. Recommended.", vm::loginWithBrowser)
    MethodOption("Device code", "Enter a short code on microsoft.com/link, on this or any other device.", vm::loginWithDeviceCode)
    DialogActions { TextButton(onClick = vm::closeDialog) { Text("Cancel", color = DyroxColors.TextSecondary) } }
}

@Composable
private fun MethodOption(title: String, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DyroxColors.SurfaceElevated)
            .border(1.dp, DyroxColors.Border, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(description, color = DyroxColors.TextSecondary, fontSize = 12.sp)
        }
        AccentButton("Continue", onClick = onClick)
    }
}

@Composable
private fun DeviceCode(state: MicrosoftLoginState.WaitingForDeviceCode) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remaining = ((state.prompt.expiresAt - now) / 1000).coerceAtLeast(0)
    Text("1. Open ${state.prompt.verificationUri}\n2. Enter this code:", color = DyroxColors.TextSecondary, fontSize = 13.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            state.prompt.userCode,
            color = DyroxColors.Accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            letterSpacing = 3.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { DesktopActions.copyToClipboard(state.prompt.userCode) }) { Text("Copy", color = DyroxColors.TextSecondary) }
        AccentButton("Copy & open page", onClick = {
            DesktopActions.copyToClipboard(state.prompt.userCode)
            DesktopActions.openUrl(state.prompt.verificationUri)
        })
    }
    Waiting("Waiting for you to finish signing in… code expires in ${remaining / 60}:${"%02d".format(remaining % 60)}")
}

@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), color = DyroxColors.Accent, strokeWidth = 2.dp)
        Text(text, color = DyroxColors.TextSecondary, fontSize = 13.sp)
    }
}
