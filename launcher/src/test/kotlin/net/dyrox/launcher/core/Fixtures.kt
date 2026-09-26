package net.dyrox.launcher.core

import net.dyrox.launcher.core.version.VersionJson
import net.dyrox.launcher.core.version.VersionResolver
import net.dyrox.shared.json.DyroxJson
import net.dyrox.shared.platform.Architecture
import net.dyrox.shared.platform.OperatingSystem
import net.dyrox.shared.platform.Platform

/** Real version JSONs downloaded from Mojang and Fabric, stored under test resources. */
object Fixtures {
    const val VANILLA_26_3 = "26.3"
    const val LEGACY_1_8_9 = "1.8.9"
    const val FABRIC_26_3 = "fabric-loader-0.19.5-26.3"

    val windows = Platform(OperatingSystem.WINDOWS, Architecture.X86_64, "10.0")
    val linux = Platform(OperatingSystem.LINUX, Architecture.X86_64, "6.8.0")
    val macArm = Platform(OperatingSystem.MACOS, Architecture.ARM64, "15.0")

    fun text(id: String): String =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$id.json")) { "Missing fixture $id" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    fun version(id: String): VersionJson = DyroxJson.decodeFromString(VersionJson.serializer(), text(id))

    /** Resolves [id] using only fixtures, like the real resolver would with files on disk. */
    suspend fun resolve(id: String) = VersionResolver { version(it) }.resolve(id)
}
