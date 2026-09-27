package com.ghostmode.app.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

open class RootShellExecutor : ShellExecutor {

    protected val isRootAvailableFlow = MutableStateFlow(false)

    val isRootAvailable: StateFlow<Boolean> = isRootAvailableFlow.asStateFlow()

    override val readiness: StateFlow<Boolean> = isRootAvailable

    @Volatile
    private var lastProbeTimeMs = 0L

    /**
     * Checks `su` availability. Positive results are cached briefly; negative results longer,
     * so devices without root do not spawn a failing `su` process for every command and root
     * managers do not show a "denied" toast on each tap.
     */
    open suspend fun probeRoot(force: Boolean = false): Boolean {
        if (!force && isProbeCacheValid()) return isRootAvailableFlow.value
        val isAvailable = try {
            withContext(Dispatchers.IO) { probeRootProcess() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        lastProbeTimeMs = System.currentTimeMillis()
        isRootAvailableFlow.value = isAvailable
        return isAvailable
    }

    override suspend fun awaitReady(timeoutMs: Long): Boolean = probeRoot()

    /** Pins the probe result; used by screenshot tests that render a "root available" state. */
    @androidx.annotation.VisibleForTesting
    internal fun pinAvailabilityForTest(available: Boolean) {
        isRootAvailableFlow.value = available
        lastProbeTimeMs = Long.MAX_VALUE / 2
    }

    override suspend fun execute(command: String): CommandResult {
        if (!probeRoot()) return failure(command, ERROR_ROOT_UNAVAILABLE)
        return try {
            withContext(Dispatchers.IO) { run(listOf(SU_BINARY, SU_FLAG, command), COMMAND_TIMEOUT_MS, command) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure(command, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun isProbeCacheValid(): Boolean {
        val ttl = if (isRootAvailableFlow.value) POSITIVE_CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
        return lastProbeTimeMs != 0L && System.currentTimeMillis() - lastProbeTimeMs < ttl
    }

    private suspend fun probeRootProcess(): Boolean {
        val result = run(listOf(SU_BINARY, SU_FLAG, ROOT_PROBE_COMMAND), PROBE_TIMEOUT_MS, ROOT_PROBE_COMMAND)
        return result.isSuccess && result.stdout.contains(ROOT_UID_MARKER)
    }

    /**
     * Both streams are drained concurrently *before* waiting, otherwise a command that hangs
     * with an open stdout would block forever and the timeout would never apply.
     */
    private suspend fun run(argv: List<String>, timeoutMs: Long, command: String): CommandResult = coroutineScope {
        val process = ProcessBuilder(argv).start()
        process.outputStream.close()
        val stdout = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
        val stderr = async(Dispatchers.IO) { process.errorStream.bufferedReader().use { it.readText() } }
        val exitCode = if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.exitValue()
        } else {
            process.destroyForcibly()
            EXIT_PROCESS_KILLED
        }
        CommandResult(command, stdout.await(), stderr.await(), exitCode)
    }

    private fun failure(command: String, reason: String) =
        CommandResult(command = command, stdout = "", stderr = reason, exitCode = EXIT_ROOT_FAILURE)

    companion object {
        const val SU_BINARY = "su"
        const val SU_FLAG = "-c"
        const val ROOT_PROBE_COMMAND = "id"
        const val ROOT_UID_MARKER = "uid=0"

        const val POSITIVE_CACHE_TTL_MS = 10_000L
        const val NEGATIVE_CACHE_TTL_MS = 60_000L
        const val PROBE_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 20_000L
        const val EXIT_PROCESS_KILLED = -3

        private const val EXIT_ROOT_FAILURE = -1
        private const val ERROR_ROOT_UNAVAILABLE = "Root access is not available"
    }
}
