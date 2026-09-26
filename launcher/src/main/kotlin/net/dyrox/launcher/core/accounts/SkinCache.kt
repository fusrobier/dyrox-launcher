package net.dyrox.launcher.core.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.io.AtomicFiles
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads and caches skin PNGs for the account list. Only Mojang's texture server is contacted
 * (the URL comes from the Minecraft profile); offline accounts get a generated avatar instead, so no
 * username is ever sent to third-party skin services.
 */
class SkinCache(private val http: HttpService, private val dir: Path) {
    private val memory = ConcurrentHashMap<String, ByteArray>()

    suspend fun skinPng(skinUrl: String): ByteArray? {
        val url = normalize(skinUrl) ?: return null
        memory[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = dir.resolve(Hashing.sha1(url.toByteArray()) + ".png")
            val bytes = if (Files.isRegularFile(file)) {
                Files.readAllBytes(file)
            } else {
                try {
                    http.getBytes(url).also { AtomicFiles.write(file, it) }
                } catch (_: IOException) {
                    return@withContext null
                }
            }
            memory[url] = bytes
            bytes
        }
    }

    private fun normalize(skinUrl: String): String? {
        val uri = runCatching { URI(skinUrl) }.getOrNull() ?: return null
        if (uri.host != TEXTURE_HOST) return null
        // Profiles list http:// URLs; the texture server also serves them over TLS.
        return "https://$TEXTURE_HOST${uri.rawPath}"
    }

    private companion object {
        const val TEXTURE_HOST = "textures.minecraft.net"
    }
}
