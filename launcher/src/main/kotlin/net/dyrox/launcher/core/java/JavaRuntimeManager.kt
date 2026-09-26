package net.dyrox.launcher.core.java

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.download.DownloadTask
import net.dyrox.launcher.core.version.DownloadRef
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.http.ChecksumMismatchException
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import net.dyrox.shared.json.DyroxJsonPretty
import net.dyrox.shared.platform.Architecture
import net.dyrox.shared.platform.OperatingSystem
import net.dyrox.shared.platform.Platform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

@Serializable
data class RuntimeEntry(val manifest: DownloadRef, val version: RuntimeVersion)

@Serializable
data class RuntimeVersion(val name: String, val released: String? = null)

@Serializable
data class RuntimeFilesManifest(val files: Map<String, RuntimeFile>)

@Serializable
data class RuntimeFile(
    /** `file`, `directory` or `link`. */
    val type: String,
    val executable: Boolean = false,
    val downloads: RuntimeFileDownloads? = null,
    /** Link target, relative to the link. */
    val target: String? = null,
)

@Serializable
data class RuntimeFileDownloads(val raw: DownloadRef, val lzma: DownloadRef? = null)

@Serializable
private data class InstalledRuntimeMarker(val component: String, val version: String, val manifestSha1: String)

class UnsupportedRuntimeException(message: String) : RuntimeException(message)

/**
 * Installs the Java runtime Mojang assigns to a version (`javaVersion.component`, e.g.
 * `java-runtime-epsilon` = Java 25 for 26.3) from Mojang's runtime manifest, verifying every file.
 */
class JavaRuntimeManager(
    private val paths: LauncherPaths,
    private val http: HttpService,
    private val downloads: DownloadManager,
    private val platform: Platform = Platform.current,
) {
    private val mutex = Mutex()
    private var catalog: Map<String, Map<String, List<RuntimeEntry>>>? = null

    /** Returns the `java`/`javaw` executable for [component], installing or updating it if needed. */
    suspend fun ensure(component: String, onProgress: (DownloadProgress) -> Unit = {}): Path = withContext(Dispatchers.IO) {
        val platformKey = platformKey(platform)
            ?: throw UnsupportedRuntimeException("Mojang provides no Java runtimes for ${platform.os}/${platform.arch}; set a custom Java path")
        val dir = paths.runtimesDir.resolve(component).resolve(platformKey)
        val executable = javaExecutable(dir, platform.os)
        val marker = readMarker(dir)

        val entry = try {
            loadCatalog()[platformKey]?.get(component)?.firstOrNull()
        } catch (e: IOException) {
            // Offline: fall back to whatever is installed.
            if (marker != null && Files.isRegularFile(executable)) return@withContext executable
            throw e
        } ?: throw UnsupportedRuntimeException("Java runtime '$component' is not available for $platformKey")

        if (marker?.manifestSha1 == entry.manifest.sha1 && Files.isRegularFile(executable)) return@withContext executable

        val manifestBytes = http.getBytes(entry.manifest.url)
        if (!Hashing.sha1(manifestBytes).equals(entry.manifest.sha1, ignoreCase = true)) {
            throw ChecksumMismatchException("Runtime manifest for $component failed SHA-1 verification")
        }
        val files = DyroxJson.decodeFromString(RuntimeFilesManifest.serializer(), manifestBytes.toString(Charsets.UTF_8)).files

        for ((name, file) in files) {
            if (file.type == "directory") Files.createDirectories(AtomicFiles.resolveInside(dir, name))
        }
        val tasks = files.mapNotNull { (name, file) ->
            val raw = file.downloads?.raw ?: return@mapNotNull null
            if (file.type != "file") return@mapNotNull null
            DownloadTask(raw.url, AtomicFiles.resolveInside(dir, name), raw.sha1, raw.size)
        }
        downloads.downloadAll(tasks, onProgress)

        if (platform.os != OperatingSystem.WINDOWS) applyUnixMetadata(dir, files)
        if (!Files.isRegularFile(executable)) throw IOException("Runtime $component installed but $executable is missing")

        writeMarker(dir, InstalledRuntimeMarker(component, entry.version.name, entry.manifest.sha1))
        executable
    }

    private suspend fun loadCatalog(): Map<String, Map<String, List<RuntimeEntry>>> = mutex.withLock {
        catalog ?: http.getJson(CATALOG_URL, CATALOG_SERIALIZER).also { catalog = it }
    }

    private fun applyUnixMetadata(dir: Path, files: Map<String, RuntimeFile>) {
        val executablePermissions = PosixFilePermissions.fromString("rwxr-xr-x")
        for ((name, file) in files) {
            val path = AtomicFiles.resolveInside(dir, name)
            when {
                file.type == "file" && file.executable -> Files.setPosixFilePermissions(path, executablePermissions)
                file.type == "link" && file.target != null -> {
                    Files.deleteIfExists(path)
                    path.parent?.let(Files::createDirectories)
                    Files.createSymbolicLink(path, Path.of(file.target))
                }
            }
        }
    }

    private fun readMarker(dir: Path): InstalledRuntimeMarker? {
        val file = dir.resolve(MARKER_FILE)
        if (!Files.isRegularFile(file)) return null
        return runCatching { DyroxJson.decodeFromString(InstalledRuntimeMarker.serializer(), Files.readString(file)) }.getOrNull()
    }

    private fun writeMarker(dir: Path, marker: InstalledRuntimeMarker) =
        AtomicFiles.writeString(dir.resolve(MARKER_FILE), DyroxJsonPretty.encodeToString(InstalledRuntimeMarker.serializer(), marker))

    companion object {
        const val CATALOG_URL =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
        /** Used when a version JSON predates the `javaVersion` field (those versions all run on Java 8). */
        const val LEGACY_COMPONENT = "jre-legacy"
        private const val MARKER_FILE = ".dyrox-runtime.json"

        private val CATALOG_SERIALIZER = MapSerializer(
            String.serializer(),
            MapSerializer(String.serializer(), ListSerializer(RuntimeEntry.serializer())),
        )

        /** Key used by Mojang's runtime catalog for [platform], or null if Mojang ships nothing for it. */
        fun platformKey(platform: Platform): String? = when (platform.os) {
            OperatingSystem.WINDOWS -> when (platform.arch) {
                Architecture.X86_64 -> "windows-x64"
                Architecture.X86 -> "windows-x86"
                Architecture.ARM64 -> "windows-arm64"
                else -> null
            }
            OperatingSystem.LINUX -> when (platform.arch) {
                Architecture.X86_64 -> "linux"
                Architecture.X86 -> "linux-i386"
                else -> null
            }
            OperatingSystem.MACOS -> when (platform.arch) {
                Architecture.ARM64 -> "mac-os-arm64"
                Architecture.X86_64 -> "mac-os"
                else -> null
            }
            OperatingSystem.UNKNOWN -> null
        }

        /** `javaw.exe` on Windows so the game gets no console window; output is still captured via pipes. */
        fun javaExecutable(runtimeDir: Path, os: OperatingSystem): Path = when (os) {
            OperatingSystem.WINDOWS -> runtimeDir.resolve("bin").resolve("javaw.exe")
            OperatingSystem.MACOS -> runtimeDir.resolve("jre.bundle/Contents/Home/bin/java")
            else -> runtimeDir.resolve("bin").resolve("java")
        }
    }
}
