package com.ghostmode.app.ui

import android.content.Context
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ghostmode.app.R
import com.ghostmode.app.scheduling.ScheduleWindow
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.icu.util.Calendar as IcuCalendar

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

    /** "Every day", "Weekdays", "Weekends" or a list of short day names in the locale's week order. */
    fun scheduleDays(context: Context, days: Int): String = when (days and ScheduleWindow.ALL_DAYS) {
        ScheduleWindow.ALL_DAYS -> context.getString(R.string.schedule_every_day)
        ScheduleWindow.WEEKDAYS -> context.getString(R.string.schedule_weekdays)
        ScheduleWindow.WEEKEND -> context.getString(R.string.schedule_weekends)
        else -> {
            val locale = context.resources.configuration.locales[0]
            val names = DateFormatSymbols.getInstance(locale).shortWeekdays
            ScheduleWindow.week(firstDayOfWeek(locale))
                .filter { days and ScheduleWindow.dayBit(it) != 0 }
                .joinToString(", ") { names[it] }
        }
    }

    /**
     * The in-app language picker sets a bare language (`ru`, `en`) with no region, and
     * `java.util.Calendar` then starts every week on Sunday. Filling in the likely region first
     * gives Monday for `ru` and Sunday for `en`.
     */
    fun firstDayOfWeek(locale: Locale): Int =
        IcuCalendar.getInstance(ULocale.addLikelySubtags(ULocale.forLocale(locale))).firstDayOfWeek

    /** Uses the app locale (which may differ from the system one) for day names. */
    fun weekdayShort(context: Context, timeMs: Long): String =
        java.text.SimpleDateFormat("EE", context.resources.configuration.locales[0]).format(Date(timeMs))
}

@Composable
fun rememberDuration(ms: Long): String = Format.duration(LocalContext.current, ms)
