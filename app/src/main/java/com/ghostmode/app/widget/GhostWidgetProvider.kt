package com.ghostmode.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.ghostmode.app.MainActivity
import com.ghostmode.app.R
import com.ghostmode.app.appGraph

class GhostWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildRemoteViews(context, context.appGraph.state.isOn.value)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return
        val graph = context.appGraph
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        graph.actions.launch {
            try {
                if (graph.shell.awaitReady(READY_TIMEOUT_MS)) {
                    toggle()
                } else {
                    // No root / Shizuku yet: open the app so the user can see what is missing.
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Widget toggle failed", error)
            } finally {
                refreshSurfaces()
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "GhostWidget"
        private const val READY_TIMEOUT_MS = 4_000L
        private const val REQUEST_TOGGLE = 0
        private const val REQUEST_OPEN = 1

        const val ACTION_TOGGLE = "com.ghostmode.app.widget.TOGGLE"

        fun refreshAll(context: Context, isOn: Boolean) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, GhostWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildRemoteViews(context, isOn)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildRemoteViews(context: Context, isOn: Boolean): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_ghost).apply {
                setInt(R.id.widget_root, "setBackgroundResource", if (isOn) R.drawable.widget_bg_on else R.drawable.widget_bg_off)
                setImageViewResource(R.id.widget_icon, R.drawable.ic_ghost)
                setInt(R.id.widget_icon, "setColorFilter", context.getColor(if (isOn) R.color.widget_on_content else R.color.widget_off_content))
                setTextViewText(R.id.widget_label, context.getString(if (isOn) R.string.widget_state_on else R.string.widget_state_off))
                setTextColor(R.id.widget_label, context.getColor(if (isOn) R.color.widget_on_content else R.color.widget_off_content))
                setContentDescription(
                    R.id.widget_root,
                    context.getString(if (isOn) R.string.widget_cd_on else R.string.widget_cd_off)
                )
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getBroadcast(
                        context,
                        REQUEST_TOGGLE,
                        Intent(context, GhostWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                setOnClickPendingIntent(
                    R.id.widget_open,
                    PendingIntent.getActivity(
                        context,
                        REQUEST_OPEN,
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
    }
}
