package com.ghostmode.app.shell

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import com.ghostmode.app.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

class ShizukuManager(private val context: Context) : ShellExecutor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val statusFlow = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = statusFlow.asStateFlow()

    override val readiness: StateFlow<Boolean> = status
        .map { it == ShizukuStatus.READY }
        .stateIn(scope, SharingStarted.Eagerly, INITIAL_READINESS)

    private val userServiceArgs: Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, UserService::class.java.name)
    )
        .tag(USER_SERVICE_TAG)
        .daemon(false)
        .processNameSuffix(PROCESS_NAME_SUFFIX)
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refreshStatus() }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) refreshStatus()
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
            if (!binder.pingBinder()) return
            val service = IUserService.Stub.asInterface(binder)
            connectedService = service
            connectionAwaiter?.complete(service)
            connectionAwaiter = null
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            connectedService = null
            connectionAwaiter?.completeExceptionally(IllegalStateException(ERROR_SERVICE_DISCONNECTED))
            connectionAwaiter = null
        }
    }

    private val connectionMutex = Mutex()

    @Volatile
    private var started = false

    @Volatile
    private var connectedService: IUserService? = null

    @Volatile
    private var connectionAwaiter: CompletableDeferred<IUserService>? = null

    @Volatile
    private var isUserServiceBound = false

    fun start() {
        if (started) return
        started = true
        runShizukuCall { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runShizukuCall { Shizuku.addBinderDeadListener(binderDeadListener) }
        runShizukuCall { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
        refreshStatus()
    }

    fun stop() {
        if (!started) return
        started = false
        runShizukuCall { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
        runShizukuCall { Shizuku.removeBinderDeadListener(binderDeadListener) }
        runShizukuCall { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
        unbindUserServiceIfNeeded()
        resetConnectionState()
    }

    fun requestPermission() {
        runShizukuCall { requestPermissionIfEligible() }
    }

    fun openShizukuApp() {
        for (pkg in SHIZUKU_PACKAGES) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        }
    }

    fun openShizukuDownload() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    override suspend fun execute(command: String): CommandResult {
        ensureReadyForExecution()
        return withContext(Dispatchers.IO) { runRemoteCommand(command) }
    }

    suspend fun execute(commands: List<String>): List<CommandResult> {
        return commands.map { command -> execute(command) }
    }

    private fun ensureReadyForExecution() {
        if (status.value != ShizukuStatus.READY) {
            throw IllegalStateException(ERROR_NOT_READY)
        }
    }

    private suspend fun runRemoteCommand(command: String): CommandResult {
        return try {
            CommandResult.fromJson(command, obtainConnectedService().runCommand(command))
        } catch (error: RemoteException) {
            remoteFailureResult(command, error)
        } catch (error: SecurityException) {
            remoteFailureResult(command, error)
        } catch (error: IllegalStateException) {
            remoteFailureResult(command, error)
        }
    }

    private suspend fun obtainConnectedService(): IUserService = connectionMutex.withLock {
        connectedService?.let { return it }
        val awaiter = CompletableDeferred<IUserService>()
        connectionAwaiter = awaiter
        try {
            ensureSupportedShizukuVersion()
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (error: IllegalStateException) {
            connectionAwaiter = null
            throw error
        }
        isUserServiceBound = true
        awaiter.await()
    }

    private fun ensureSupportedShizukuVersion() {
        if (Shizuku.getVersion() < MIN_SHIZUKU_VERSION) {
            throw IllegalStateException(ERROR_UNSUPPORTED_VERSION)
        }
    }

    private fun remoteFailureResult(command: String, error: Exception): CommandResult {
        refreshStatus()
        return CommandResult(
            command = command,
            stdout = EMPTY_STDOUT,
            stderr = error.message ?: error.javaClass.simpleName,
            exitCode = EXIT_REMOTE_FAILURE
        )
    }

    private fun requestPermissionIfEligible() {
        if (Shizuku.isPreV11()) return
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private fun unbindUserServiceIfNeeded() {
        if (!isUserServiceBound) return
        runShizukuCall { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) }
        isUserServiceBound = false
    }

    private fun resetConnectionState() {
        connectedService = null
        connectionAwaiter?.completeExceptionally(IllegalStateException(ERROR_SERVICE_DISCONNECTED))
        connectionAwaiter = null
    }

    private fun runShizukuCall(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalStateException) {
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        statusFlow.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus {
        if (isBinderAlive()) {
            return if (isPermissionGranted()) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.NO_PERMISSION
            }
        }
        return if (isShizukuInstalled()) {
            ShizukuStatus.NOT_RUNNING
        } else {
            ShizukuStatus.NOT_INSTALLED
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return SHIZUKU_PACKAGES.any { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }
    }

    private fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun isPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: IllegalStateException) {
            false
        }
    }

    companion object {
        const val USER_SERVICE_TAG = "ghost-service"
        const val PROCESS_NAME_SUFFIX = "service"
        const val PERMISSION_REQUEST_CODE = 101
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        val SHIZUKU_PACKAGES = listOf(
            "moe.shizuku.privileged.api",
            "io.github.nightzuku",
            "com.nightzuku",
            "moe.nightzuku"
        )
        const val MIN_SHIZUKU_VERSION = 11

        private const val INITIAL_READINESS = false

        private const val EXIT_REMOTE_FAILURE = -1
        private const val EMPTY_STDOUT = ""
        private const val ERROR_NOT_READY = "Shizuku is not ready for command execution"
        private const val ERROR_UNSUPPORTED_VERSION = "Installed Shizuku version is not supported"
        private const val ERROR_SERVICE_DISCONNECTED = "Shizuku user service connection was lost"
    }
}
