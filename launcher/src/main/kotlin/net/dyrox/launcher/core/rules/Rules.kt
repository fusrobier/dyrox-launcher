package net.dyrox.launcher.core.rules

import net.dyrox.launcher.core.version.Rule
import net.dyrox.shared.platform.Platform

/** Feature flags that version JSON argument rules can test for. */
object Features {
    const val DEMO_USER = "is_demo_user"
    const val CUSTOM_RESOLUTION = "has_custom_resolution"
    const val QUICK_PLAYS_SUPPORT = "has_quick_plays_support"
    const val QUICK_PLAY_SINGLEPLAYER = "is_quick_play_singleplayer"
    const val QUICK_PLAY_MULTIPLAYER = "is_quick_play_multiplayer"
    const val QUICK_PLAY_REALMS = "is_quick_play_realms"
}

data class RuleContext(
    val platform: Platform,
    val features: Set<String> = emptySet(),
)

/**
 * Mojang's rule semantics: no rules means allowed; otherwise start from "disallowed" and let every
 * matching rule, in order, set the outcome to its action. The last match wins.
 */
object Rules {
    fun allows(rules: List<Rule>, context: RuleContext): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (matches(rule, context)) allowed = rule.action == Rule.Action.ALLOW
        }
        return allowed
    }

    private fun matches(rule: Rule, context: RuleContext): Boolean {
        rule.os?.let { os ->
            if (os.name != null && os.name != context.platform.os.mojangName) return false
            if (os.arch != null && os.arch != context.platform.arch.mojangName) return false
            if (os.version != null && !Regex(os.version).containsMatchIn(context.platform.osVersion)) return false
        }
        rule.features?.forEach { (feature, expected) ->
            if ((feature in context.features) != expected) return false
        }
        return true
    }
}
