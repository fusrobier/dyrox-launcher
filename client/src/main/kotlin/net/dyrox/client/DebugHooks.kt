package net.dyrox.client

import net.dyrox.client.altmanager.AltManagerScreen
import net.dyrox.client.module.modules.render.ClickGui
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.GameType
import net.minecraft.world.level.levelgen.presets.WorldPresets
import org.slf4j.LoggerFactory

/**
 * Developer aids for UI testing and screenshots, inert unless a `-Ddyrox.debug.*` property is set:
 * - `dyrox.debug.world=true`: create (or reopen) a creative "Dyrox Test" world from the title screen
 * - `dyrox.debug.screen=clickgui|altmanager`: open that screen (on the title screen, or once in the world)
 * - `dyrox.debug.expand=HUD,ClickGUI`: modules whose settings start expanded in the ClickGUI
 * - `dyrox.debug.toggle=Sprint`: toggle a module a few seconds after joining the world (HUD/toast check)
 * - `dyrox.debug.switch=Alex`: with the alt manager open, switch to that launcher account (session swap check)
 * A framebuffer screenshot (`screenshots/dyrox-debug.png`) is taken a few seconds after the scene is ready.
 */
object DebugHooks {
    private val logger = LoggerFactory.getLogger("Dyrox/Debug")
    private const val WORLD_NAME = "Dyrox Test"

    val expandedModules: Set<String> =
        System.getProperty("dyrox.debug.expand").orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun install() {
        val screen = System.getProperty("dyrox.debug.screen")
        val world = System.getProperty("dyrox.debug.world") == "true"
        if (screen == null && !world) return
        logger.warn("Debug hooks active (dyrox.debug.*): screen={}, world={}", screen, world)

        var titleHandled = false
        ScreenEvents.AFTER_INIT.register { minecraft, current, _, _ ->
            if (titleHandled || current !is TitleScreen) return@register
            titleHandled = true
            if (world) openTestWorld(minecraft, current) else openScreen(minecraft, screen, current, afterMillis = 0)
        }
        if (world) {
            var joinedOnce = false
            ClientPlayConnectionEvents.JOIN.register { _, _, minecraft ->
                if (joinedOnce) return@register
                joinedOnce = true
                // Let chunks render before opening a screen over them.
                openScreen(minecraft, screen, parent = null, afterMillis = 5_000)
                // Toggle a module shortly before the screenshot, so the array list and a toast show up.
                System.getProperty("dyrox.debug.toggle")?.let { name -> later(8_000) { DyroxClient.modules[name]?.toggle() } }
            }
        }
    }

    private fun openScreen(minecraft: Minecraft, screen: String?, parent: net.minecraft.client.gui.screens.Screen?, afterMillis: Long) {
        later(afterMillis) {
            when (screen) {
                "clickgui" -> ClickGui.trigger()
                "altmanager" -> {
                    minecraft.gui.setScreen(AltManagerScreen(parent ?: TitleScreen()))
                    System.getProperty("dyrox.debug.switch")?.let { name ->
                        later(1_500) { (minecraft.gui.screen() as? AltManagerScreen)?.switchByName(name) }
                    }
                }
            }
            later(4_000) {
                Screenshot.grab(minecraft.gameDirectory, "dyrox-debug.png", minecraft.gameRenderer.mainRenderTarget(), 1) {
                    logger.info("Debug screenshot: {}", it.string)
                }
            }
        }
    }

    private fun openTestWorld(minecraft: Minecraft, parent: TitleScreen) {
        minecraft.execute {
            val exists = runCatching { minecraft.levelSource.levelExists(WORLD_NAME) }.getOrDefault(false)
            if (exists) {
                minecraft.createWorldOpenFlows().openWorld(WORLD_NAME) { minecraft.gui.setScreen(parent) }
            } else {
                val settings = LevelSettings(WORLD_NAME, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT)
                minecraft.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, SelectWorldScreen.TEST_OPTIONS, WorldPresets::createNormalWorldDimensions, parent)
            }
        }
    }

    private fun later(millis: Long, action: () -> Unit) {
        val minecraft = Minecraft.getInstance()
        if (millis <= 0) {
            minecraft.execute(action)
            return
        }
        Thread({
            Thread.sleep(millis)
            minecraft.execute(action)
        }, "Dyrox debug").apply { isDaemon = true }.start()
    }
}
