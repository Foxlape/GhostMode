package com.ghostmode.app.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

open class RootShellExecutor : ShellExecutor {

    protected val isRootAvailableFlow = MutableStateFlow(false)

    val isRootAvailable: StateFlow<Boolean> = isRootAvailableFlow.asStateFlow()

    override val readiness: StateFlow<Boolean> = isRootAvailable

    open suspend fun probeRoot(): Boolean {
        val isAvailable = try {
            withContext(Dispatchers.IO) { probeRootProcess() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        isRootAvailableFlow.value = isAvailable
        return isAvailable
    }

    override suspend fun execute(command: String): CommandResult {
        if (!isRootAvailableFlow.value && !probeRoot()) {
            return rootFailure(command, ERROR_ROOT_UNAVAILABLE)
        }
        return try {
            withContext(Dispatchers.IO) { runRootCommand(command) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            rootFailure(command, error.message ?: error.javaClass.simpleName)
        }
    }

    private suspend fun probeRootProcess(): Boolean = coroutineScope {
        val process = ProcessBuilder(SU_BINARY, SU_FLAG, ROOT_PROBE_COMMAND).start()
        val stderrContent = async(Dispatchers.IO) {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val stdoutContent = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        stderrContent.await()
        exitCode == CommandResult.EXIT_SUCCESS && stdoutContent.contains(ROOT_UID_MARKER)
    }

    private suspend fun runRootCommand(command: String): CommandResult = coroutineScope {
        val process = ProcessBuilder(SU_BINARY, SU_FLAG, command).start()
        val stderrContent = async(Dispatchers.IO) {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val stdoutContent = process.inputStream.bufferedReader().use { it.readText() }
        CommandResult(command, stdoutContent, stderrContent.await(), process.waitFor())
    }

    private fun rootFailure(command: String, reason: String): CommandResult =
        CommandResult(
            command = command,
            stdout = EMPTY_OUTPUT,
            stderr = reason,
            exitCode = EXIT_ROOT_FAILURE
        )

    companion object {
        const val SU_BINARY = "su"
        const val SU_FLAG = "-c"
        const val ROOT_PROBE_COMMAND = "id"
        const val ROOT_UID_MARKER = "uid=0"

        private const val EXIT_ROOT_FAILURE = -1
        private const val EMPTY_OUTPUT = ""
        private const val ERROR_ROOT_UNAVAILABLE = "Root access is not available"
    }
}
