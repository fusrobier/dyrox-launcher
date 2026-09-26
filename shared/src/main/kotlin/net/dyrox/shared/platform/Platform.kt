package net.dyrox.shared.platform

/** Operating systems, named the way Mojang's version JSON rules name them. */
enum class OperatingSystem(val mojangName: String) {
    WINDOWS("windows"),
    LINUX("linux"),
    MACOS("osx"),
    UNKNOWN("unknown");

    companion object {
        val current: OperatingSystem by lazy { fromOsName(System.getProperty("os.name").orEmpty()) }

        fun fromOsName(osName: String): OperatingSystem {
            val name = osName.lowercase()
            return when {
                name.startsWith("windows") -> WINDOWS
                name.startsWith("mac") || name.startsWith("darwin") -> MACOS
                name.contains("linux") || name.contains("nux") -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

/** CPU architectures. [mojangName] matches the `arch` field of version JSON rules. */
enum class Architecture(val mojangName: String, val bits: Int) {
    X86("x86", 32),
    X86_64("x86_64", 64),
    ARM64("arm64", 64),
    ARM32("arm32", 32),
    UNKNOWN("unknown", 64);

    companion object {
        val current: Architecture by lazy { fromOsArch(System.getProperty("os.arch").orEmpty()) }

        fun fromOsArch(osArch: String): Architecture = when (osArch.lowercase()) {
            "amd64", "x86_64", "x64" -> X86_64
            "x86", "i386", "i486", "i586", "i686" -> X86
            "aarch64", "arm64" -> ARM64
            "arm", "arm32", "armv7l", "aarch32" -> ARM32
            else -> UNKNOWN
        }
    }
}

/** The platform a game is launched on. Passed around explicitly so launch logic stays testable. */
data class Platform(
    val os: OperatingSystem,
    val arch: Architecture,
    val osVersion: String,
) {
    val classpathSeparator: String get() = if (os == OperatingSystem.WINDOWS) ";" else ":"

    companion object {
        val current: Platform by lazy {
            Platform(OperatingSystem.current, Architecture.current, System.getProperty("os.version").orEmpty())
        }
    }
}
