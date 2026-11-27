package com.example.eyerest

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class RestReminderOverlay(
    private val context: Context,
    private val continuousMs: Long,
    private val todayMs: Long,
    private val onDismiss: () -> Unit
) {

    private var windowManager: WindowManager? = null
    private var view: android.view.View? = null

    fun show(): Boolean {
        val wm = context.getSystemService(WindowManager::class.java)
        windowManager = wm

        val v = LayoutInflater.from(context).inflate(R.layout.overlay_reminder, null)
        v.findViewById<TextView>(R.id.tvContinuous).text =
            "连续使用手机：${formatDuration(continuousMs)}"
        v.findViewById<TextView>(R.id.tvToday).text =
            "今日使用手机：${formatDuration(todayMs)}"

        v.findViewById<Button>(R.id.btnOk).setOnClickListener {
            dismiss(); onDismiss()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        return try {
            wm.addView(v, params)
            view = v
            true
        } catch (e: Exception) {
            view = null
            false
        }
    }

    fun dismiss() {
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
    }

    private fun formatDuration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}小时${m}分钟"
            m > 0 -> "${m}分钟"
            else -> "不足1分钟"
        }
    }
}
