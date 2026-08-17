package com.ghostmode.app.shell

import org.json.JSONException
import org.json.JSONObject

data class CommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    val isSuccess: Boolean get() = exitCode == EXIT_SUCCESS

    companion object {
        const val EXIT_SUCCESS = 0
        const val JSON_KEY_STDOUT = "out"
        const val JSON_KEY_STDERR = "err"
        const val JSON_KEY_EXIT_CODE = "code"
        const val EXIT_PARSE_FAILURE = -1

        fun fromJson(command: String, payload: String): CommandResult {
            return try {
                val root = JSONObject(payload)
                val stdout = root.optString(JSON_KEY_STDOUT, EMPTY_STREAM)
                val stderr = root.optString(JSON_KEY_STDERR, EMPTY_STREAM)
                val exitCode = root.optInt(JSON_KEY_EXIT_CODE, EXIT_PARSE_FAILURE)
                CommandResult(command, stdout, stderr, exitCode)
            } catch (error: JSONException) {
                CommandResult(command, payload, EMPTY_STREAM, EXIT_PARSE_FAILURE)
            }
        }

        private const val EMPTY_STREAM = ""
    }
}
