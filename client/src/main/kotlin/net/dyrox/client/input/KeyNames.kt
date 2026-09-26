package net.dyrox.client.input

/**
 * Converts what users type (`r`, `rshift`, `F5`, `mouse4`) to Minecraft key names
 * (`key.keyboard.r`, `key.keyboard.right.shift`, ...), and back to short labels.
 *
 * Minecraft 26.x identifies keys by SDL scancode internally, but its key *names* are stable across
 * versions, so binds are stored by name and resolved through `InputConstants` at runtime.
 */
object KeyNames {
    private const val KEYBOARD = "key.keyboard."
    private const val MOUSE = "key.mouse."

    private val aliases = mapOf(
        "rshift" to "right.shift", "lshift" to "left.shift",
        "rctrl" to "right.control", "lctrl" to "left.control", "rcontrol" to "right.control", "lcontrol" to "left.control",
        "ralt" to "right.alt", "lalt" to "left.alt",
        "rwin" to "right.win", "lwin" to "left.win",
        "esc" to "escape", "del" to "delete", "ins" to "insert", "pgup" to "page.up", "pgdn" to "page.down", "pgdown" to "page.down",
        "caps" to "caps.lock", "capslock" to "caps.lock", "numlock" to "num.lock", "scrolllock" to "scroll.lock",
        "return" to "enter", "backspace" to "backspace",
        "up" to "up", "down" to "down", "left" to "left", "right" to "right",
        "minus" to "minus", "equals" to "equal",
    )

    private val mouseAliases = mapOf("lmb" to "left", "rmb" to "right", "mmb" to "middle", "mouse1" to "left", "mouse2" to "right", "mouse3" to "middle")

    /**
     * Candidate Minecraft key name for [input], or null for "none"/"unbind". The result still has to
     * be validated against the game (see `MinecraftKeys`).
     */
    fun toMinecraftName(input: String): String? {
        val raw = input.trim().lowercase()
        if (raw in setOf("none", "unbind", "null", "-")) return null
        if (raw.startsWith(KEYBOARD) || raw.startsWith(MOUSE)) return raw
        mouseAliases[raw]?.let { return MOUSE + it }
        Regex("^mouse(\\d+)$").matchEntire(raw)?.let { return MOUSE + it.groupValues[1] }
        val numpad = Regex("^(?:kp|num|numpad)(\\d)$").matchEntire(raw)
        if (numpad != null) return KEYBOARD + "keypad." + numpad.groupValues[1]
        return KEYBOARD + (aliases[raw] ?: raw.replace('_', '.').replace(' ', '.'))
    }

    /** Short label for UIs and chat: `key.keyboard.right.shift` → `RSHIFT`. */
    fun label(name: String?): String {
        if (name == null) return "NONE"
        aliases.entries.firstOrNull { KEYBOARD + it.value == name && it.key.length <= 6 }?.let { return it.key.uppercase() }
        return name.removePrefix(KEYBOARD).removePrefix(MOUSE).replace('.', ' ').uppercase()
    }
}
