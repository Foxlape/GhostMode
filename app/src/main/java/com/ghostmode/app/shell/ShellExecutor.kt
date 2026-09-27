package com.ghostmode.app.shell

import kotlinx.coroutines.flow.StateFlow

enum class ShellBackend { ROOT, SHIZUKU }

interface ShellExecutor {
    /** `true` while commands can be executed right now. */
    val readiness: StateFlow<Boolean>

    /**
     * Waits until a backend becomes available. Shizuku delivers its binder asynchronously after
     * process start, so callers woken up by alarms, tiles or widgets must wait for it.
     */
    suspend fun awaitReady(timeoutMs: Long): Boolean

    suspend fun execute(command: String): CommandResult
}
