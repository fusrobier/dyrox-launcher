package net.dyrox.client.command

import net.dyrox.client.combat.Friends

/** `.friend add|remove <name>`, `.friend list`, `.friend clear`. Friends are shared by all profiles. */
class FriendCommand : Command("friend", "Manages friends (never attacked)", "<add|remove|list|clear> [name]", listOf("f")) {
    private val nameRegex = Regex("^[A-Za-z0-9_]{1,16}$")

    override fun execute(args: List<String>, output: CommandOutput) {
        when (args.firstOrNull()?.lowercase()) {
            "add" -> {
                val name = name(args)
                if (Friends.add(name)) output.info("Added $name to your friends.") else output.info("$name is already a friend.")
            }
            "remove", "del" -> {
                val name = name(args)
                if (Friends.remove(name)) output.info("Removed $name.") else fail("$name is not a friend.")
            }
            "list" -> {
                val all = Friends.all
                output.info(if (all.isEmpty()) "No friends yet. Middle-click a player or use .friend add <name>." else "Friends (${all.size}): ${all.joinToString(", ")}")
            }
            "clear" -> {
                Friends.clear()
                output.info("Friend list cleared.")
            }
            else -> usageError()
        }
    }

    override fun complete(args: List<String>): List<String> = when (args.size) {
        1 -> listOf("add", "remove", "list", "clear").filter { it.startsWith(args[0], true) }
        2 -> if (args[0].equals("remove", true)) Friends.all.filter { it.startsWith(args[1], true) } else emptyList()
        else -> emptyList()
    }

    private fun name(args: List<String>): String {
        val name = args.getOrNull(1) ?: usageError()
        if (!nameRegex.matches(name)) fail("'$name' is not a valid Minecraft name")
        return name
    }
}
