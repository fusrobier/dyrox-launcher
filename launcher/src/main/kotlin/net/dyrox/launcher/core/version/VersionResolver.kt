package net.dyrox.launcher.core.version

import net.dyrox.launcher.core.library.MavenCoordinate

class VersionResolutionException(message: String) : RuntimeException(message)

/** Follows `inheritsFrom` links and merges the chain, child values taking precedence over parents. */
class VersionResolver(private val load: suspend (String) -> VersionJson) {
    suspend fun resolve(id: String): ResolvedVersion {
        val chain = ArrayList<VersionJson>()
        val seen = HashSet<String>()
        var next: String? = id
        while (next != null) {
            if (!seen.add(next)) throw VersionResolutionException("Cyclic inheritsFrom chain: ${seen.joinToString(" -> ")} -> $next")
            if (chain.size >= MAX_DEPTH) throw VersionResolutionException("inheritsFrom chain of $id is too deep")
            val json = load(next)
            chain += json
            next = json.inheritsFrom
        }
        return merge(chain)
    }

    companion object {
        private const val MAX_DEPTH = 8

        /** [chain] is ordered leaf first, root last. */
        fun merge(chain: List<VersionJson>): ResolvedVersion {
            require(chain.isNotEmpty()) { "Empty version chain" }
            val leaf = chain.first()
            val rootToLeaf = chain.asReversed()

            val mainClass = chain.firstNotNullOfOrNull { it.mainClass }
                ?: throw VersionResolutionException("${leaf.id} has no mainClass")
            val assetIndex = chain.firstNotNullOfOrNull { it.assetIndex }
                ?: throw VersionResolutionException("${leaf.id} has no assetIndex")
            val jarOwner = chain.firstOrNull { it.downloads?.client != null } ?: chain.last()

            return ResolvedVersion(
                id = leaf.id,
                gameVersion = chain.last().id,
                jarId = jarOwner.id,
                type = chain.firstNotNullOfOrNull { it.type } ?: "release",
                mainClass = mainClass,
                libraries = mergeLibraries(chain),
                // Arguments are cumulative: parent arguments first, then each child's additions.
                jvmArguments = rootToLeaf.flatMap { it.arguments?.jvm.orEmpty() },
                gameArguments = rootToLeaf.flatMap { it.arguments?.game.orEmpty() },
                legacyGameArguments = chain.firstNotNullOfOrNull { it.minecraftArguments },
                assetIndex = assetIndex,
                clientDownload = jarOwner.downloads?.client,
                javaVersion = chain.firstNotNullOfOrNull { it.javaVersion },
                logging = chain.firstNotNullOfOrNull { it.logging?.client },
            )
        }

        /**
         * Child libraries come first. A parent library is dropped when a child already declares the same
         * group:artifact[:classifier]. Duplicates inside one JSON are kept, since they are usually
         * OS-specific variants guarded by rules.
         */
        private fun mergeLibraries(chain: List<VersionJson>): List<Library> {
            val result = ArrayList<Library>()
            val declaredByChildren = HashSet<String>()
            for (version in chain) {
                val keys = version.libraries.map { MavenCoordinate.parse(it.name).versionlessKey }
                version.libraries.forEachIndexed { index, library ->
                    if (keys[index] !in declaredByChildren) result += library
                }
                declaredByChildren += keys
            }
            return result
        }
    }
}
