package com.ghostmode.app.support

import android.util.Log
import com.ghostmode.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val tagName: String,
    val versionName: String,
    val changelog: String?,
    val apkUrl: String
)

object UpdateChecker {

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty(HEADER_ACCEPT, HEADER_ACCEPT_VALUE)
            connection.setRequestProperty(HEADER_USER_AGENT, HEADER_USER_AGENT_VALUE)
            val payload = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            parseRelease(payload)
        } catch (error: Exception) {
            Log.w(TAG, "Update check failed", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseRelease(payload: String): GitHubRelease? = try {
        val root = JSONObject(payload)
        val tagName = root.optString(KEY_TAG_NAME).removePrefix(VERSION_PREFIX).removePrefix(VERSION_PREFIX_UPPER)
        val changelog = root.optString(KEY_BODY).ifEmpty { null }
        val apkUrl = findApkDownloadUrl(root)
        if (tagName.isEmpty() || apkUrl.isNullOrEmpty()) {
            null
        } else {
            GitHubRelease(tagName, tagName, changelog, apkUrl)
        }
    } catch (_: JSONException) {
        null
    }

    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = remoteVersion.split(".").map { part -> part.trim().toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { part -> part.trim().toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) return remotePart > currentPart
        }
        return false
    }

    private fun findApkDownloadUrl(root: JSONObject): String? {
        val assets = root.optJSONArray(KEY_ASSETS) ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString(KEY_ASSET_NAME).endsWith(APK_SUFFIX, ignoreCase = true)) {
                return asset.optString(KEY_DOWNLOAD_URL).ifEmpty { null }
            }
        }
        return null
    }

    private const val TAG = "GhostUpdate"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Foxlape/GhostMode/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val HEADER_ACCEPT = "Accept"
    private const val HEADER_ACCEPT_VALUE = "application/vnd.github+json"
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val HEADER_USER_AGENT_VALUE = "GhostMode-App"
    private const val KEY_TAG_NAME = "tag_name"
    private const val KEY_BODY = "body"
    private const val KEY_ASSETS = "assets"
    private const val KEY_ASSET_NAME = "name"
    private const val KEY_DOWNLOAD_URL = "browser_download_url"
    private const val VERSION_PREFIX = "v"
    private const val VERSION_PREFIX_UPPER = "V"
    private const val APK_SUFFIX = ".apk"
}

object UpdateManager {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val availableUpdateFlow = MutableStateFlow<GitHubRelease?>(null)
    val availableUpdate: StateFlow<GitHubRelease?> = availableUpdateFlow.asStateFlow()

    fun refresh() {
        backgroundScope.launch {
            availableUpdateFlow.value = findAvailableUpdate(BuildConfig.VERSION_NAME)
        }
    }

    fun dismiss() {
        availableUpdateFlow.value = null
    }

    internal suspend fun findAvailableUpdate(currentVersionName: String): GitHubRelease? {
        val release = runCatching { UpdateChecker.fetchLatestRelease() }.getOrNull() ?: return null
        return if (UpdateChecker.isNewerVersion(release.versionName, currentVersionName)) release else null
    }
}
