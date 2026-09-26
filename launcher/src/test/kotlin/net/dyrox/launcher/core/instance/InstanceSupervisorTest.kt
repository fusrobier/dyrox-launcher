package net.dyrox.launcher.core.instance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.launch.LaunchCommand
import net.dyrox.shared.account.AccountManager
import net.dyrox.shared.account.StoredAccount
import net.dyrox.shared.vault.AccountVault
import net.dyrox.shared.vault.MasterKeyStore
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Runs real child JVMs (FakeGameMain) through the supervisor. */
class InstanceSupervisorTest {
    @TempDir
    lateinit var dir: Path

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: InstanceRepository
    private lateinit var accounts: AccountManager
    private lateinit var alice: StoredAccount
    private lateinit var bob: StoredAccount
    private val supervisors = ArrayList<InstanceSupervisor>()

    private class MemoryKeys : MasterKeyStore {
        private var key: ByteArray? = null
        override val description = "memory"
        override fun load() = key
        override fun save(key: ByteArray) {
            this.key = key
        }
    }

    private fun setUp(): Unit = runBlocking {
        repo = InstanceRepository(LauncherPaths(dir), moveToTrash = { false }).also { it.load() }
        accounts = AccountManager(AccountVault(dir.resolve("accounts.vault"), MemoryKeys()), { null })
        alice = accounts.addOffline("Alice")
        bob = accounts.addOffline("Bob")
    }

    /** Only what FakeGameMain needs, to keep the child command line short. */
    private val childClasspath = System.getProperty("java.class.path").split(File.pathSeparator)
        .filter { "kotlin" in it || "coroutines" in it || "serialization" in it || "${File.separator}build${File.separator}" in it }
        .joinToString(File.pathSeparator)

    private fun supervisor(vararg mode: String): InstanceSupervisor {
        val preparer = LaunchPreparer { instance, identity, _, _ ->
            LaunchCommand(
                executable = Path.of(ProcessHandle.current().info().command().get()),
                arguments = listOf("-cp", childClasspath, "net.dyrox.launcher.core.instance.FakeGameMainKt") +
                    mode.map { it.replace("{account}", accounts.contents.value.accounts.first { a -> a.username == "Bob" }.id) },
                workingDirectory = repo.gameDirectory(instance),
                secrets = listOf(identity.accessToken),
            )
        }
        return InstanceSupervisor(repo, accounts, preparer, scope, staggerMillis = 0).also { supervisors += it }
    }

    @AfterTest
    fun tearDown() {
        supervisors.forEach {
            it.killAll()
            it.close()
        }
        scope.cancel()
    }

    private suspend fun InstanceSession.await(predicate: (InstanceState) -> Boolean): InstanceState =
        withTimeout(30_000) { state.first(predicate) }

    @Test
    fun `game runs, connects over IPC and stops gracefully`(): Unit = runBlocking {
        setUp()
        val instance = repo.create("Main", "26.3", LoaderType.FABRIC)
        val supervisor = supervisor("wait")
        val session = supervisor.launch(instance.id)

        session.await { it == InstanceState.Running }
        withTimeout(10_000) { while (!session.ipcConnected) delay(50) }
        assertNotNull(session.pid)
        assertEquals("Alice", session.account.username, "defaults to the selected account")
        withTimeout(10_000) { while (session.memoryBytes.value == null) delay(100) }
        assertTrue(session.memoryBytes.value!! > 1_000_000, "memory should be sampled")

        supervisor.stop(instance.id)
        assertEquals(InstanceState.Exited(0), session.await { !it.isActive })
        val log = session.log.snapshot().joinToString("\n") { it.message }
        assertTrue("env ipc=true instance=${instance.id}" in log)
        assertTrue("Dyrox client connected" in log)
        // The game folder is free again.
        GameDirLock.acquire(repo.gameDirectory(instance)).close()
    }

    @Test
    fun `non-zero exit after a crash message is reported as a crash`(): Unit = runBlocking {
        setUp()
        val instance = repo.create("Crashy", "26.3", LoaderType.VANILLA)
        val session = supervisor("crash").launch(instance.id)
        val final = session.await { !it.isActive }
        assertIs<InstanceState.Crashed>(final)
        assertEquals(1, final.code)
    }

    @Test
    fun `one account can't play in two instances, and an instance can't run twice`(): Unit = runBlocking {
        setUp()
        val a = repo.create("A", "26.3", LoaderType.FABRIC)
        val b = repo.create("B", "26.3", LoaderType.FABRIC)
        val supervisor = supervisor("wait")
        supervisor.launch(a.id, alice.id).await { it == InstanceState.Running }
        assertFailsWith<AccountInUseException> { supervisor.launch(b.id, alice.id) }
        assertFailsWith<IllegalStateException> { supervisor.launch(a.id, bob.id) }
        val second = supervisor.launch(b.id, bob.id)
        second.await { it == InstanceState.Running }
        assertEquals(2, supervisor.activeSessions.size)
    }

    @Test
    fun `launch with selected accounts starts one instance per account`(): Unit = runBlocking {
        setUp()
        val template = repo.create("PvP", "26.3", LoaderType.FABRIC)
        val supervisor = supervisor("wait")
        val sessions = supervisor.launchWithAccounts(template.id, listOf(alice.id, bob.id))
        sessions.forEach { it.await { state -> state == InstanceState.Running } }

        assertEquals(listOf("PvP", "PvP #2"), sessions.map { it.instance.name })
        assertEquals(listOf("Alice", "Bob"), sessions.map { it.account.username })
        assertNotEquals(sessions[0].gameDirectory, sessions[1].gameDirectory)
        assertNotEquals(sessions[0].pid, sessions[1].pid)

        supervisor.stopAll()
        assertTrue(supervisor.awaitAllStopped(30_000))
    }

    @Test
    fun `kill ends the game immediately`(): Unit = runBlocking {
        setUp()
        val instance = repo.create("Main", "26.3", LoaderType.FABRIC)
        val supervisor = supervisor("wait", "no-ipc")
        val session = supervisor.launch(instance.id)
        session.await { it == InstanceState.Running }
        supervisor.kill(instance.id)
        assertIs<InstanceState.Exited>(session.await { !it.isActive })
    }

    @Test
    fun `the game can switch accounts through the launcher`(): Unit = runBlocking {
        setUp()
        val instance = repo.create("Main", "26.3", LoaderType.FABRIC)
        val supervisor = supervisor("session", "{account}")
        val session = supervisor.launch(instance.id, alice.id)
        withTimeout(30_000) { while (session.log.snapshot().none { it.message == "SESSION Bob" }) delay(50) }
        assertEquals("Bob", session.account.username)
    }
}
