package net.dyrox.launcher.cli

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dyrox.launcher.core.LaunchProgress
import net.dyrox.launcher.core.LaunchRequest
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.core.LoaderSpec
import net.dyrox.launcher.core.launch.LaunchIdentity
import net.dyrox.launcher.core.launch.Resolution
import net.dyrox.launcher.core.process.GameProcess
import net.dyrox.shared.auth.OfflineProfiles
import kotlin.system.exitProcess

/**
 * Headless front-end to the launcher core, for testing without the UI:
 *
 *     gradlew :launcher:runCli --args="versions"
 *     gradlew :launcher:runCli --args="launch 26.3 --fabric --user Steve"
 *     gradlew :launcher:runCli --args="launch 1.8.9 --dry-run"
 */
fun main(args: Array<String>) {
    val exitCode = runBlocking { DevCli(LauncherCore()).run(args.toList()) }
    exitProcess(exitCode)
}

class DevCli(private val core: LauncherCore) {
    suspend fun run(args: List<String>): Int = when (args.firstOrNull()) {
        "versions" -> listVersions(includeSnapshots = "--snapshots" in args)
        "launch" -> launch(args.drop(1))
        else -> {
            println(USAGE)
            if (args.isEmpty()) 0 else 1
        }
    }

    private suspend fun listVersions(includeSnapshots: Boolean): Int {
        val manifest = core.manifests.manifest()
        println("Latest release: ${manifest.latest.release}   latest snapshot: ${manifest.latest.snapshot}")
        manifest.versions
            .filter { it.isRelease || (includeSnapshots && it.isSnapshot) }
            .forEach { println("  ${it.id.padEnd(24)} ${it.type.padEnd(10)} ${it.releaseTime.take(10)}") }
        return 0
    }

    private suspend fun launch(args: List<String>): Int {
        val options = parseOptions(args) ?: return 1
        val versionArg = options.positional ?: "latest"
        val gameVersion = if (versionArg == "latest") core.manifests.manifest().latest.release else versionArg
        val username = options.values["user"] ?: "Player"
        if (!OfflineProfiles.isValidName(username)) {
            System.err.println("'$username' is not a valid Minecraft name (3-16 of A-Z, a-z, 0-9, _)")
            return 1
        }

        val instance = core.defaultInstanceDir
        val request = LaunchRequest(
            gameVersion = gameVersion,
            loader = if ("fabric" in options.flags) LoaderSpec.Fabric() else LoaderSpec.Vanilla,
            identity = LaunchIdentity.offline(username),
            gameDirectory = instance.resolve("minecraft"),
            nativesDirectory = instance.resolve("natives"),
            maxMemoryMb = options.values["ram"]?.toIntOrNull() ?: 4096,
            resolution = options.values["width"]?.toIntOrNull()?.let { w ->
                Resolution(w, options.values["height"]?.toIntOrNull() ?: (w * 9 / 16))
            },
        )

        println("Data directory: ${core.paths.root}")
        var lastPrint = 0L
        val command = core.gameLauncher.prepare(request) { progress ->
            when (progress) {
                is LaunchProgress.Stage -> println("> ${progress.stage.label} ${progress.detail}".trimEnd())
                is LaunchProgress.Download -> {
                    val now = System.currentTimeMillis()
                    val p = progress.progress
                    if (p.totalFiles > 0 && (now - lastPrint > 1000 || p.completedFiles == p.totalFiles)) {
                        lastPrint = now
                        println("   ${p.completedFiles}/${p.totalFiles} files, ${p.downloadedBytes / 1_048_576}/${p.totalBytes / 1_048_576} MiB")
                    }
                }
            }
        }

        println("Command: ${command.redactedCommandLine().joinToString(" ")}")
        if ("dry-run" in options.flags) return 0

        return coroutineScope {
            val process = GameProcess.start(command, this)
            println("Started game, PID ${process.pid}")
            val printer = launch {
                process.lines.collect { line -> println("[${line.level}] ${line.message}") }
            }
            val code = process.exitCode.await()
            printer.cancel()
            println("Game exited with code $code")
            code
        }
    }

    private data class Options(val positional: String?, val flags: Set<String>, val values: Map<String, String>)

    private fun parseOptions(args: List<String>): Options? {
        var positional: String? = null
        val flags = HashSet<String>()
        val values = HashMap<String, String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg.removePrefix("--") in VALUE_OPTIONS -> {
                    val value = args.getOrNull(i + 1) ?: run { System.err.println("$arg needs a value"); return null }
                    values[arg.removePrefix("--")] = value
                    i++
                }
                arg.startsWith("--") -> flags += arg.removePrefix("--")
                positional == null -> positional = arg
                else -> { System.err.println("Unexpected argument: $arg"); return null }
            }
            i++
        }
        return Options(positional, flags, values)
    }

    private companion object {
        val VALUE_OPTIONS = setOf("user", "ram", "width", "height")
        val USAGE = """
            Dyrox dev CLI
              versions [--snapshots]                 list Minecraft versions
              launch [<version>|latest] [options]    install and start a version (offline account)
                --fabric         install Fabric loader + Fabric API
                --user NAME      offline username (default: Player)
                --ram MB         max heap (default: 4096)
                --width W [--height H]
                --dry-run        prepare everything and print the command, but don't start the game
        """.trimIndent()
    }
}
