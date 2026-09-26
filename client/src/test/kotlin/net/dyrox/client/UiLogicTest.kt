package net.dyrox.client

import net.dyrox.client.altmanager.SessionSwapper
import net.dyrox.client.config.ConfigSystem
import net.dyrox.client.event.EventBus
import net.dyrox.client.event.KeyEvent
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.ModuleManager
import net.dyrox.client.module.TriggerModule
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Easing
import net.dyrox.client.ui.clickgui.ClickGuiLayout
import net.dyrox.client.ui.clickgui.PanelState
import net.dyrox.client.ui.clickgui.Style
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiLogicTest {
    @Test
    fun `animations follow the clock and retarget from where they are`() {
        var now = 0L
        val anim = Animated(0f, durationMillis = 100f, easing = Easing::linear, clock = { now })
        anim.animateTo(10f)
        now = 50
        assertEquals(5f, anim.value)
        // Retargeting mid-way starts from the current value, not from the old start.
        anim.animateTo(0f)
        now = 100
        assertEquals(2.5f, anim.value)
        now = 150
        assertEquals(0f, anim.value)
        assertTrue(anim.isDone)
        anim.snapTo(7f)
        assertEquals(7f, anim.value)
    }

    @Test
    fun `easing curves start at 0 and end at 1`() {
        for (curve in listOf(Easing::linear, Easing::outCubic, Easing::inOutCubic, Easing::outBack)) {
            assertEquals(0f, curve(0f), 1e-4f)
            assertEquals(1f, curve(1f), 1e-4f)
        }
        assertTrue(Easing.outBack(0.7f) > 1f, "outBack overshoots")
    }

    @Test
    fun `colour helpers blend channels and alpha`() {
        assertEquals(0x80FFFFFF.toInt(), Colors.withAlpha(0xFFFFFFFF.toInt(), 0x80))
        assertEquals(0x40000000, Colors.fade(0x80000000.toInt(), 0.5f))
        assertEquals(0xFF000000.toInt(), Colors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), Colors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF7F7F7F.toInt(), Colors.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        assertEquals(0xFF, Colors.alpha(Colors.rainbow()))
    }

    @Test
    fun `text is vertically centred in rows`() {
        assertEquals(10 + (18 - 8) / 2, Style.textY(10, 18))
    }

    @Test
    fun `UUIDs parse with and without dashes`() {
        val uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
        assertEquals(uuid, SessionSwapper.parseUuid("069a79f444e94726a5befca90e38aaf5"))
        assertEquals(uuid, SessionSwapper.parseUuid(uuid.toString()))
        assertFailsWith<IllegalArgumentException> { SessionSwapper.parseUuid("nope") }
    }

    private class Menu : TriggerModule("Menu", Category.RENDER, "test", "key.keyboard.m") {
        var triggered = 0
        override fun trigger() {
            triggered++
        }
    }

    @Test
    fun `trigger modules run their action instead of toggling`() {
        val bus = EventBus()
        val modules = ModuleManager(bus)
        val menu = Menu()
        modules.register(menu)
        val toggles = ArrayList<Pair<Module, Boolean>>()
        val listener: (Module, Boolean) -> Unit = { m, on -> toggles += m to on }
        Module.toggleListeners += listener
        try {
            bus.post(KeyEvent("key.keyboard.m", KeyEvent.PRESS, screenOpen = false))
            assertEquals(1, menu.triggered)
            assertFalse(menu.enabled)
            // Enabling it (command, old config) runs the action once and leaves it off, without toggle events.
            menu.enabled = true
            assertEquals(2, menu.triggered)
            assertFalse(menu.enabled)
            assertTrue(toggles.none { it.first === menu }, "trigger modules must not produce toggle notifications")
        } finally {
            Module.toggleListeners -= listener
        }
    }

    @Test
    fun `panel layout is saved in the gui section`(@TempDir dir: Path) {
        val states = ClickGuiLayout.states.values.toList()
        assertEquals(Category.entries.size, states.size)
        val config = ConfigSystem(dir, { mapOf("gui" to states) })
        val combat: PanelState = ClickGuiLayout.states.getValue(Category.COMBAT)
        combat.x.value = 120
        combat.y.value = 44
        combat.expanded.value = false
        config.save("layout")

        combat.resetAll()
        assertEquals(-1, combat.x.value, "unplaced by default")
        config.load("layout")
        assertEquals(120, combat.x.value)
        assertEquals(44, combat.y.value)
        assertFalse(combat.expanded.value)
        assertTrue(config.isLoading.not())
    }
}
