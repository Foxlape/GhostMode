package com.ghostmode.app.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ghostmode.app.R
import java.util.Calendar
import java.util.Date

object Format {

    fun duration(context: Context, ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours >= 24 -> context.getString(R.string.duration_days_hours, hours / 24, hours % 24)
            hours > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
            totalMinutes > 0 -> context.getString(R.string.duration_minutes, minutes)
            else -> context.getString(R.string.duration_less_than_minute)
        }
    }

    /** Wall-clock time honoring the system 12/24-hour setting. */
    fun clock(context: Context, timeMs: Long): String = DateFormat.getTimeFormat(context).format(Date(timeMs))

    fun minuteOfDay(context: Context, minuteOfDay: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
        }
        return clock(context, calendar.timeInMillis)
    }

    fun date(context: Context, timeMs: Long): String = DateFormat.getMediumDateFormat(context).format(Date(timeMs))

    /** Uses the app locale (which may differ from the system one) for day names. */
    fun weekdayShort(context: Context, timeMs: Long): String =
        java.text.SimpleDateFormat("EE", context.resources.configuration.locales[0]).format(Date(timeMs))
}

@Composable
fun rememberDuration(ms: Long): String = Format.duration(LocalContext.current, ms)
