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
    private val onSnooze: (Long) -> Unit
) {

    private var windowManager: WindowManager? = null
    private var view: android.view.View? = null

    fun show() {
        val wm = context.getSystemService(WindowManager::class.java)
        windowManager = wm

        val v = LayoutInflater.from(context).inflate(R.layout.overlay_reminder, null)
        v.findViewById<TextView>(R.id.tvHint).text =
            "已连续使用 ${SettingsStore.threshold(context)} 分钟\n建议让眼睛休息一下"

        val restMs = SettingsStore.rest(context) * 60_000L
        v.findViewById<Button>(R.id.btnRest).setOnClickListener {
            dismiss(); onSnooze(restMs)
        }
        v.findViewById<Button>(R.id.btn5).setOnClickListener {
            dismiss(); onSnooze(5 * 60_000L)
        }
        v.findViewById<Button>(R.id.btn10).setOnClickListener {
            dismiss(); onSnooze(10 * 60_000L)
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

        try {
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            view = null
        }
    }

    fun dismiss() {
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
    }
}
