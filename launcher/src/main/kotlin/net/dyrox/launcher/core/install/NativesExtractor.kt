package net.dyrox.launcher.core.install

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.deleteRecursively

/**
 * Extracts legacy natives jars (LWJGL 2 era) into an instance's natives directory. Modern versions
 * ship natives as regular classpath jars and LWJGL extracts them itself, so they need nothing here.
 */
object NativesExtractor {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun extract(archives: List<NativeArchive>, targetDir: Path) {
        if (archives.isEmpty()) return
        val root = targetDir.toAbsolutePath().normalize()
        // Start clean so natives from a previously launched version can't be picked up.
        if (Files.exists(root)) root.deleteRecursively()
        Files.createDirectories(root)
        for (archive in archives) {
            ZipFile(archive.jar.toFile()).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory || archive.excludes.any { entry.name.startsWith(it) }) continue
                    val out = root.resolve(entry.name).normalize()
                    require(out.startsWith(root)) { "Zip entry ${entry.name} escapes $root" }
                    out.parent?.let(Files::createDirectories)
                    zip.getInputStream(entry).use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }
}
