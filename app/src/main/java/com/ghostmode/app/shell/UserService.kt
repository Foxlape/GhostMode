package com.ghostmode.app.shell

import android.content.Context
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class UserService : IUserService.Stub {
    constructor()

    @Keep
    constructor(context: Context)

    override fun runCommand(command: String): String {
        return try {
            val process = ProcessBuilder(SHELL_COMMAND, SHELL_ARGUMENT, command).start()
            val stderrReading = readStreamAsync(process.errorStream)
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            successJson(stdout, stderrReading.get(), process.waitFor())
        } catch (error: Exception) {
            failureJson(error)
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun readStreamAsync(stream: InputStream): Future<String> =
        STREAM_READERS.submit(
            Callable { stream.bufferedReader().use { it.readText() } }
        )

    private fun successJson(stdout: String, stderr: String, exitCode: Int): String =
        JSONObject()
            .put(CommandResult.JSON_KEY_STDOUT, stdout)
            .put(CommandResult.JSON_KEY_STDERR, stderr)
            .put(CommandResult.JSON_KEY_EXIT_CODE, exitCode)
            .toString()

    private fun failureJson(error: Exception): String =
        JSONObject()
            .put(CommandResult.JSON_KEY_STDOUT, EMPTY_STREAM)
            .put(CommandResult.JSON_KEY_STDERR, error.message ?: error.javaClass.simpleName)
            .put(CommandResult.JSON_KEY_EXIT_CODE, CommandResult.EXIT_PARSE_FAILURE)
            .toString()

    companion object {
        const val SHELL_COMMAND = "sh"
        const val SHELL_ARGUMENT = "-c"

        private const val EMPTY_STREAM = ""

        private val STREAM_READERS: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
    }
}
