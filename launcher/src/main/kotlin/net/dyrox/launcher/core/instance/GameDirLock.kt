package net.dyrox.launcher.core.instance

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class GameDirInUseException(message: String) : IOException(message)

/**
 * Exclusive claim on a game directory for as long as a game runs in it.
 *
 * Two layers: an OS file lock (held by this launcher process) and the game's PID written into the
 * lock file. The PID covers the case where the launcher was closed while the game kept running:
 * the OS lock is gone then, but the PID still identifies a live game.
 */
class GameDirLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    /** Records the game's PID once it has started. */
    fun recordPid(pid: Long) {
        channel.truncate(0)
        channel.write(ByteBuffer.wrap(pid.toString().toByteArray(Charsets.US_ASCII)), 0)
        channel.force(false)
    }

    override fun close() {
        runCatching {
            channel.truncate(0)
            lock.release()
        }
        runCatching { channel.close() }
    }

    companion object {
        const val FILE_NAME = ".dyrox-instance.lock"

        fun acquire(gameDir: Path, isAlive: (Long) -> Boolean = ::isProcessAlive): GameDirLock {
            Files.createDirectories(gameDir)
            val file = gameDir.resolve(FILE_NAME)
            val channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                throw GameDirInUseException("Another game is already running in $gameDir")
            }
            val previousPid = readPid(channel)
            if (previousPid != null && previousPid != ProcessHandle.current().pid() && isAlive(previousPid)) {
                lock.release()
                channel.close()
                throw GameDirInUseException("A game started earlier (PID $previousPid) is still running in $gameDir")
            }
            return GameDirLock(channel, lock)
        }

        private fun readPid(channel: FileChannel): Long? {
            if (channel.size() == 0L || channel.size() > 32) return null
            val buffer = ByteBuffer.allocate(channel.size().toInt())
            channel.read(buffer, 0)
            return String(buffer.array(), Charsets.US_ASCII).trim().toLongOrNull()
        }

        /** Only Java processes count, so a recycled PID of some unrelated program doesn't block the folder. */
        fun isProcessAlive(pid: Long): Boolean = ProcessHandle.of(pid)
            .map { handle -> handle.isAlive && handle.info().command().map { "java" in it.lowercase() }.orElse(true) }
            .orElse(false)
    }
}
