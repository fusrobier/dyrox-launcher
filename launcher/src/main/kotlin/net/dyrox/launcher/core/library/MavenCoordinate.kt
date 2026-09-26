package net.dyrox.launcher.core.library

/** `group:artifact:version[:classifier][@extension]` */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
    val extension: String = "jar",
) {
    /** Repository-relative path, e.g. `org/ow2/asm/asm/9.10.1/asm-9.10.1.jar`. */
    val path: String
        get() {
            val suffix = classifier?.let { "-$it" }.orEmpty()
            return "${group.replace('.', '/')}/$artifact/$version/$artifact-$version$suffix.$extension"
        }

    /**
     * Identity without the version. When a child profile (Fabric) and its parent (vanilla) both list a
     * library with the same key, only the child's copy is kept; two ASM versions on one classpath crash Fabric.
     */
    val versionlessKey: String get() = "$group:$artifact" + classifier?.let { ":$it" }.orEmpty()

    fun withClassifier(classifier: String): MavenCoordinate = copy(classifier = classifier)

    override fun toString(): String =
        "$group:$artifact:$version" + classifier?.let { ":$it" }.orEmpty() + if (extension != "jar") "@$extension" else ""

    companion object {
        fun parse(notation: String): MavenCoordinate {
            val coordinates = notation.substringBefore('@')
            val extension = notation.substringAfter('@', "jar")
            val parts = coordinates.split(':')
            require(parts.size in 3..4 && parts.none(String::isBlank)) { "Invalid Maven coordinate: $notation" }
            return MavenCoordinate(parts[0], parts[1], parts[2], parts.getOrNull(3), extension)
        }
    }
}
