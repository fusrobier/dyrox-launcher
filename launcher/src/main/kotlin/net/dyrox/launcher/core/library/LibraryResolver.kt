package net.dyrox.launcher.core.library

import net.dyrox.launcher.core.rules.RuleContext
import net.dyrox.launcher.core.rules.Rules
import net.dyrox.launcher.core.version.Artifact
import net.dyrox.launcher.core.version.Library

/** A file inside the libraries directory, with where to fetch it from and how to verify it. */
data class LibraryFile(
    /** Relative, '/'-separated path under the libraries directory. */
    val path: String,
    /** Null when the JSON gives no source (the file must already exist locally). */
    val url: String?,
    val sha1: String?,
    val size: Long?,
)

data class ResolvedLibrary(
    val coordinate: MavenCoordinate,
    /** Goes on the classpath. */
    val artifact: LibraryFile?,
    /** Legacy natives jar, extracted into the natives directory before launch. */
    val native: LibraryFile?,
    val extractExcludes: List<String>,
)

/** Turns library entries into concrete files for one platform, applying rules and native classifiers. */
object LibraryResolver {
    const val MOJANG_LIBRARIES = "https://libraries.minecraft.net/"

    fun resolve(libraries: List<Library>, context: RuleContext): List<ResolvedLibrary> =
        libraries.mapNotNull { resolve(it, context) }

    fun resolve(library: Library, context: RuleContext): ResolvedLibrary? {
        if (!Rules.allows(library.rules, context)) return null
        val coordinate = MavenCoordinate.parse(library.name)
        val artifact = artifactOf(library, coordinate)
        val native = nativeOf(library, coordinate, context)
        if (artifact == null && native == null) return null
        return ResolvedLibrary(coordinate, artifact, native, library.extract?.exclude.orEmpty())
    }

    private fun artifactOf(library: Library, coordinate: MavenCoordinate): LibraryFile? {
        val downloads = library.downloads
        if (downloads != null) {
            return downloads.artifact?.toLibraryFile(coordinate)
        }
        // Old-style natives-only entries have no main artifact.
        if (library.natives != null) return null
        return mavenFile(library, coordinate, library.sha1, library.size)
    }

    private fun nativeOf(library: Library, coordinate: MavenCoordinate, context: RuleContext): LibraryFile? {
        val template = library.natives?.get(context.platform.os.mojangName) ?: return null
        val classifier = template.replace("\${arch}", context.platform.arch.bits.toString())
        val nativeCoordinate = coordinate.withClassifier(classifier)
        library.downloads?.classifiers?.get(classifier)?.let { return it.toLibraryFile(nativeCoordinate) }
        return mavenFile(library, nativeCoordinate, sha1 = null, size = null)
    }

    private fun mavenFile(library: Library, coordinate: MavenCoordinate, sha1: String?, size: Long?): LibraryFile {
        val base = (library.url ?: MOJANG_LIBRARIES).let { if (it.endsWith('/')) it else "$it/" }
        return LibraryFile(coordinate.path, base + coordinate.path, sha1, size)
    }

    private fun Artifact.toLibraryFile(coordinate: MavenCoordinate) =
        LibraryFile(path ?: coordinate.path, url.ifBlank { null }, sha1, size)
}
