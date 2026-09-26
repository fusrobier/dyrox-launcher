package net.dyrox.client

import net.dyrox.client.command.BindCommand
import net.dyrox.client.command.BindsCommand
import net.dyrox.client.command.CommandManager
import net.dyrox.client.command.ConfigCommand
import net.dyrox.client.command.HelpCommand
import net.dyrox.client.command.ModulesCommand
import net.dyrox.client.command.PrefixCommand
import net.dyrox.client.command.ToggleCommand
import net.dyrox.client.config.ConfigSystem
import net.dyrox.client.event.ChatSendEvent
import net.dyrox.client.event.ClientShutdownEvent
import net.dyrox.client.event.Events
import net.dyrox.client.event.GameTickEvent
import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.Render2DEvent
import net.dyrox.client.event.WorldChangeEvent
import net.dyrox.client.event.WorldRenderEvent
import net.dyrox.client.input.MinecraftKeys
import net.dyrox.client.integration.LauncherBridge
import net.dyrox.client.module.ModuleManager
import net.dyrox.client.combat.Friends
import net.dyrox.client.command.FriendCommand
import net.dyrox.client.command.SetCommand
import net.dyrox.client.module.modules.combat.*
import net.dyrox.client.module.modules.exploit.*
import net.dyrox.client.module.modules.`fun`.*
import net.dyrox.client.module.modules.misc.*
import net.dyrox.client.module.modules.movement.*
import net.dyrox.client.module.modules.player.*
import net.dyrox.client.module.modules.render.*
import net.dyrox.client.module.modules.world.*
import net.dyrox.client.render.CameraController
import net.dyrox.client.rotation.RotationManager
import net.dyrox.client.ui.clickgui.ClickGuiLayout
import net.dyrox.client.altmanager.AltManagerScreen
import net.dyrox.client.util.ChatOutput
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object DyroxClient : ClientModInitializer {
    const val MOD_ID = "dyrox"
    private val logger = LoggerFactory.getLogger("Dyrox")

    val version: String by lazy {
        FabricLoader.getInstance().getModContainer(MOD_ID).map { it.metadata.version.friendlyString }.orElse("dev")
    }

    val modules = ModuleManager()
    lateinit var config: ConfigSystem
        private set
    val commands = CommandManager()

    override fun onInitializeClient() {
        val started = System.nanoTime()
        registerModules()

        config = ConfigSystem(
            dir = FabricLoader.getInstance().configDir.resolve(MOD_ID),
            sections = { mapOf("modules" to modules.modules, "gui" to ClickGuiLayout.states.values.toList()) },
        )
        // The launcher's per-instance profile wins; otherwise continue with the last one used.
        val profile = LauncherBridge.profile?.takeIf(ConfigSystem::isValidName) ?: config.activeProfile
        config.load(profile)
        config.trackChanges()
        Friends.load(FabricLoader.getInstance().configDir.resolve(MOD_ID).resolve("friends.json"))
        runCatching { commands.prefix = config.state.commandPrefix }

        registerCommands()
        hookFabricEvents()
        LauncherBridge.connect(version)
        AltManagerScreen.registerButton()
        DebugHooks.install()

        logger.info(
            "Dyrox Client {} ready: {} modules, {} commands, profile '{}' ({} ms)",
            version, modules.modules.size, commands.commands.size, config.activeProfile, (System.nanoTime() - started) / 1_000_000,
        )
    }

    private fun registerModules() {
        modules.register(
            // Combat
            KillAura, TriggerBot, Velocity, Criticals, AutoTotem,
            // Movement
            Sprint, Fly, Speed, Step, NoSlow, SafeWalk, AutoWalk, InventoryMove, Spider,
            // Player
            NoFall, FastPlace, AutoTool, ChestStealer, AutoRespawn, AutoEat,
            // Render
            ClickGui, Hud, Esp, Tracers, StorageEsp, OreEsp, Fullbright, NoHurtCam, Zoom, CameraClip,
            // World
            Scaffold, Nuker, FastBreak, Timer,
            // Exploit, Misc, Fun
            Blink, MiddleClickFriend, AutoReconnect, AntiAim, Twerk, SkinDerp,
        )
    }

    private fun registerCommands() {
        commands.register(
            HelpCommand(commands),
            ToggleCommand(modules),
            BindCommand(modules, MinecraftKeys),
            BindsCommand(modules),
            ModulesCommand(modules),
            ConfigCommand(config),
            PrefixCommand(commands, config),
            FriendCommand(),
            SetCommand(modules),
        )
    }

    private fun hookFabricEvents() {
        ClientTickEvents.START_CLIENT_TICK.register {
            Events.post(GameTickEvent)
            CameraController.tick()
        }
        ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
            if (minecraft.player != null) Events.post(PlayerTickEvent)
            config.saveIfDirty()
        }

        // Chat: our commands never reach the server; other messages can be vetoed by modules.
        ClientSendMessageEvents.ALLOW_CHAT.register { message ->
            if (commands.isCommand(message)) {
                Minecraft.getInstance().gui.hud.chat.addRecentChat(message)
                commands.execute(message, ChatOutput)
                false
            } else {
                !Events.post(ChatSendEvent(message)).isCancelled
            }
        }

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud")) { graphics, delta ->
            Events.post(Render2DEvent(graphics, delta))
        }
        LevelRenderEvents.END_MAIN.register { context -> Events.post(WorldRenderEvent(context)) }

        ClientPlayConnectionEvents.JOIN.register { _, _, minecraft ->
            RotationManager.reset()
            Events.post(WorldChangeEvent(joined = true))
            LauncherBridge.status("in_world", minecraft.currentServer?.ip ?: "singleplayer")
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            Events.post(WorldChangeEvent(joined = false))
            LauncherBridge.status("menu")
        }
        ClientLifecycleEvents.CLIENT_STARTED.register { LauncherBridge.status("title_screen") }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            Events.post(ClientShutdownEvent)
            runCatching { config.save() }.onFailure { logger.error("Could not save config on exit", it) }
        }
    }
}
