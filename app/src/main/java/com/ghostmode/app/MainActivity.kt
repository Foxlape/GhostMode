package com.ghostmode.app

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostmode.app.tile.GhostTileService
import com.ghostmode.app.ui.AppHost
import com.ghostmode.app.ui.GhostApp
import com.ghostmode.app.ui.MainViewModel
import com.ghostmode.app.ui.home.startActivitySafely
import com.ghostmode.app.ui.theme.GhostModeTheme

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotificationEnabled(granted)
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        uri?.let(vm::exportPresets)
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::readImport)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val host = AppHost(
            onNotificationToggle = ::onNotificationToggle,
            onAddTile = ::requestAddTile,
            onOpenUrl = { url -> startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            onImport = { importLauncher.launch(arrayOf(MIME_JSON, "text/plain", "application/octet-stream")) },
            onExport = { exportLauncher.launch(EXPORT_FILE_NAME) }
        )
        setContent {
            val dynamicColor by vm.dynamicColor.collectAsStateWithLifecycle()
            GhostModeTheme(dynamicColor = dynamicColor) {
                GhostApp(vm, host)
            }
        }
        // Shortcut intents are handled once; a recreated activity must not repeat the action.
        if (savedInstanceState == null) handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun handleShortcut(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHORTCUT_TURN_ON -> vm.turnOn()
            ACTION_SHORTCUT_TURN_OFF -> vm.turnOff()
            ACTION_SHORTCUT_TIMER_1H -> vm.turnOn(timerMinutes = 60)
            else -> return
        }
        // Consume the action so it is not replayed from the task's base intent.
        intent.action = Intent.ACTION_MAIN
    }

    private fun onNotificationToggle(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        vm.setNotificationEnabled(enabled)
    }

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java)?.requestAddTileService(
            ComponentName(this, GhostTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_ghost),
            mainExecutor
        ) { result -> vm.onTileRequestResult(result) }
    }

    private companion object {
        const val ACTION_SHORTCUT_TURN_ON = "com.ghostmode.app.shortcut.TURN_ON"
        const val ACTION_SHORTCUT_TURN_OFF = "com.ghostmode.app.shortcut.TURN_OFF"
        const val ACTION_SHORTCUT_TIMER_1H = "com.ghostmode.app.shortcut.TIMER_1H"
        const val MIME_JSON = "application/json"
        const val EXPORT_FILE_NAME = "ghostmode-presets.json"
    }
}
