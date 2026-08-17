package com.ghostmode.app.shell

import kotlinx.coroutines.flow.StateFlow

enum class ShellBackend { ROOT, SHIZUKU }

interface ShellExecutor {
    val readiness: StateFlow<Boolean>
    suspend fun execute(command: String): CommandResult
}
