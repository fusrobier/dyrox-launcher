package net.dyrox.client.altmanager

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.runBlocking
import net.dyrox.client.integration.LauncherBridge
import net.dyrox.client.module.modules.render.ClickGui
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
import net.dyrox.client.render.Glass
import net.dyrox.shared.account.AccountType
import net.dyrox.shared.auth.OfflineProfiles
import net.dyrox.shared.auth.toUndashedString
import net.dyrox.shared.ipc.IpcAccount
import net.dyrox.shared.ipc.IpcSession
import net.dyrox.shared.theme.DyroxPalette
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * In-game account switcher, opened from the multiplayer screen. With Dyrox Launcher, it lists the
 * launcher's accounts and asks the launcher for a fresh session (the launcher refreshes tokens).
 * Without it, only offline names can be used.
 */
class AltManagerScreen(private val parent: Screen) : Screen(Component.literal("Alt Manager")) {
    @Volatile private var accounts: List<IpcAccount> = emptyList()
    @Volatile private var status: Pair<String, Boolean>? = null // message, isError
    @Volatile private var busy = false
    private var offlineName = ""
    private var nameFocused = false

    private val cardWidth = 280
    private val rowHeight = 22
    private val cardX get() = (width - cardWidth) / 2
    private val cardY get() = 30
    private val listTop get() = cardY + 44
    private val listBottom get() = listTop + (accounts.size.coerceAtLeast(1)) * rowHeight
    private val offlineY get() = listBottom + 14

    override fun init() {
        refresh()
    }

    private fun refresh() {
        val client = LauncherBridge.client ?: run {
            status = "Not started by Dyrox Launcher: only offline names are available." to false
            return
        }
        background("Loading accounts…") {
            accounts = runBlocking { client.requestAccounts() }
            null
        }
    }

    /** Runs [work] off the render thread; its return value (if any) becomes the status message. */
    private fun background(message: String, work: () -> String?) {
        if (busy) return
        busy = true
        status = message to false
        Thread({
            val result = runCatching(work)
            minecraft.execute {
                busy = false
                status = result.fold({ it?.let { text -> text to false } }, { (it.message ?: it.javaClass.simpleName) to true })
            }
        }, "Dyrox alt manager").apply { isDaemon = true }.start()
    }

    private fun switchTo(account: IpcAccount) {
        val client = LauncherBridge.client ?: return
        background("Signing in as ${account.username}…") {
            val session = runBlocking { client.requestSession(account.id) }
            minecraft.execute { SessionSwapper.swap(session) }
            "Now playing as ${session.username}"
        }
    }

    /** Used by DebugHooks: switch to the launcher account named [name]. */
    internal fun switchByName(name: String) {
        accounts.firstOrNull { it.username.equals(name, ignoreCase = true) }?.let(::switchTo)
    }

    private fun useOffline() {
        val name = offlineName.trim()
        if (!OfflineProfiles.isValidName(name)) {
            status = "Names are 3–16 letters, digits or _" to true
            return
        }
        val uuid = OfflineProfiles.uuidFor(name).toUndashedString()
        SessionSwapper.swap(IpcSession("offline:$name", name, uuid, "0", null, AccountType.OFFLINE))
        status = "Now playing as $name (offline)" to false
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        // Glass needs something to frost: the world in-game, the menu panorama on the title screen.
        if (minecraft.level == null) extractPanorama(graphics, a)
        if (ClickGui.blur) extractBlurredBackground(graphics)
        Draw.rect(graphics, 0, 0, width, height, Glass.BACKDROP)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val accent = ClickGui.accent
        val cardHeight = offlineY + 50 - cardY
        Draw.glass(graphics, cardX, cardY, cardWidth, cardHeight, 14)
        Draw.text(graphics, "Alt Manager", cardX + 14, cardY + 10, Glass.TEXT, bold = true)
        Draw.text(graphics, "Playing as ${minecraft.user.name}", cardX + 14, cardY + 22, Glass.TEXT_DIM)
        val source = if (LauncherBridge.isConnected) "via Dyrox Launcher" else "launcher not connected"
        Draw.text(graphics, source, cardX + cardWidth - 14 - Draw.width(source), cardY + 10, if (LauncherBridge.isConnected) accent else DyroxPalette.WARNING)
        Draw.rect(graphics, cardX + 14, cardY + 36, cardWidth - 28, 1, Glass.DIVIDER)

        if (accounts.isEmpty()) {
            Draw.text(graphics, if (LauncherBridge.isConnected) "No accounts in the launcher" else "—", cardX + 16, listTop + 7, Glass.TEXT_MUTED)
        }
        accounts.forEachIndexed { index, account ->
            val rowY = listTop + index * rowHeight
            val current = account.uuid.equals(minecraft.user.profileId.toString().replace("-", ""), ignoreCase = true)
            val hovered = !account.inUse && mouseX in cardX + 8 until cardX + cardWidth - 8 && mouseY in rowY until rowY + rowHeight - 2
            if (current) {
                Draw.roundedRect(graphics, cardX + 8, rowY, cardWidth - 16, rowHeight - 2, 10, Colors.withAlpha(accent, 64))
                Draw.roundedRing(graphics, cardX + 8, rowY, cardWidth - 16, rowHeight - 2, 10, Colors.withAlpha(accent, 150))
            } else if (hovered) {
                Draw.roundedRect(graphics, cardX + 8, rowY, cardWidth - 16, rowHeight - 2, 10, Glass.HOVER)
            }
            Draw.circle(graphics, cardX + 20, rowY + 10, 7, Glass.BODY_STRONG)
            Draw.centeredText(graphics, account.username.take(1).uppercase(), cardX + 20, rowY + 6, Glass.TEXT, bold = true)
            Draw.text(graphics, account.username, cardX + 34, rowY + 6, if (account.inUse) Glass.TEXT_MUTED else Glass.TEXT, bold = current)
            val tag = when {
                current -> "current"
                account.inUse -> "playing elsewhere"
                else -> account.type.label
            }
            Draw.text(graphics, tag, cardX + cardWidth - 18 - Draw.width(tag), rowY + 6, if (account.type == AccountType.MICROSOFT) accent else Glass.TEXT_DIM)
        }

        // Offline name login (works without the launcher).
        Draw.text(graphics, "Offline name", cardX + 14, offlineY, Glass.TEXT_DIM)
        val fieldWidth = cardWidth - 108
        Draw.roundedRect(graphics, cardX + 12, offlineY + 11, fieldWidth, 18, 9, if (nameFocused) Glass.BODY_STRONG else Glass.HOVER)
        Draw.roundedRing(graphics, cardX + 12, offlineY + 11, fieldWidth, 18, 9, if (nameFocused) accent else Glass.DIVIDER)
        val caret = if (nameFocused && System.currentTimeMillis() / 500 % 2 == 0L) "|" else ""
        Draw.text(graphics, offlineName + caret, cardX + 20, offlineY + 16, Glass.TEXT)
        val buttonHovered = mouseX in cardX + cardWidth - 86 until cardX + cardWidth - 12 && mouseY in offlineY + 11 until offlineY + 29
        Draw.roundedRect(graphics, cardX + cardWidth - 86, offlineY + 11, 74, 18, 9, if (buttonHovered) Colors.lerp(accent, 0xFFFFFFFF.toInt(), 0.15f) else accent)
        Draw.centeredText(graphics, "Use offline", cardX + cardWidth - 49, offlineY + 16, DyroxPalette.ON_ACCENT, bold = true)

        status?.let { (text, error) ->
            Draw.centeredText(graphics, Draw.ellipsize(text, cardWidth - 20), width / 2, cardY + cardHeight + 8, if (error) DyroxPalette.DANGER else Glass.TEXT_DIM)
        }
        Draw.centeredText(graphics, "Esc to go back", width / 2, height - 14, Glass.TEXT_MUTED)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (event.buttonInfo().button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        nameFocused = mx in cardX + 12 until cardX + cardWidth - 92 && my in offlineY + 11 until offlineY + 29
        if (mx in cardX + cardWidth - 86 until cardX + cardWidth - 12 && my in offlineY + 11 until offlineY + 29) {
            useOffline()
            return true
        }
        if (!busy && mx in cardX + 8 until cardX + cardWidth - 8 && my in listTop until listTop + accounts.size * rowHeight) {
            accounts.getOrNull((my - listTop) / rowHeight)?.takeIf { !it.inUse }?.let(::switchTo)
            return true
        }
        return nameFocused
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (nameFocused) {
            val key = InputConstants.getKey(event).name
            when {
                key == "key.keyboard.backspace" -> offlineName = offlineName.dropLast(1)
                event.isConfirmation -> useOffline()
                event.isEscape -> nameFocused = false
                else -> return super.keyPressed(event)
            }
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (!nameFocused) return false
        val text = event.codepointAsString()
        if (text.all { it.isLetterOrDigit() || it == '_' } && offlineName.length < 16) offlineName += text
        return true
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    companion object {
        /** Adds the "Alt Manager" button to the multiplayer screen. */
        fun registerButton() {
            ScreenEvents.AFTER_INIT.register { minecraft, screen, _, _ ->
                if (screen is JoinMultiplayerScreen) {
                    Screens.getWidgets(screen).add(
                        Button.builder(Component.literal("Alt Manager")) { minecraft.gui.setScreen(AltManagerScreen(screen)) }
                            .bounds(6, 6, 80, 20)
                            .build(),
                    )
                }
            }
        }
    }
}
