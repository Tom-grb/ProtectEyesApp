package com.example.eyerest

import android.content.Context

object SettingsStore {
    private const val PREFS = "settings"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_REST = "rest"
    private const val KEY_ENABLED = "enabled"

    private fun p(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun threshold(ctx: Context) = p(ctx).getInt(KEY_THRESHOLD, 30)
    fun rest(ctx: Context) = p(ctx).getInt(KEY_REST, 5)
    fun enabled(ctx: Context) = p(ctx).getBoolean(KEY_ENABLED, false)

    fun save(ctx: Context, t: Int, y: Int) {
        p(ctx).edit().putInt(KEY_THRESHOLD, t).putInt(KEY_REST, y).apply()
    }

    fun setEnabled(ctx: Context, e: Boolean) {
        p(ctx).edit().putBoolean(KEY_ENABLED, e).apply()
    }
}
