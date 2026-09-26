package net.dyrox.client

import net.dyrox.client.event.GameTickEvent
import net.dyrox.client.event.Listenable
import net.dyrox.client.event.handler
import net.dyrox.client.rotation.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory

/**
 * Scripted in-game checks for development (`-Ddyrox.debug.script=...`), started once the debug world
 * is loaded. Steps are separated by `;`:
 *
 * - `wait:<ticks>`: pause
 * - `cmd:<command>`: a vanilla command without the slash (`cmd:summon zombie ~3 ~ ~`)
 * - `dyrox:<command>`: a Dyrox chat command without the prefix (`dyrox:t killaura on`)
 * - `key:<forward|back|left|right|jump|sneak|use|attack>=<0|1>`: hold or release a key
 * - `look:<yaw>,<pitch>`: turn the camera
 * - `slot:<0-8>`: select a hotbar slot
 * - `probe:<player|entities|rotation|modules|camera|inventory|block=x,y,z>`: write state to the log (tag "Dyrox/Debug")
 * - `shot:<name>`: framebuffer screenshot `screenshots/<name>.png`
 * - `quit`: close the game
 *
 * Inert unless the property is set.
 */
object DebugScript : Listenable {
    private val logger = LoggerFactory.getLogger("Dyrox/Debug")
    private val steps = ArrayDeque<String>()
    private var waitTicks = 0
    private var running = false

    fun start(script: String) {
        steps.clear()
        steps += script.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        running = true
        logger.info("Debug script: {} steps", steps.size)
    }

    @Suppress("unused")
    private val onTick = handler<GameTickEvent> {
        if (!running) return@handler
        if (waitTicks > 0) {
            waitTicks--
            return@handler
        }
        while (steps.isNotEmpty() && waitTicks == 0) {
            val step = steps.removeFirst()
            runCatching { run(step) }.onFailure { logger.error("Step '{}' failed", step, it) }
        }
        if (steps.isEmpty() && waitTicks == 0) {
            running = false
            logger.info("Debug script finished")
        }
    }

    private fun run(step: String) {
        val minecraft = Minecraft.getInstance()
        val (kind, arg) = step.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        logger.info("step {}", step)
        when (kind) {
            "wait" -> waitTicks = arg.toInt()
            "cmd" -> minecraft.player?.connection?.sendCommand(arg)
            "dyrox" -> DyroxClient.commands.execute(DyroxClient.commands.prefix + arg, net.dyrox.client.util.ChatOutput)
            "key" -> {
                val (name, state) = arg.split('=')
                val options = minecraft.options
                val key = when (name) {
                    "forward" -> options.keyUp
                    "back" -> options.keyDown
                    "left" -> options.keyLeft
                    "right" -> options.keyRight
                    "jump" -> options.keyJump
                    "sneak" -> options.keyShift
                    "use" -> options.keyUse
                    "attack" -> options.keyAttack
                    else -> error("Unknown key $name")
                }
                key.setDown(state == "1")
            }
            "slot" -> minecraft.player?.inventory?.selectedSlot = arg.toInt()
            "look" -> {
                val (yaw, pitch) = arg.split(',').map { it.trim().toFloat() }
                minecraft.player?.let {
                    it.yRot = yaw
                    it.xRot = pitch
                }
            }
            "probe" -> probe(arg)
            "shot" -> Screenshot.grab(minecraft.gameDirectory, "$arg.png", minecraft.gameRenderer.mainRenderTarget(), 1) {
                logger.info("Screenshot: {}", it.string)
            }
            "quit" -> minecraft.stop()
            else -> error("Unknown step kind '$kind'")
        }
    }

    private fun probe(what: String) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return logger.info("probe {}: no player", what)
        when (what) {
            "player" -> logger.info(
                "probe player: pos=%.2f/%.2f/%.2f rot=%.1f/%.1f motion=%.3f/%.3f/%.3f ground=%s health=%.1f food=%d fall=%.2f slot=%d item=%s mode=%s flying=%s step=%.2f offhand=%s screen=%s".format(
                    player.x, player.y, player.z, player.yRot, player.xRot,
                    player.deltaMovement.x, player.deltaMovement.y, player.deltaMovement.z,
                    player.onGround(), player.health, player.foodData.foodLevel, player.fallDistance,
                    player.inventory.selectedSlot, player.mainHandItem, minecraft.gameMode?.playerMode, player.abilities.flying,
                    player.maxUpStep(), player.offhandItem, minecraft.gui.screen()?.javaClass?.simpleName,
                ),
            )
            "entities" -> minecraft.level?.entitiesForRendering()?.filterIsInstance<LivingEntity>()?.filter { it !== player }?.forEach {
                logger.info("probe entity: {} alive={} health={} dist={}", it.type.description.string, it.isAlive, it.health, "%.2f".format(it.distanceTo(player)))
            }
            "rotation" -> logger.info("probe rotation: server={} camera={}/{}", RotationManager.serverRotation, player.yRot, player.xRot)
            "modules" -> logger.info("probe modules: {}", DyroxClient.modules.modules.filter { it.enabled }.joinToString { it.name })
            "killaura" -> logger.info("probe killaura: {}", net.dyrox.client.module.modules.combat.KillAura.debugState())
            "misc" -> logger.info(
                "probe misc: millis={} playerTicks={} sneaking={} fov={} rightClickDelay={} skinParts={}",
                System.currentTimeMillis(), player.tickCount, player.isShiftKeyDown, minecraft.gameRenderer.mainCamera().fov,
                (minecraft as net.dyrox.client.mixin.MinecraftAccessor).`dyrox$getRightClickDelay`(),
                net.minecraft.world.entity.player.PlayerModelPart.entries.count { minecraft.options.isModelPartEnabled(it) },
            )
            "inventory" -> logger.info(
                "probe inventory: {}",
                (0 until player.inventory.containerSize).map { player.inventory.getItem(it) }.filter { !it.isEmpty }.joinToString { "${it.count}x${it.item}" },
            )
            "camera" -> logger.info("probe camera: type={} detached={} pos={}", minecraft.options.cameraType, minecraft.gameRenderer.mainCamera().isDetached, minecraft.gameRenderer.mainCamera().position())
            else -> if (what.startsWith("block=")) {
                val (x, y, z) = what.removePrefix("block=").split(',').map { it.trim().toInt() }
                logger.info("probe block {} {} {}: {}", x, y, z, minecraft.level?.getBlockState(net.minecraft.core.BlockPos(x, y, z)))
            } else {
                logger.info("probe {}: unknown", what)
            }
        }
    }
}
