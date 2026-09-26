package net.dyrox.client

import net.dyrox.client.combat.Friends
import net.dyrox.client.command.CommandException
import net.dyrox.client.command.CommandOutput
import net.dyrox.client.command.SetCommand
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.EventBus
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.ModuleManager
import net.dyrox.client.rotation.Rotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleLogicTest {
    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun `rotation between points uses minecraft angles`() {
        val eye = Vec3(0.0, 0.0, 0.0)
        // Yaw 0 looks south (+Z), 90 west (-X), -90 east (+X); pitch -90 is up.
        assertNear(0f, Rotation.between(eye, Vec3(0.0, 0.0, 5.0)).yaw)
        assertNear(90f, Rotation.between(eye, Vec3(-5.0, 0.0, 0.0)).yaw)
        assertNear(-90f, Rotation.between(eye, Vec3(5.0, 0.0, 0.0)).yaw)
        assertNear(-90f, Rotation.between(eye, Vec3(0.0, 5.0, 0.001)).pitch, 0.1f)
        assertNear(45f, Rotation.between(eye, Vec3(0.0, -1.0, 1.0)).pitch)
        // Consistent with vanilla's own direction vector.
        val r = Rotation.between(eye, Vec3(3.0, 2.0, -4.0))
        val back = Vec3.directionFromRotation(r.pitch, r.yaw)
        val expected = Vec3(3.0, 2.0, -4.0).normalize()
        assertTrue(back.distanceTo(expected) < 1.0E-3, "direction $back vs $expected")
    }

    @Test
    fun `turning is limited per tick and takes the short way round`() {
        val from = Rotation(170f, 0f)
        val step = from.stepTowards(Rotation(-170f, 0f), 10f)
        // -170 is 20° clockwise of 170, not 340° the other way; yaw stays continuous (no wrap jump).
        assertNear(180f, step.yaw)
        val done = step.stepTowards(Rotation(-170f, 0f), 10f)
        assertNear(190f, done.yaw)
        assertNear(0f, done.angleTo(Rotation(-170f, 0f)))
        assertNear(90f, Rotation(0f, 60f).stepTowards(Rotation(0f, 120f), 90f).pitch) // pitch clamps
    }

    @Test
    fun `mouse step rounding keeps rotations on the sensitivity grid`() {
        val from = Rotation(10f, 5f)
        val snapped = Rotation.applyMouseStep(from, Rotation(23.37f, -7.21f), 0.5)
        val f = 0.5 * 0.6 + 0.2
        val step = (f * f * f * 8.0 * 0.15).toFloat()
        val dYaw = (snapped.yaw - from.yaw) / step
        val dPitch = (snapped.pitch - from.pitch) / step
        assertNear(Math.round(dYaw).toFloat(), dYaw, 1.0E-3f)
        assertNear(Math.round(dPitch).toFloat(), dPitch, 1.0E-3f)
        assertTrue(snapped.angleTo(Rotation(23.37f, -7.21f)) < step)
    }

    @Test
    fun `closest point clamps into the box`() {
        val box = AABB(0.0, 0.0, 0.0, 1.0, 2.0, 1.0)
        assertEquals(Vec3(0.0, 1.5, 1.0), Rotation.closestPoint(Vec3(-3.0, 1.5, 4.0), box))
        assertEquals(Vec3(0.5, 1.0, 0.5), Rotation.closestPoint(Vec3(0.5, 1.0, 0.5), box))
    }

    @Test
    fun `friends persist case-insensitively`(@org.junit.jupiter.api.io.TempDir dir: java.nio.file.Path) {
        val file = dir.resolve("friends.json")
        Friends.load(file)
        assertTrue(Friends.add("Notch"))
        assertTrue(!Friends.add("notch"), "names are case-insensitive")
        Friends.add("jeb_")
        assertTrue(Friends.isFriend("NOTCH"))

        Friends.load(file) // fresh read from disk
        assertEquals(listOf("jeb_", "Notch"), Friends.all.sortedBy { it.lowercase() })
        assertTrue(Friends.remove("JEB_"))
        Friends.load(file)
        assertEquals(listOf("Notch"), Friends.all)

        java.nio.file.Files.writeString(file, "not json")
        Friends.load(file) // a broken file is ignored, not fatal
        assertEquals(emptyList(), Friends.all)
    }

    private enum class Mode(override val choiceName: String) : NamedChoice { SILENT("Silent"), LOCK("Lock view") }

    private inner class Aura : Module("KillAura", Category.COMBAT, "test") {
        val range by float("Range", 3f, 1f..6f, 0.1f)
        val turnSpeed by floatRange("Turn speed", 40f..60f, 5f..180f, 1f)
        val rotations by choice("Rotations", Mode.SILENT, Mode.entries)
        val targets by multiChoice("Targets", setOf(Mode.SILENT), Mode.entries)
        val swing by boolean("Swing", true)
    }

    private class Output : CommandOutput {
        val lines = ArrayList<String>()
        override fun info(message: String) {
            lines += message
        }

        override fun error(message: String) {
            lines += "ERR $message"
        }
    }

    @Test
    fun `set command changes settings by loose names`() {
        val aura = Aura()
        val modules = ModuleManager(EventBus()).apply { register(aura) }
        val command = SetCommand(modules)
        val out = Output()

        command.execute(listOf("killaura", "range", "4.2"), out)
        assertEquals(4.2f, aura.range)
        command.execute(listOf("killaura", "turnspeed", "80-120"), out)
        assertEquals(80f..120f, aura.turnSpeed)
        command.execute(listOf("killaura", "Rotations", "lockview"), out)
        assertEquals(Mode.LOCK, aura.rotations)
        command.execute(listOf("killaura", "targets", "lock", "view"), out)
        assertEquals(setOf(Mode.SILENT, Mode.LOCK), aura.targets)
        command.execute(listOf("killaura", "swing", "off"), out)
        assertEquals(false, aura.swing)
        command.execute(listOf("killaura", "range", "99"), out)
        assertEquals(6f, aura.range) // clamped by the setting

        command.execute(listOf("killaura", "range"), out)
        assertTrue(out.lines.last().endsWith("Range = 6.0"), out.lines.last())
        assertFailsWith<CommandException> { command.execute(listOf("killaura", "nope", "1"), out) }
        assertFailsWith<CommandException> { command.execute(listOf("killaura", "swing", "maybe"), out) }
        assertFailsWith<CommandException> { command.execute(listOf("killaura", "bind", "r"), out) }
    }
}
