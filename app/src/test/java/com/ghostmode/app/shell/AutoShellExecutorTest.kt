package com.ghostmode.app.shell

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRootExecutor(private val available: Boolean = false) : RootShellExecutor() {
    override suspend fun probeRoot(force: Boolean): Boolean {
        isRootAvailableFlow.value = available
        return available
    }

    override suspend fun execute(command: String): CommandResult =
        if (available) CommandResult(command, "root", "", 0) else CommandResult(command, "", "no root", -1)
}

class FakeShizukuManager(initialStatus: ShizukuStatus) : ShizukuManager(null as Context?) {
    init {
        statusFlow.value = initialStatus
    }

    fun setStatus(status: ShizukuStatus) {
        statusFlow.value = status
    }

    override fun refresh() = Unit

    override suspend fun execute(command: String): CommandResult =
        if (status.value == ShizukuStatus.READY) CommandResult(command, "shizuku", "", 0)
        else CommandResult(command, "", "not ready", -1)
}

class AutoShellExecutorTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun root_isPreferredOverShizuku() = runTest {
        val executor = AutoShellExecutor(FakeRootExecutor(available = true), FakeShizukuManager(ShizukuStatus.READY), scope)

        assertTrue(executor.awaitReady(0))
        assertEquals("root", executor.execute("id").stdout)
    }

    @Test
    fun shizuku_isUsedWithoutRoot() = runTest {
        val executor = AutoShellExecutor(FakeRootExecutor(), FakeShizukuManager(ShizukuStatus.READY), scope)

        assertTrue(executor.awaitReady(0))
        assertEquals("shizuku", executor.execute("id").stdout)
        assertEquals(ShellBackend.SHIZUKU, executor.backend.value)
    }

    @Test
    fun awaitReady_waitsForShizukuBinder() = runTest {
        val shizuku = FakeShizukuManager(ShizukuStatus.NOT_RUNNING)
        val executor = AutoShellExecutor(FakeRootExecutor(), shizuku, scope)

        val ready = async { executor.awaitReady(5_000) }
        yield()
        shizuku.setStatus(ShizukuStatus.READY)

        assertTrue(ready.await())
    }

    @Test
    fun awaitReady_timesOutWithoutBackend() = runTest {
        val executor = AutoShellExecutor(FakeRootExecutor(), FakeShizukuManager(ShizukuStatus.NO_PERMISSION), scope)

        assertFalse(executor.awaitReady(1_000))
        assertEquals(null, executor.backend.value)
    }
}
