package net.dyrox.launcher.core.launch

import net.dyrox.launcher.core.install.InstalledGame
import net.dyrox.launcher.core.rules.Features
import net.dyrox.launcher.core.rules.RuleContext
import net.dyrox.launcher.core.rules.Rules
import net.dyrox.launcher.core.version.Argument
import net.dyrox.shared.platform.OperatingSystem
import net.dyrox.shared.platform.Platform
import java.nio.file.Path

/**
 * Builds the full `java ... <mainClass> <game args>` command. Pure: no I/O, all inputs explicit,
 * which is what makes it unit-testable against real version JSONs.
 *
 * Order: memory, version JVM args (or legacy defaults), log config, system properties,
 * user JVM args (last, so they win), main class, game args, user game args.
 */
class LaunchCommandBuilder(
    private val launcherName: String,
    private val launcherVersion: String,
) {
    fun build(game: InstalledGame, identity: LaunchIdentity, options: LaunchOptions, platform: Platform): LaunchCommand {
        val version = game.version
        val features = buildSet {
            if (options.resolution != null) add(Features.CUSTOM_RESOLUTION)
        }
        val context = RuleContext(platform, features)
        val variables = variables(game, identity, options, platform)
        fun substitute(value: String) = PLACEHOLDER.replace(value) { match ->
            variables[match.groupValues[1]] ?: match.value
        }
        fun List<Argument>.evaluate() = filter { Rules.allows(it.rules, context) }.flatMap { it.values }.map(::substitute)

        val arguments = buildList {
            add("-Xms${options.minMemoryMb}M")
            add("-Xmx${options.maxMemoryMb}M")
            // Game output is parsed by the launcher; force UTF-8 regardless of the OS code page (Java 19+).
            add("-Dstdout.encoding=UTF-8")
            add("-Dstderr.encoding=UTF-8")

            if (version.usesLegacyArguments) {
                if (platform.os == OperatingSystem.MACOS) add("-XstartOnFirstThread")
                addAll(LEGACY_JVM_ARGUMENTS.map(::substitute))
            }
            addAll(version.jvmArguments.evaluate())

            val logging = version.logging
            if (logging != null && game.logConfig != null) {
                add(logging.argument.replace("\${path}", game.logConfig.toAbsolutePath().toString()))
            }
            options.systemProperties.forEach { (key, value) -> add("-D$key=$value") }
            addAll(options.extraJvmArguments)

            add(version.mainClass)

            version.legacyGameArguments?.let { legacy ->
                addAll(legacy.split(' ').filter(String::isNotBlank).map(::substitute))
                // Pre-1.13 versions have no resolution rule, so pass it explicitly.
                options.resolution?.let { addAll(listOf("--width", it.width.toString(), "--height", it.height.toString())) }
            }
            addAll(version.gameArguments.evaluate())
            addAll(options.extraGameArguments)
        }

        return LaunchCommand(
            executable = options.javaExecutable,
            arguments = arguments,
            workingDirectory = options.gameDirectory,
            secrets = listOf(identity.accessToken),
        )
    }

    private fun variables(game: InstalledGame, identity: LaunchIdentity, options: LaunchOptions, platform: Platform): Map<String, String> {
        val version = game.version
        return mapOf(
            "auth_player_name" to identity.username,
            "auth_uuid" to identity.uuid,
            "auth_access_token" to identity.accessToken,
            "auth_session" to "token:${identity.accessToken}:${identity.uuid}",
            "auth_xuid" to identity.xuid,
            "clientid" to identity.clientId,
            "user_type" to identity.userType,
            "user_properties" to "{}",
            "version_name" to version.id,
            "version_type" to version.type,
            "game_directory" to options.gameDirectory.abs(),
            "assets_root" to game.assetsRoot.abs(),
            "game_assets" to game.gameAssetsDir.abs(),
            "assets_index_name" to version.assetIndex.id,
            "natives_directory" to options.nativesDirectory.abs(),
            "library_directory" to game.librariesDir.abs(),
            "classpath" to game.classpath.joinToString(platform.classpathSeparator) { it.abs() },
            "classpath_separator" to platform.classpathSeparator,
            "launcher_name" to launcherName,
            "launcher_version" to launcherVersion,
            "resolution_width" to (options.resolution?.width ?: DEFAULT_WIDTH).toString(),
            "resolution_height" to (options.resolution?.height ?: DEFAULT_HEIGHT).toString(),
        )
    }

    private fun Path.abs(): String = toAbsolutePath().normalize().toString()

    companion object {
        private val PLACEHOLDER = Regex("""\$\{([A-Za-z0-9_]+)}""")
        private const val DEFAULT_WIDTH = 854
        private const val DEFAULT_HEIGHT = 480

        /** What the official launcher passes to versions whose JSON has no JVM arguments. */
        private val LEGACY_JVM_ARGUMENTS = listOf(
            "-Djava.library.path=\${natives_directory}",
            "-Dminecraft.launcher.brand=\${launcher_name}",
            "-Dminecraft.launcher.version=\${launcher_version}",
            "-cp",
            "\${classpath}",
        )
    }
}
