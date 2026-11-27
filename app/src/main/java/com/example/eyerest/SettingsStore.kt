package com.example.eyerest

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsStore {
    private const val PREFS = "settings"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TODAY_DATE = "today_date"
    private const val KEY_TODAY_MS = "today_ms"

    private fun p(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun threshold(ctx: Context) = p(ctx).getInt(KEY_THRESHOLD, 30)
    fun enabled(ctx: Context) = p(ctx).getBoolean(KEY_ENABLED, false)

    fun saveThreshold(ctx: Context, t: Int) {
        p(ctx).edit().putInt(KEY_THRESHOLD, t).apply()
    }

    fun setEnabled(ctx: Context, e: Boolean) {
        p(ctx).edit().putBoolean(KEY_ENABLED, e).apply()
    }

    // 今日使用时长（毫秒），跨天自动重置
    fun todayMs(ctx: Context): Long {
        val prefs = p(ctx)
        return if (prefs.getString(KEY_TODAY_DATE, null) == todayKey())
            prefs.getLong(KEY_TODAY_MS, 0L) else 0L
    }

    fun saveToday(ctx: Context, ms: Long) {
        val date = todayKey()
        val prefs = p(ctx)
        val e = prefs.edit()
        if (prefs.getString(KEY_TODAY_DATE, null) != date) {
            e.putString(KEY_TODAY_DATE, date)
        }
        e.putLong(KEY_TODAY_MS, ms).apply()
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
}
