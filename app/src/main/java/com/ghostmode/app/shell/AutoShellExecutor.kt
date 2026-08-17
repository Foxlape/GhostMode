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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val backend: StateFlow<ShellBackend?> =
        combine(root.isRootAvailable, shizuku.status) { isRootAvailable, status ->
            when {
                isRootAvailable -> ShellBackend.ROOT
                status == ShizukuStatus.READY -> ShellBackend.SHIZUKU
                else -> null
            }
        }.stateIn(scope, SharingStarted.Eagerly, INITIAL_BACKEND)

    override val readiness: StateFlow<Boolean> =
        backend.map { it != null }.stateIn(scope, SharingStarted.Eagerly, INITIAL_READINESS)

    override suspend fun execute(command: String): CommandResult =
        if (root.isRootAvailable.value) {
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

    private fun shizukuFailure(command: String, reason: String): CommandResult =
        CommandResult(
            command = command,
            stdout = EMPTY_OUTPUT,
            stderr = reason,
            exitCode = EXIT_SHIZUKU_FAILURE
        )

    companion object {
        private val INITIAL_BACKEND: ShellBackend? = null
        private const val INITIAL_READINESS = false
        private const val EXIT_SHIZUKU_FAILURE = -1
        private const val EMPTY_OUTPUT = ""
    }
}
