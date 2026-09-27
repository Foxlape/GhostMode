package com.ghostmode.app.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/** Prefers root when it is granted, otherwise falls back to Shizuku (or Sui). */
class AutoShellExecutor(
    private val root: RootShellExecutor,
    private val shizuku: ShizukuManager,
    scope: CoroutineScope
) : ShellExecutor {

    val backend: StateFlow<ShellBackend?> =
        combine(root.isRootAvailable, shizuku.status) { isRoot, status -> resolveBackend(isRoot, status) }
            .stateIn(scope, SharingStarted.Eagerly, resolveBackend(root.isRootAvailable.value, shizuku.status.value))

    override val readiness: StateFlow<Boolean> =
        backend.map { it != null }.stateIn(scope, SharingStarted.Eagerly, backend.value != null)

    override suspend fun awaitReady(timeoutMs: Long): Boolean {
        if (root.probeRoot()) return true
        shizuku.refresh()
        if (shizuku.status.value == ShizukuStatus.READY) return true
        return withTimeoutOrNull(timeoutMs) {
            shizuku.status.first { it == ShizukuStatus.READY }
        } != null
    }

    override suspend fun execute(command: String): CommandResult =
        if (root.probeRoot()) root.execute(command) else executeViaShizuku(command)

    private suspend fun executeViaShizuku(command: String): CommandResult =
        try {
            shizuku.execute(command)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommandResult(command, "", error.message ?: error.javaClass.simpleName, EXIT_SHIZUKU_FAILURE)
        }

    private fun resolveBackend(isRootAvailable: Boolean, status: ShizukuStatus): ShellBackend? = when {
        isRootAvailable -> ShellBackend.ROOT
        status == ShizukuStatus.READY -> ShellBackend.SHIZUKU
        else -> null
    }

    private companion object {
        const val EXIT_SHIZUKU_FAILURE = -1
    }
}
