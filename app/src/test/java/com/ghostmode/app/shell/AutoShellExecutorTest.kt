package com.ghostmode.app.shell

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRootExecutor(private var available: Boolean = false) : RootShellExecutor() {
    init {
        isRootAvailableFlow.value = available
    }

    override suspend fun probeRoot(force: Boolean): Boolean {
        isRootAvailableFlow.value = available
        return available
    }

    override suspend fun execute(command: String): CommandResult =
        if (available) {
            CommandResult(command, "root_success", "", 0)
        } else {
            CommandResult(command, "", "Root access is not available", -1)
        }
}

class FakeShizukuManager(initialStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED) :
    ShizukuManager(null as Context?) {
    init {
        statusFlow.value = initialStatus
    }

    override suspend fun execute(command: String): CommandResult =
        if (status.value == ShizukuStatus.READY) {
            CommandResult(command, "shizuku_success", "", 0)
        } else {
            CommandResult(command, "", "Shizuku not ready", -1)
        }
}

class AutoShellExecutorTest {

    @Test
    fun isReady_whenRootAvailable_returnsTrue() = runTest {
        val root = FakeRootExecutor(available = true)
        val shizuku = FakeShizukuManager(ShizukuStatus.NOT_INSTALLED)
        val autoExecutor = AutoShellExecutor(root, shizuku)

        assertTrue(autoExecutor.isReady())
        val result = autoExecutor.execute("echo test")
        assertEquals(0, result.exitCode)
        assertEquals("root_success", result.stdout)
    }

    @Test
    fun isReady_whenShizukuReady_returnsTrue() = runTest {
        val root = FakeRootExecutor(available = false)
        val shizuku = FakeShizukuManager(ShizukuStatus.READY)
        val autoExecutor = AutoShellExecutor(root, shizuku)

        assertTrue(autoExecutor.isReady())
        val result = autoExecutor.execute("echo test")
        assertEquals(0, result.exitCode)
        assertEquals("shizuku_success", result.stdout)
    }

    @Test
    fun isReady_whenNeitherAvailable_returnsFalse() = runTest {
        val root = FakeRootExecutor(available = false)
        val shizuku = FakeShizukuManager(ShizukuStatus.NO_PERMISSION)
        val autoExecutor = AutoShellExecutor(root, shizuku)

        assertFalse(autoExecutor.isReady())
    }
}
