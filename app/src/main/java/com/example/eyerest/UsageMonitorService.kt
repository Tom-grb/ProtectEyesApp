package com.example.eyerest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat

class UsageMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var reminderWindow: RestReminderOverlay? = null

    private var accumulatedMs = 0L   // 自上次有效休息以来的连续使用毫秒
    private var snoozeUntil = 0L     // 下次提醒的时间戳；0 = 不在等待期
    private var reminded = false     // 本轮是否已弹出首次提醒
    private var restStartedAt = 0L   // 屏幕熄灭(开始休息)时间；0 = 当前不在休息
    private var lastTick = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (lastTick != 0L) tick(now, now - lastTick)
            lastTick = now
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        lastTick = System.currentTimeMillis()
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(ticker)
        reminderWindow?.dismiss()
        super.onDestroy()
    }

    private fun tick(now: Long, delta: Long) {
        if (isUsingPhone()) {
            // 刚从休息中恢复
            if (restStartedAt != 0L) {
                val rested = now - restStartedAt
                restStartedAt = 0L
                if (rested >= restMs()) {
                    // 有效休息：本轮清零，重新开始累计
                    accumulatedMs = 0L
                    snoozeUntil = 0L
                    reminded = false
                }
            }

            accumulatedMs += delta

            when {
                snoozeUntil != 0L && now >= snoozeUntil -> {
                    snoozeUntil = 0L
                    remind()
                }
                snoozeUntil == 0L && !reminded && accumulatedMs >= thresholdMs() -> {
                    remind()
                    reminded = true
                }
            }
        } else {
            if (restStartedAt == 0L) restStartedAt = now
        }
    }

    private fun remind() {
        reminderWindow?.dismiss()
        reminderWindow = RestReminderOverlay(this) { ms ->
            snoozeUntil = System.currentTimeMillis() + ms
        }.also { it.show() }

        val v = getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600), -1))
    }

    private fun isUsingPhone(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isInteractive
    }

    private fun thresholdMs() = SettingsStore.threshold(this) * 60_000L
    private fun restMs() = SettingsStore.rest(this) * 60_000L

    private fun buildNotification(): Notification {
        val channelId = "monitor"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(channelId, "护眼守护", NotificationManager.IMPORTANCE_LOW)
            )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("护眼守护运行中")
            .setContentText("正在统计连续使用时长")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile var isRunning = false
        private const val NOTIF_ID = 1
        private const val TICK_MS = 2_000L
    }
}
