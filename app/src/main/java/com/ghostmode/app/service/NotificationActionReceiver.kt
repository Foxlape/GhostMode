package com.ghostmode.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghostmode.app.appGraph

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_TURN_OFF && action != ACTION_REAPPLY) return
        val actions = context.appGraph.actions
        val pendingResult = goAsync()
        actions.launch {
            try {
                if (action == ACTION_TURN_OFF) turnOff() else reapply()
            } catch (error: Exception) {
                Log.e(TAG, "Notification action $action failed", error)
            } finally {
                refreshSurfaces()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GhostNotifAction"
        const val ACTION_TURN_OFF = "com.ghostmode.app.notification.TURN_OFF"
        const val ACTION_REAPPLY = "com.ghostmode.app.notification.REAPPLY"
    }
}
