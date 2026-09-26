package net.dyrox.client

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.dyrox.client.command.BindCommand
import net.dyrox.client.command.CommandException
import net.dyrox.client.command.CommandManager
import net.dyrox.client.command.CommandOutput
import net.dyrox.client.command.ConfigCommand
import net.dyrox.client.command.HelpCommand
import net.dyrox.client.command.KeyResolver
import net.dyrox.client.command.ToggleCommand
import net.dyrox.client.config.BindMode
import net.dyrox.client.config.ChoiceValue
import net.dyrox.client.config.ConfigSystem
import net.dyrox.client.config.DyroxColor
import net.dyrox.client.config.FloatRangeValue
import net.dyrox.client.config.FloatValue
import net.dyrox.client.config.IntValue
import net.dyrox.client.config.KeyBind
import net.dyrox.client.config.Mode
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.CancellableEvent
import net.dyrox.client.event.Event
import net.dyrox.client.event.EventBus
import net.dyrox.client.event.KeyEvent
import net.dyrox.client.event.Listenable
import net.dyrox.client.event.handler
import net.dyrox.client.input.KeyNames
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.ModuleManager
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientCoreTest {
    private class Ping : CancellableEvent()
    private object Tick : Event

    private val bus = EventBus()

    enum class Shape(override val choiceName: String) : NamedChoice { CIRCLE("Circle"), SQUARE("Square") }

    /** A module on a private bus, with one setting of each common kind and two modes. */
    private inner class Fly : Module("Fly", Category.MOVEMENT, "test") {
        val speed = register(FloatValue("Speed", 1f, 0.1f..5f, 0.1f))
        val count = register(IntValue("Count", 3, 1..10))
        val shape = register(ChoiceValue("Shape", Shape.CIRCLE, Shape.entries))
        val delay = register(FloatRangeValue("Delay", 80f..120f, 0f..500f, 1f))
        var ticks = 0
        inner class Vanilla : Mode("Vanilla") {
            val boost = boolean("Boost", false)
        }

        inner class Glide : Mode("Glide") {
            var ticks = 0

            init {
                handler<Tick>(bus = bus) { ticks++ }
            }
        }

        val vanilla = Vanilla()
        val glide = Glide()
        val mode = modes(this, "Mode", listOf(vanilla, glide))

        init {
            handler<Tick>(bus = bus) { ticks++ }
        }
    }

    private class Output : CommandOutput {
        val lines = ArrayList<String>()
        override fun info(message: String) {
            lines += message
        }

        override fun error(message: String) {
            lines += "ERROR: $message"
        }
    }

    // --- Event bus ---

    @Test
    fun `handlers run by priority and can cancel`() {
        val order = ArrayList<String>()
        val owner = object : Listenable {}
        owner.handler<Ping>(priority = 0, bus = bus) { order += "normal"; it.cancel() }
        owner.handler<Ping>(priority = 10, bus = bus) { order += "high" }
        owner.handler<Ping>(priority = -5, bus = bus) { order += "low" }
        val event = bus.post(Ping())
        assertEquals(listOf("high", "normal", "low"), order)
        assertTrue(event.isCancelled)
    }

    @Test
    fun `disabled owners don't receive events, and a failing handler doesn't stop others`() {
        var enabled = false
        var received = 0
        val owner = object : Listenable {
            override fun handleEvents() = enabled
        }
        owner.handler<Tick>(bus = bus) { received++ }
        owner.handler<Tick>(bus = bus, ignoreCondition = true) { error("boom") }
        owner.handler<Tick>(bus = bus, ignoreCondition = true) { received += 10 }
        bus.post(Tick)
        assertEquals(10, received)
        enabled = true
        bus.post(Tick)
        assertEquals(21, received)
    }

    @Test
    fun `mode handlers only run when the mode is selected and the module enabled`() {
        val fly = Fly()
        bus.post(Tick)
        assertEquals(0, fly.ticks)
        fly.enabled = true
        fly.mode.value = fly.glide
        bus.post(Tick)
        assertEquals(1, fly.ticks)
        assertEquals(1, fly.glide.ticks)
        fly.mode.value = fly.vanilla
        bus.post(Tick)
        assertEquals(1, fly.glide.ticks)
    }

    // --- Settings ---

    @Test
    fun `numeric settings are clamped and snapped to their step`() {
        val fly = Fly()
        fly.speed.value = 9f
        assertEquals(5f, fly.speed.value)
        fly.speed.value = 1.26f
        assertEquals(1.3f, fly.speed.value)
        fly.count.value = -4
        assertEquals(1, fly.count.value)
        fly.delay.value = 300f..100f
        assertEquals(100f..300f, fly.delay.value)
    }

    @Test
    fun `settings round-trip through JSON, bad values keep the old value`() {
        val fly = Fly()
        fly.speed.value = 2.5f
        fly.shape.value = Shape.SQUARE
        fly.mode.value = fly.glide
        fly.vanilla.boost.value = true
        fly.bind.value = KeyBind("key.keyboard.f", BindMode.HOLD)
        val json = fly.toJson()

        val copy = Fly()
        val errors = ArrayList<String>()
        copy.fromJson(json, errors::add)
        assertTrue(errors.isEmpty(), errors.toString())
        assertEquals(2.5f, copy.speed.value)
        assertEquals(Shape.SQUARE, copy.shape.value)
        assertEquals(copy.glide, copy.mode.value)
        assertEquals(true, copy.vanilla.boost.value)
        assertEquals(KeyBind("key.keyboard.f", BindMode.HOLD), copy.bind.value)

        val broken = buildJsonObject {
            put("Speed", "fast")
            put("Shape", "Triangle")
            put("Count", 7)
            put("Unknown", 1)
        }
        copy.fromJson(broken, errors::add)
        assertEquals(2, errors.size)
        assertEquals(2.5f, copy.speed.value)
        assertEquals(7, copy.count.value)
    }

    @Test
    fun `colours parse hex with and without alpha`() {
        assertEquals(0xFFA3E635.toInt(), DyroxColor.parseHex("#A3E635"))
        assertEquals(0x80A3E635.toInt(), DyroxColor.parseHex("80A3E635"))
        assertFailsWith<IllegalArgumentException> { DyroxColor.parseHex("#123") }
    }

    // --- Config profiles ---

    @Test
    fun `profiles save, load and stay independent`(@TempDir dir: Path) {
        val fly = Fly()
        val config = ConfigSystem(dir, { mapOf("modules" to listOf(fly)) })
        fly.enabled = true
        fly.speed.value = 3f
        config.save("pvp")

        fly.speed.value = 0.5f
        config.saveAs("building")
        assertEquals("building", config.activeProfile)

        assertTrue(config.load("pvp").isEmpty())
        assertEquals(3f, fly.speed.value)
        assertTrue(fly.enabled)
        assertEquals(listOf("building", "pvp"), config.profileNames())

        // A profile that doesn't exist yet starts from defaults.
        config.load("fresh")
        assertEquals(1f, fly.speed.value)
        assertFalse(fly.enabled)
        assertEquals("fresh", ConfigSystem(dir, { emptyMap() }).state.lastProfile)
    }

    @Test
    fun `changes are autosaved after a debounce`(@TempDir dir: Path) {
        var now = 0L
        val fly = Fly()
        val config = ConfigSystem(dir, { mapOf("modules" to listOf(fly)) }, clock = { now })
        config.load("default")
        config.trackChanges()
        fly.speed.value = 4f
        config.saveIfDirty(debounceMillis = 2_000)
        assertFalse(Files.exists(dir.resolve("profiles/default.json")), "saved too early")
        now = 2_500
        config.saveIfDirty(debounceMillis = 2_000)
        assertTrue(Files.readString(dir.resolve("profiles/default.json")).contains("\"Speed\": 4.0"))
    }

    @Test
    fun `profile names are validated and the active profile can't be deleted`(@TempDir dir: Path) {
        val config = ConfigSystem(dir, { emptyMap() })
        assertFailsWith<IllegalArgumentException> { config.save("../evil") }
        assertFailsWith<IllegalArgumentException> { config.delete(config.activeProfile) }
    }

    // --- Commands and keybinds ---

    private val keys = KeyResolver { input -> KeyNames.toMinecraftName(input)?.also { if ("zz" in it) throw CommandException("Unknown key '$input'") } }

    private fun commandSetup(dir: Path): Triple<CommandManager, ModuleManager, Fly> {
        val modules = ModuleManager(bus)
        val fly = Fly()
        modules.register(fly)
        val commands = CommandManager()
        commands.register(HelpCommand(commands), ToggleCommand(modules), BindCommand(modules, keys), ConfigCommand(ConfigSystem(dir, { mapOf("modules" to modules.modules) })))
        return Triple(commands, modules, fly)
    }

    @Test
    fun `toggle, bind and config commands work and report errors`(@TempDir dir: Path) {
        val (commands, _, fly) = commandSetup(dir)
        val out = Output()
        assertFalse(commands.execute("hello everyone", out), "normal chat is not a command")
        assertTrue(commands.execute(".t fly", out))
        assertTrue(fly.enabled)
        commands.execute(".toggle FLY off", out)
        assertFalse(fly.enabled)

        commands.execute(".bind fly rshift hold", out)
        assertEquals(KeyBind("key.keyboard.right.shift", BindMode.HOLD), fly.bind.value)
        commands.execute(".bind fly zz", out)
        commands.execute(".bind fly none", out)
        assertNull(fly.bind.value.key)

        commands.execute(".config save pvp", out)
        commands.execute(".config list", out)
        commands.execute(".nope", out)
        commands.execute(".toggle", out)
        assertEquals(
            listOf(
                "Fly §aenabled", "Fly §cdisabled", "Bound Fly to RSHIFT (hold)", "ERROR: Unknown key 'zz'", "Unbound Fly",
                "Saved as pvp (now active)", "Profiles: §apvp§r (active)", "ERROR: Unknown command 'nope'. Try .help", "ERROR: Usage: toggle <module> [on|off]",
            ),
            out.lines,
        )
    }

    @Test
    fun `completion suggests commands and arguments`(@TempDir dir: Path) {
        val (commands, _, _) = commandSetup(dir)
        assertEquals(listOf("toggle"), commands.complete(".to"))
        assertEquals(listOf("Fly"), commands.complete(".toggle F"))
        assertEquals(listOf("load"), commands.complete(".config lo"))
        assertEquals(listOf("save", "my profile"), CommandManager.tokenize("save \"my profile\""))
    }

    @Test
    fun `keybinds toggle or hold, and never fire while a screen is open`(@TempDir dir: Path) {
        val (_, _, fly) = commandSetup(dir)
        fly.bind.value = KeyBind("key.keyboard.f")
        bus.post(KeyEvent("key.keyboard.f", KeyEvent.PRESS, screenOpen = false))
        assertTrue(fly.enabled)
        bus.post(KeyEvent("key.keyboard.f", KeyEvent.RELEASE, screenOpen = false))
        assertTrue(fly.enabled, "toggle mode ignores release")
        bus.post(KeyEvent("key.keyboard.f", KeyEvent.PRESS, screenOpen = true))
        assertTrue(fly.enabled, "typing in chat must not toggle")

        fly.bind.value = KeyBind("key.keyboard.f", BindMode.HOLD)
        fly.enabled = false
        bus.post(KeyEvent("key.keyboard.f", KeyEvent.PRESS, screenOpen = false))
        assertTrue(fly.enabled)
        bus.post(KeyEvent("key.keyboard.f", KeyEvent.RELEASE, screenOpen = false))
        assertFalse(fly.enabled)
    }

    @Test
    fun `key names map user input to Minecraft names and back`() {
        assertEquals("key.keyboard.r", KeyNames.toMinecraftName("R"))
        assertEquals("key.keyboard.right.shift", KeyNames.toMinecraftName("rshift"))
        assertEquals("key.keyboard.keypad.5", KeyNames.toMinecraftName("num5"))
        assertEquals("key.mouse.4", KeyNames.toMinecraftName("mouse4"))
        assertNull(KeyNames.toMinecraftName("none"))
        assertEquals("RSHIFT", KeyNames.label("key.keyboard.right.shift"))
        assertEquals("F5", KeyNames.label("key.keyboard.f5"))
        assertEquals("NONE", KeyNames.label(null))
        assertEquals(JsonPrimitive("x"), JsonPrimitive("x"))
    }
}
