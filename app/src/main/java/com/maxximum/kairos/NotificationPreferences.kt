package com.maxximum.kairos

import android.content.Context

object NotificationPreferences {
    private const val PREFS_NAME = "kairos_prefs"
    private const val KEY_OVERDUE_INTERVAL_HOURS = "overdue_interval_hours"
    const val DEFAULT_INTERVAL_HOURS = 4

    fun getOverdueIntervalHours(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_OVERDUE_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)
    }

    fun setOverdueIntervalHours(context: Context, hours: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OVERDUE_INTERVAL_HOURS, hours)
            .apply()
    }
}
