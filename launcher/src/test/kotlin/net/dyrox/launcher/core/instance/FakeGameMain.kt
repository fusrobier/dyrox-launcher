package net.dyrox.launcher.core.instance

import kotlinx.coroutines.runBlocking
import net.dyrox.shared.ipc.IpcClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Stand-in for Minecraft in supervisor tests: prints the lines the supervisor watches for, talks to
 * the launcher over IPC like the Dyrox client will, then behaves according to `args[0]`.
 */
fun main(args: Array<String>) {
    println("[12:00:00] [Render thread/INFO]: Setting user: fake")
    println("[12:00:00] [Render thread/INFO]: env ipc=${System.getenv("DYROX_IPC_PORT") != null} instance=${System.getenv("DYROX_INSTANCE_ID")}")
    val client = if ("no-ipc" in args) null else IpcClient.fromEnvironment("fake-client")
    val quit = CountDownLatch(1)
    client?.onShutdown = { quit.countDown() }
    println("[12:00:01] [Render thread/INFO]: Sound engine started")
    System.out.flush()

    when (args.firstOrNull()) {
        "crash" -> {
            println("[12:00:02] [Render thread/FATAL]: Minecraft has crashed!")
            System.out.flush()
            exitProcess(1)
        }
        "session" -> {
            val session = runBlocking { client!!.requestSession(args[1]) }
            println("SESSION ${session.username}")
            System.out.flush()
            quit.await(30, TimeUnit.SECONDS)
            exitProcess(0)
        }
        else -> {
            // "wait": run until asked to quit (or killed).
            quit.await(30, TimeUnit.SECONDS)
            println("[12:00:03] [Render thread/INFO]: Stopping!")
            System.out.flush()
            exitProcess(0)
        }
    }
}
