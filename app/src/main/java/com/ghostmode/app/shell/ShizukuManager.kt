package com.ghostmode.app.shell

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.RemoteException
import com.ghostmode.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Owns the connection to Shizuku (or Sui / compatible forks) and a single bound user service.
 * One instance lives for the whole process — see [com.ghostmode.app.AppGraph].
 */
open class ShizukuManager(private val context: Context? = null) {

    protected val statusFlow = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = statusFlow.asStateFlow()

    private val userServiceArgs: Shizuku.UserServiceArgs? = context?.let {
        Shizuku.UserServiceArgs(ComponentName(it.packageName, UserService::class.java.name))
            .tag(USER_SERVICE_TAG)
            .daemon(false)
            .processNameSuffix(PROCESS_NAME_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        // The user service dies together with the Shizuku server and onServiceDisconnected is
        // not guaranteed to be delivered, so forget the connection explicitly.
        resetConnection()
        refresh()
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            if (!binder.pingBinder()) return
            val service = IUserService.Stub.asInterface(binder)
            synchronized(connectionLock) {
                connectedService = service
                connectionAwaiter?.complete(service)
                connectionAwaiter = null
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            resetConnection()
        }
    }

    private val connectionLock = Any()
    private val executionMutex = Mutex()

    @Volatile private var started = false
    @Volatile private var connectedService: IUserService? = null
    @Volatile private var connectionAwaiter: CompletableDeferred<IUserService>? = null
    @Volatile private var isUserServiceBound = false

    fun start() {
        if (started) return
        started = true
        runShizukuCall { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runShizukuCall { Shizuku.addBinderDeadListener(binderDeadListener) }
        runShizukuCall { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
        refresh()
    }

    open fun refresh() {
        statusFlow.value = computeStatus()
    }

    fun requestPermission() {
        runShizukuCall {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    fun openShizukuApp(): Boolean {
        val ctx = context ?: return false
        for (pkg in SHIZUKU_PACKAGES) {
            val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            ctx.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
        return false
    }

    /**
     * Opens the latest Shizuku release on GitHub in the browser. Google Play is not used:
     * it is missing or disabled on many of the phones this app targets.
     */
    fun openShizukuDownload() {
        val ctx = context ?: return
        for (url in listOf(SHIZUKU_RELEASES_URL, SHIZUKU_SITE_URL)) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // No app for this link; try the next one.
            }
        }
    }

    open suspend fun execute(command: String): CommandResult {
        if (status.value != ShizukuStatus.READY) refresh()
        if (status.value != ShizukuStatus.READY) {
            return CommandResult(command, "", ERROR_NOT_READY, EXIT_REMOTE_FAILURE)
        }
        return executionMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    CommandResult.fromJson(command, obtainService().runCommand(command))
                } catch (error: RemoteException) {
                    remoteFailure(command, error)
                } catch (error: SecurityException) {
                    remoteFailure(command, error)
                } catch (error: IllegalStateException) {
                    remoteFailure(command, error)
                }
            }
        }
    }

    private suspend fun obtainService(): IUserService {
        val awaiter: CompletableDeferred<IUserService>
        synchronized(connectionLock) {
            connectedService?.let { service ->
                if (service.asBinder().pingBinder()) return service
                connectedService = null
                isUserServiceBound = false
            }
            // The awaiter must exist before binding: the connection callback may arrive at any time.
            awaiter = connectionAwaiter ?: CompletableDeferred<IUserService>().also { connectionAwaiter = it }
        }
        if (Shizuku.getVersion() < MIN_SHIZUKU_VERSION) throw IllegalStateException(ERROR_UNSUPPORTED_VERSION)
        bindUserServiceIfNeeded()
        return withTimeoutOrNull(BIND_TIMEOUT_MS) { awaiter.await() }
            ?: run {
                synchronized(connectionLock) {
                    if (connectionAwaiter === awaiter) connectionAwaiter = null
                    isUserServiceBound = false
                }
                throw IllegalStateException(ERROR_BIND_TIMEOUT)
            }
    }

    private fun bindUserServiceIfNeeded() {
        val args = userServiceArgs ?: throw IllegalStateException(ERROR_NOT_READY)
        synchronized(connectionLock) {
            if (isUserServiceBound) return
            isUserServiceBound = true
        }
        try {
            Shizuku.bindUserService(args, userServiceConnection)
        } catch (error: RuntimeException) {
            synchronized(connectionLock) { isUserServiceBound = false }
            throw IllegalStateException(error.message ?: ERROR_NOT_READY, error)
        }
    }

    private fun resetConnection() {
        synchronized(connectionLock) {
            connectedService = null
            isUserServiceBound = false
            connectionAwaiter?.completeExceptionally(IllegalStateException(ERROR_SERVICE_DISCONNECTED))
            connectionAwaiter = null
        }
    }

    private fun remoteFailure(command: String, error: Exception): CommandResult {
        refresh()
        return CommandResult(command, "", error.message ?: error.javaClass.simpleName, EXIT_REMOTE_FAILURE)
    }

    private fun runShizukuCall(action: () -> Unit) {
        try {
            action()
        } catch (_: IllegalStateException) {
            refresh()
        } catch (_: RuntimeException) {
            // Shizuku throws plain RuntimeExceptions when the binder is gone.
        }
    }

    private fun computeStatus(): ShizukuStatus {
        if (isBinderAlive()) {
            return if (isPermissionGranted()) ShizukuStatus.READY else ShizukuStatus.NO_PERMISSION
        }
        return if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
    }

    private fun isShizukuInstalled(): Boolean {
        val pm = context?.packageManager ?: return false
        return SHIZUKU_PACKAGES.any { pkg -> pm.getLaunchIntentForPackage(pkg) != null }
    }

    private fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: RuntimeException) {
        false
    }

    private fun isPermissionGranted(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: RuntimeException) {
        false
    }

    companion object {
        const val USER_SERVICE_TAG = "ghost-service"
        const val PROCESS_NAME_SUFFIX = "service"
        const val PERMISSION_REQUEST_CODE = 101
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases/latest"
        const val SHIZUKU_SITE_URL = "https://shizuku.rikka.app/download/"
        val SHIZUKU_PACKAGES = listOf(SHIZUKU_PACKAGE, "rikka.sui")
        const val MIN_SHIZUKU_VERSION = 11
        const val BIND_TIMEOUT_MS = 10_000L

        private const val EXIT_REMOTE_FAILURE = -1
        private const val ERROR_NOT_READY = "Shizuku is not ready"
        private const val ERROR_UNSUPPORTED_VERSION = "Installed Shizuku version is not supported"
        private const val ERROR_SERVICE_DISCONNECTED = "Shizuku user service connection was lost"
        private const val ERROR_BIND_TIMEOUT = "Shizuku user service binding timed out"
    }
}
