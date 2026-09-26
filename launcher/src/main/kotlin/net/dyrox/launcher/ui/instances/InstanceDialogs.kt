package net.dyrox.launcher.ui.instances

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.core.instance.LoaderType
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.AccountAvatar
import net.dyrox.launcher.ui.components.AccountTypeTag
import net.dyrox.launcher.ui.components.DialogActions
import net.dyrox.launcher.ui.components.ModalDialog
import net.dyrox.launcher.ui.components.SegmentedToggle
import net.dyrox.launcher.ui.components.VersionList
import net.dyrox.launcher.ui.components.dyroxTextFieldColors
import net.dyrox.launcher.ui.theme.DyroxColors

@Composable
fun InstanceDialogs(vm: InstancesViewModel) {
    val dialog = vm.dialog
    val close = { vm.dialog = null }

    ModalDialog(visible = dialog is InstanceDialog.NewInstance, title = "New instance", onDismiss = close, width = 560.dp) {
        var name by remember { mutableStateOf("") }
        var version by remember { mutableStateOf(vm.catalog.latestRelease) }
        var loader by remember { mutableStateOf(LoaderType.FABRIC) }
        OutlinedTextField(
            name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
            shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        VersionList(vm.catalog, version, { version = it }, Modifier.fillMaxWidth().height(300.dp))
        SegmentedToggle(LoaderType.entries, loader, { it.label }, { loader = it }, Modifier.fillMaxWidth())
        val chosen = version
        val fabricOk = loader == LoaderType.VANILLA || chosen == null || vm.catalog.supportsFabric(chosen)
        if (!fabricOk) Text("Fabric doesn't support $chosen", color = DyroxColors.Warning, fontSize = 12.sp)
        DialogActions {
            TextButton(onClick = close) { Text("Cancel", color = DyroxColors.TextSecondary) }
            AccentButton(
                "Create",
                { if (chosen != null) vm.create(name.ifBlank { "$chosen ${loader.label}" }, chosen, loader) },
                enabled = chosen != null && fabricOk,
            )
        }
    }

    val choose = dialog as? InstanceDialog.ChooseVersion
    ModalDialog(visible = choose != null, title = "Minecraft version", onDismiss = close, width = 520.dp) {
        VersionList(vm.catalog, choose?.current, { id ->
            choose?.onChosen?.invoke(id)
            vm.dialog = null
        }, Modifier.fillMaxWidth().height(420.dp))
    }

    val delete = dialog as? InstanceDialog.ConfirmDelete
    ModalDialog(visible = delete != null, title = "Delete instance?", onDismiss = close) {
        val instance = delete?.instance
        Text(
            "\"${instance?.name.orEmpty()}\" and its worlds, settings and mods will be moved to the Recycle Bin." +
                if (instance?.gameDirectory != null) " Its custom game folder (${instance.gameDirectory}) is not touched." else "",
            color = DyroxColors.TextSecondary,
            fontSize = 13.sp,
        )
        DialogActions {
            TextButton(onClick = close) { Text("Cancel", color = DyroxColors.TextSecondary) }
            AccentButton("Delete", { instance?.let(vm::delete) }, danger = true)
        }
    }

    val multi = dialog as? InstanceDialog.MultiLaunch
    ModalDialog(visible = multi != null, title = "Launch with selected accounts", onDismiss = close, width = 520.dp) {
        val template = vm.instances.firstOrNull { it.id == multi?.templateId }
        val inUse = vm.sessions.values.filter { it.isActive }.associate { it.account.id to it.instance.name }
        val picked = remember(multi) { mutableStateListOf<String>() }
        Text(
            "Starts one game per account. The first uses \"${template?.name.orEmpty()}\"; the others use copies " +
                "(\"${template?.name.orEmpty()} #2\", …) with the same version and settings but their own game folders.",
            color = DyroxColors.TextSecondary,
            fontSize = 13.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            vm.accounts.accounts.forEach { account ->
                val busyIn = inUse[account.id]
                val checked = account.id in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = busyIn == null) { if (checked) picked.remove(account.id) else picked.add(account.id) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { if (it) picked.add(account.id) else picked.remove(account.id) },
                        enabled = busyIn == null,
                        colors = CheckboxDefaults.colors(checkedColor = DyroxColors.Accent, checkmarkColor = DyroxColors.OnAccent),
                    )
                    AccountAvatar(account, vm.core.skins, size = 28.dp)
                    Text(account.username, color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (busyIn != null) Text("playing in $busyIn", color = DyroxColors.TextMuted, fontSize = 11.sp)
                    AccountTypeTag(account.type)
                }
            }
        }
        DialogActions {
            TextButton(onClick = close) { Text("Cancel", color = DyroxColors.TextSecondary) }
            AccentButton(
                if (picked.isEmpty()) "Launch" else "Launch ${picked.size}",
                // Keep the order the accounts are listed in, so the template gets the first one.
                { template?.let { t -> vm.launchWithAccounts(t.id, vm.accounts.accounts.map { it.id }.filter { it in picked }) } },
                enabled = picked.isNotEmpty() && template != null,
            )
        }
    }
}
