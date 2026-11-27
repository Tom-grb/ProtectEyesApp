package com.example.eyerest

import android.app.AlarmManager
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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ServiceCompat

class UsageMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var reminderWindow: RestReminderOverlay? = null

    private var continuousMs = 0L  // 连续使用时长，点“知道了”或熄屏后重新亮屏时清零
    private var todayMs = 0L       // 今日使用总时长（自己统计）
    private var reminding = false  // 提醒弹窗是否正在显示
    private var wasInteractive = false
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
        MonitorState.isRunning = true
        todayMs = SettingsStore.todayMs(this)
        MonitorState.todayMs = todayMs
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        lastTick = System.currentTimeMillis()
        wasInteractive = isUsingPhone()
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK) {
            // AlarmManager 兜底唤醒：若 Handler 被系统冻结，补一次计时
            val now = System.currentTimeMillis()
            if (lastTick != 0L && now - lastTick >= TICK_MS * 2) {
                tick(now, now - lastTick)
                lastTick = now
            }
        }
        scheduleNextCheck()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        MonitorState.isRunning = false
        MonitorState.nextRemindInMs = 0L
        MonitorState.continuousMs = 0L
        handler.removeCallbacks(ticker)
        reminderWindow?.dismiss()
        cancelCheck()
        SettingsStore.saveToday(this, todayMs)
        super.onDestroy()
    }

    private fun tick(now: Long, delta: Long) {
        val interactive = isUsingPhone()
        if (interactive) {
            // 熄屏后重新亮屏：重新开始计时
            if (!wasInteractive) continuousMs = 0L
            continuousMs += delta
            todayMs += delta

            if (!reminding && continuousMs >= thresholdMs()) {
                reminding = true
                remind()
            }
        }
        wasInteractive = interactive

        MonitorState.continuousMs = continuousMs
        MonitorState.todayMs = todayMs
        MonitorState.nextRemindInMs = (thresholdMs() - continuousMs).coerceAtLeast(0L)

        // 定期持久化今日时长
        if (now - lastSavedAt >= SAVE_INTERVAL_MS) {
            lastSavedAt = now
            SettingsStore.saveToday(this, todayMs)
        }
    }

    private fun remind() {
        reminderWindow?.dismiss()
        val overlay = RestReminderOverlay(
            this, continuousMs, todayMs
        ) {
            // 点击“知道了”：重新开始计时
            continuousMs = 0L
            reminding = false
        }
        reminderWindow = overlay
        val shown = overlay.show()

        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600), -1)
        )

        if (!shown) {
            sendFallbackNotification()
            reminding = false
        }
    }

    private fun sendFallbackNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = "reminder"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "休息提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                enableLights(true)
            }
        )
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, channelId)
            .setContentTitle("该休息一下了")
            .setContentText("已连续使用 ${SettingsStore.threshold(this)} 分钟，请让眼睛休息一下")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()
        nm.notify(2, notif)
    }

    // 每 60 秒用 AlarmManager 兜底唤醒一次，对抗 OPPO/一加后台冻结 Handler 的问题
    private fun scheduleNextCheck() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, UsageMonitorService::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS,
            pi
        )
    }

    private fun cancelCheck() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, UsageMonitorService::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pi?.let { am.cancel(it) }
    }

    private fun isUsingPhone(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isInteractive
    }

    private fun thresholdMs() = SettingsStore.threshold(this) * 60_000L

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
        private var lastSavedAt = 0L
        private const val NOTIF_ID = 1
        private const val TICK_MS = 2_000L
        private const val SAVE_INTERVAL_MS = 60_000L
        private const val CHECK_INTERVAL_MS = 60_000L
        private const val ACTION_CHECK = "com.example.eyerest.ACTION_CHECK"
    }
}
