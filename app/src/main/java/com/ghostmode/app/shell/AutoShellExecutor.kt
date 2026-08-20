package com.ghostmode.app.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AutoShellExecutor(
    private val root: RootShellExecutor,
    private val shizuku: ShizukuManager
) : ShellExecutor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val backend: StateFlow<ShellBackend?> =
        combine(root.isRootAvailable, shizuku.status) { isRootAvailable, status ->
            resolveBackend(isRootAvailable, status)
        }.stateIn(scope, SharingStarted.Eagerly, resolveBackend(root.isRootAvailable.value, shizuku.status.value))

    override val readiness: StateFlow<Boolean> =
        backend.map { it != null }
            .stateIn(scope, SharingStarted.Eagerly, resolveBackend(root.isRootAvailable.value, shizuku.status.value) != null)

    suspend fun isReady(): Boolean {
        if (root.isRootAvailable.value || root.probeRoot()) return true
        return shizuku.status.value == ShizukuStatus.READY
    }

    override suspend fun execute(command: String): CommandResult =
        if (root.isRootAvailable.value || root.probeRoot()) {
            root.execute(command)
        } else {
            executeViaShizuku(command)
        }

    private suspend fun executeViaShizuku(command: String): CommandResult =
        try {
            shizuku.execute(command)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            shizukuFailure(command, error.message ?: error.javaClass.simpleName)
        }

    private fun resolveBackend(isRootAvailable: Boolean, status: ShizukuStatus): ShellBackend? =
        when {
            isRootAvailable -> ShellBackend.ROOT
            status == ShizukuStatus.READY -> ShellBackend.SHIZUKU
            else -> null
        }

    private fun shizukuFailure(command: String, reason: String): CommandResult =
        CommandResult(
            command = command,
            stdout = EMPTY_OUTPUT,
            stderr = reason,
            exitCode = EXIT_SHIZUKU_FAILURE
        )

    companion object {
        private const val EXIT_SHIZUKU_FAILURE = -1
        private const val EMPTY_OUTPUT = ""
    }
}
