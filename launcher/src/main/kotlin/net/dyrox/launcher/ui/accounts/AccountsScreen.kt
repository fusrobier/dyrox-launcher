package net.dyrox.launcher.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.AccountAvatar
import net.dyrox.launcher.ui.components.AccountStatusIndicator
import net.dyrox.launcher.ui.components.AccountTypeTag
import net.dyrox.launcher.ui.components.Panel
import net.dyrox.launcher.ui.components.relativeTime
import net.dyrox.launcher.ui.theme.DyroxColors
import net.dyrox.shared.account.AccountStatus
import net.dyrox.shared.account.AccountType
import net.dyrox.shared.account.StoredAccount

@Composable
fun AccountsScreen(vm: AccountsViewModel, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Accounts", color = DyroxColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Encrypted on this PC · ${vm.core.accounts.keyProtection}",
                    color = DyroxColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            SecondaryButton("Import", vm::importAccounts)
            SecondaryButton("Export", vm::exportAccounts, enabled = vm.accounts.isNotEmpty())
            SecondaryButton("Check all", vm::checkAll, enabled = vm.accounts.isNotEmpty())
            SecondaryButton("+ Offline", { vm.openDialog(AccountDialog.AddOffline) })
            AccentButton("+ Microsoft", vm::startMicrosoftLogin)
        }

        BannerView(vm.banner)

        if (!vm.microsoftLoginAvailable) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Microsoft sign-in needs an Azure app ID approved by Mojang. Until then you can use offline accounts.",
                        color = DyroxColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSettings) { Text("Set up in Settings", color = DyroxColors.Accent) }
                }
            }
        }

        val vaultError = vm.vaultError
        when {
            vaultError != null -> Panel(Modifier.fillMaxWidth(), title = "The account vault can't be opened") {
                Text(vaultError, color = DyroxColors.Danger, fontSize = 13.sp)
                Text(
                    "Your accounts are still in accounts.vault. Starting fresh keeps that file as a backup and creates a new, empty vault.",
                    color = DyroxColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Try again", vm::reload)
                    SecondaryButton("Start fresh (keep backup)", vm::startFreshVault)
                }
            }
            vm.accounts.isEmpty() -> Panel(Modifier.fillMaxWidth()) {
                Text("No accounts yet", color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Add a Microsoft account to play online, or an offline account for singleplayer and offline-mode servers.",
                    color = DyroxColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
            else -> {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(vm.accounts, key = { it.id }) { account -> AccountCard(vm, account) }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun AccountCard(vm: AccountsViewModel, account: StoredAccount) {
    val selected = account.id == vm.contents.selectedAccountId
    val border by animateColorAsState(if (selected) DyroxColors.Accent else DyroxColors.Border, tween(200))
    val status = vm.status(account)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DyroxColors.Surface)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountAvatar(account, vm.core.skins, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(account.username, color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                AccountTypeTag(account.type)
                if (selected) Text("● In use", color = DyroxColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            AccountStatusIndicator(status, vm.isChecking(account))
            Text("Last used ${relativeTime(account.lastUsedAt)}", color = DyroxColors.TextMuted, fontSize = 11.sp)
        }
        if (!selected) TextButton(onClick = { vm.select(account) }) { Text("Use", color = DyroxColors.Accent) }
        if (account.type == AccountType.MICROSOFT) {
            if (status is AccountStatus.SignInRequired) {
                TextButton(onClick = vm::startMicrosoftLogin) { Text("Sign in", color = DyroxColors.Accent) }
            } else {
                TextButton(onClick = { vm.check(account) }, enabled = !vm.isChecking(account)) { Text("Check", color = DyroxColors.TextSecondary) }
            }
        } else {
            TextButton(onClick = { vm.openDialog(AccountDialog.Rename(account)) }) { Text("Rename", color = DyroxColors.TextSecondary) }
        }
        TextButton(onClick = { vm.openDialog(AccountDialog.ConfirmRemove(account)) }) { Text("Remove", color = DyroxColors.Danger) }
    }
}

@Composable
private fun BannerView(banner: Banner?) {
    AnimatedVisibility(banner != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val current = banner ?: return@AnimatedVisibility
        val color = if (current.isError) DyroxColors.Danger else DyroxColors.Accent
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.12f))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(current.text, color = if (current.isError) DyroxColors.Danger else DyroxColors.TextPrimary, fontSize = 13.sp)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DyroxColors.Border),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = DyroxColors.TextPrimary,
            disabledContentColor = DyroxColors.TextMuted,
            containerColor = Color.Transparent,
        ),
    ) {
        Text(text, fontSize = 13.sp)
    }
}
