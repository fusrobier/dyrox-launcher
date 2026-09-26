package net.dyrox.launcher.core.instance

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import net.dyrox.shared.platform.OperatingSystem
import java.nio.file.Files
import java.nio.file.Path

/** Resident memory of a process, for the Running dashboard. */
object ProcessMetrics {
    /** Working set (Windows) or VmRSS (Linux) in bytes; null if unknown. */
    fun residentBytes(pid: Long, os: OperatingSystem = OperatingSystem.current): Long? = runCatching {
        when (os) {
            OperatingSystem.WINDOWS -> windows(pid)
            OperatingSystem.LINUX -> linux(pid)
            else -> null
        }
    }.getOrNull()

    private fun linux(pid: Long): Long? {
        val status = Path.of("/proc", pid.toString(), "status")
        if (!Files.isReadable(status)) return null
        val line = Files.readAllLines(status).firstOrNull { it.startsWith("VmRSS:") } ?: return null
        return line.removePrefix("VmRSS:").trim().substringBefore(' ').toLongOrNull()?.times(1024)
    }

    /** jna-platform 5.17 doesn't bind GetProcessMemoryInfo, so it's declared here. */
    private interface Psapi : StdCallLibrary {
        fun GetProcessMemoryInfo(process: WinNT.HANDLE, counters: ProcessMemoryCounters, size: Int): Boolean
    }

    /** `PROCESS_MEMORY_COUNTERS` from psapi.h. */
    @Structure.FieldOrder(
        "cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize", "QuotaPeakPagedPoolUsage",
        "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage",
    )
    class ProcessMemoryCounters : Structure() {
        @JvmField var cb: Int = 0
        @JvmField var PageFaultCount: Int = 0
        @JvmField var PeakWorkingSetSize: SIZE_T = SIZE_T()
        @JvmField var WorkingSetSize: SIZE_T = SIZE_T()
        @JvmField var QuotaPeakPagedPoolUsage: SIZE_T = SIZE_T()
        @JvmField var QuotaPagedPoolUsage: SIZE_T = SIZE_T()
        @JvmField var QuotaPeakNonPagedPoolUsage: SIZE_T = SIZE_T()
        @JvmField var QuotaNonPagedPoolUsage: SIZE_T = SIZE_T()
        @JvmField var PagefileUsage: SIZE_T = SIZE_T()
        @JvmField var PeakPagefileUsage: SIZE_T = SIZE_T()
    }

    private val psapi: Psapi by lazy { Native.load("psapi", Psapi::class.java, W32APIOptions.DEFAULT_OPTIONS) }

    private fun windows(pid: Long): Long? {
        val handle = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION or PROCESS_VM_READ, false, pid.toInt())
            ?: return null
        try {
            val counters = ProcessMemoryCounters()
            counters.cb = counters.size()
            return if (psapi.GetProcessMemoryInfo(handle, counters, counters.size())) counters.WorkingSetSize.toLong() else null
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }

    private const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    private const val PROCESS_VM_READ = 0x0010
}
