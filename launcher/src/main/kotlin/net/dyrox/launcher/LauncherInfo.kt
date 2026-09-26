package net.dyrox.launcher

object LauncherInfo {
    const val DISPLAY_NAME = "Dyrox Launcher"

    /** Value of `${launcher_name}`; the game shows it in crash reports and F3. */
    const val BRAND = "dyrox-launcher"

    val version: String = LauncherInfo::class.java.`package`?.implementationVersion ?: "dev"

    /** Mojang, Fabric and Modrinth ask API clients to identify themselves. */
    val userAgent: String get() = "$BRAND/$version"
}
