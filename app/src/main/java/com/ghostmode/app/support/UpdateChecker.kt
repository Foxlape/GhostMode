package com.ghostmode.app.support

import android.util.Log
import com.ghostmode.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val versionName: String,
    val changelog: String?,
    val pageUrl: String,
    val apkUrl: String?
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
}

/** Checks GitHub Releases. Network access happens only when the user enabled it or asked for it. */
class UpdateManager(private val scope: CoroutineScope) {

    private val stateFlow = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = stateFlow.asStateFlow()

    private var job: Job? = null

    fun check() {
        if (job?.isActive == true) return
        job = scope.launch {
            stateFlow.value = UpdateState.Checking
            val release = UpdateChecker.fetchLatestRelease()
            stateFlow.value = when {
                release == null -> UpdateState.Failed
                UpdateChecker.isNewerVersion(release.versionName, BuildConfig.VERSION_NAME) -> UpdateState.Available(release)
                else -> UpdateState.UpToDate
            }
        }
    }

    fun dismiss() {
        stateFlow.value = UpdateState.Idle
    }
}

object UpdateChecker {

    const val REPOSITORY_URL = "https://github.com/Foxlape/GhostMode"
    const val RELEASES_URL = "$REPOSITORY_URL/releases"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/Foxlape/GhostMode/releases/latest"
    private const val TAG = "GhostUpdate"
    private const val TIMEOUT_MS = 10_000

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "GhostMode/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: Exception) {
            Log.w(TAG, "Update check failed", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun parseRelease(payload: String): GitHubRelease? = try {
        val root = JSONObject(payload)
        val version = root.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (version.isEmpty() || root.optBoolean("draft") || root.optBoolean("prerelease")) {
            null
        } else {
            GitHubRelease(
                versionName = version,
                changelog = root.optString("body").trim().ifEmpty { null },
                pageUrl = root.optString("html_url").ifEmpty { RELEASES_URL },
                apkUrl = findApkUrl(root)
            )
        }
    } catch (_: JSONException) {
        null
    }

    /** Compares dotted numeric versions; a pre-release suffix (`0.2.0-beta1`) sorts before the release. */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
        val currentParts = current.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(index) { 0 }
            val c = currentParts.getOrElse(index) { 0 }
            if (r != c) return r > c
        }
        return current.contains('-') && !remote.contains('-')
    }

    private fun findApkUrl(root: JSONObject): String? {
        val assets = root.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").ifEmpty { null }
            }
        }
        return null
    }
}
