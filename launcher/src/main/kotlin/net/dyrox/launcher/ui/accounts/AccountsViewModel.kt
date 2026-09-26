package net.dyrox.launcher.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.ui.platform.DesktopActions
import net.dyrox.shared.account.AccountStatus
import net.dyrox.shared.account.StoredAccount
import net.dyrox.shared.account.VaultContents
import net.dyrox.shared.auth.DeviceCodePrompt
import net.dyrox.shared.auth.MicrosoftLoginResult
import java.nio.file.Files

/** What the Microsoft sign-in dialog is showing. */
sealed interface MicrosoftLoginState {
    data object ChooseMethod : MicrosoftLoginState
    data object StartingDeviceCode : MicrosoftLoginState
    data class WaitingForDeviceCode(val prompt: DeviceCodePrompt) : MicrosoftLoginState
    data class WaitingForBrowser(val url: String?) : MicrosoftLoginState
    data class Failed(val message: String) : MicrosoftLoginState
}

sealed interface AccountDialog {
    data object AddOffline : AccountDialog
    data class Rename(val account: StoredAccount) : AccountDialog
    data class ConfirmRemove(val account: StoredAccount) : AccountDialog
    data class MicrosoftLogin(val state: MicrosoftLoginState) : AccountDialog
}

data class Banner(val text: String, val isError: Boolean = false)

/** Shared by the Accounts screen, the account dialogs and the Play screen's account picker. */
class AccountsViewModel(val core: LauncherCore, private val scope: CoroutineScope) {
    var contents by mutableStateOf(VaultContents()); private set
    /** Set when the vault can't be opened; the screen offers recovery. */
    var vaultError by mutableStateOf<String?>(null); private set
    var dialog by mutableStateOf<AccountDialog?>(null); private set
    var banner by mutableStateOf<Banner?>(null); private set

    /** Results of live checks, overriding the stored-data status until the account changes. */
    private val liveStatus = mutableStateMapOf<String, AccountStatus>()
    private val checking = mutableStateMapOf<String, Boolean>()
    private var loginJob: Job? = null
    private var bannerJob: Job? = null

    val accounts: List<StoredAccount> get() = contents.accounts
    val selected: StoredAccount? get() = contents.selected
    val microsoftLoginAvailable: Boolean get() = core.settings.effectiveClientId != null

    init {
        scope.launch { core.accounts.contents.collect { contents = it } }
        reload()
    }

    fun reload() {
        scope.launch {
            try {
                core.accounts.load()
                vaultError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                vaultError = e.message ?: e.toString()
            }
        }
    }

    fun status(account: StoredAccount): AccountStatus = liveStatus[account.id] ?: core.accounts.status(account)
    fun isChecking(account: StoredAccount): Boolean = checking[account.id] == true

    fun openDialog(dialog: AccountDialog) {
        this.dialog = dialog
    }

    fun closeDialog() {
        loginJob?.cancel()
        loginJob = null
        dialog = null
    }

    fun select(account: StoredAccount) = action { core.accounts.select(account.id) }

    fun addOffline(name: String) = action {
        val account = core.accounts.addOffline(name.trim())
        dialog = null
        show("Added offline account ${account.username}")
    }

    fun rename(account: StoredAccount, name: String) = action {
        core.accounts.renameOffline(account.id, name.trim())
        dialog = null
    }

    fun remove(account: StoredAccount) = action {
        core.accounts.remove(account.id)
        liveStatus.remove(account.id)
        dialog = null
        show("Removed ${account.username}")
    }

    fun check(account: StoredAccount) {
        if (checking[account.id] == true) return
        checking[account.id] = true
        scope.launch {
            try {
                liveStatus[account.id] = core.accounts.check(account.id)
            } finally {
                checking.remove(account.id)
            }
        }
    }

    fun checkAll() = accounts.forEach(::check)

    fun startMicrosoftLogin() {
        dialog = AccountDialog.MicrosoftLogin(MicrosoftLoginState.ChooseMethod)
    }

    fun loginWithDeviceCode() = runLogin(MicrosoftLoginState.StartingDeviceCode) { authenticator ->
        authenticator.loginWithDeviceCode { prompt ->
            scope.launch { setLoginState(MicrosoftLoginState.WaitingForDeviceCode(prompt)) }
        }
    }

    fun loginWithBrowser() = runLogin(MicrosoftLoginState.WaitingForBrowser(null)) { authenticator ->
        authenticator.loginWithBrowser(openBrowser = { url ->
            scope.launch { setLoginState(MicrosoftLoginState.WaitingForBrowser(url)) }
            DesktopActions.openUrl(url)
        })
    }

    fun exportAccounts() {
        val target = DesktopActions.chooseSaveFile("Export accounts", "dyrox-accounts.json") ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { Files.writeString(target, core.accounts.exportJson()) }
                show("Exported ${accounts.size} account(s). Tokens are never exported.")
            } catch (e: Exception) {
                show("Export failed: ${e.message}", isError = true)
            }
        }
    }

    fun importAccounts() {
        val source = DesktopActions.chooseOpenFile("Import accounts") ?: return
        action {
            val json = withContext(Dispatchers.IO) { Files.readString(source) }
            val result = core.accounts.importJson(json)
            show("Imported ${result.added} account(s)" + if (result.skipped > 0) ", skipped ${result.skipped} already present" else "")
        }
    }

    /** Recovery for an unreadable vault: keeps the old file as a backup and starts empty. */
    fun startFreshVault() = action {
        val backup = core.accounts.moveVaultAside()
        vaultError = null
        show("Started a new account vault" + (backup?.let { ". The old one was kept as ${it.fileName}" } ?: ""))
    }

    private fun runLogin(initial: MicrosoftLoginState, login: suspend (net.dyrox.shared.auth.MicrosoftAuthenticator) -> MicrosoftLoginResult) {
        val authenticator = core.microsoftAuthenticator() ?: run {
            setLoginState(MicrosoftLoginState.Failed("Set an Azure client ID in Settings first."))
            return
        }
        loginJob?.cancel()
        setLoginState(initial)
        loginJob = scope.launch {
            try {
                val result = login(authenticator)
                val account = core.accounts.addMicrosoft(result)
                liveStatus.remove(account.id)
                dialog = null
                show("Signed in as ${account.username}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoginState(MicrosoftLoginState.Failed(e.message ?: e.toString()))
            }
        }
    }

    private fun setLoginState(state: MicrosoftLoginState) {
        if (dialog is AccountDialog.MicrosoftLogin) dialog = AccountDialog.MicrosoftLogin(state)
    }

    private fun action(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(e.message ?: e.toString(), isError = true)
            }
        }
    }

    fun show(text: String, isError: Boolean = false) {
        banner = Banner(text, isError)
        bannerJob?.cancel()
        bannerJob = scope.launch {
            delay(if (isError) 8000 else 4000)
            banner = null
        }
    }
}
