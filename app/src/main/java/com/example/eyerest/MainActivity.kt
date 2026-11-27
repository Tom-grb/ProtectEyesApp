package com.example.eyerest

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var tvCountdown: TextView
    private lateinit var tvCountdownLabel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvContinuous: TextView

    private val refresher = object : Runnable {
        override fun run() {
            updateCountdown()
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCountdown = findViewById(R.id.tvCountdown)
        tvCountdownLabel = findViewById(R.id.tvCountdownLabel)
        tvStatus = findViewById(R.id.tvStatus)
        tvToday = findViewById(R.id.tvToday)
        tvContinuous = findViewById(R.id.tvContinuous)

        val etThreshold = findViewById<EditText>(R.id.etThreshold)

        etThreshold.setText(SettingsStore.threshold(this).toString())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val t = etThreshold.text.toString().toIntOrNull() ?: 30
            SettingsStore.saveThreshold(this, t)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnToggle).setOnClickListener { toggleService() }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }

        findViewById<Button>(R.id.btnAutostart).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ))
        }

        findViewById<Button>(R.id.btnNotification).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        uiHandler.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(refresher)
    }

    private fun updatePermissionStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        tvStatus.text = "悬浮窗权限：" +
            if (overlayOk) "已授权" else "未授权（提醒可能无法弹出）"
        tvStatus.setTextColor(
            if (overlayOk) 0xFF34C759.toInt() else 0xFFFF3B30.toInt()
        )
    }

    private fun updateCountdown() {
        if (!MonitorState.isRunning) {
            tvCountdownLabel.text = "守护未开启"
            tvCountdown.text = "--:--"
        } else {
            tvCountdownLabel.text = "距下次休息提醒"
            tvCountdown.text = formatRemaining(MonitorState.nextRemindInMs)
        }
        tvToday.text = "今日使用：" + formatDuration(MonitorState.todayMs)
        tvContinuous.text = "连续使用：" + formatDuration(MonitorState.continuousMs)
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

    private fun formatRemaining(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun toggleService() {
        val intent = Intent(this, UsageMonitorService::class.java)
        if (UsageMonitorService.isRunning) {
            stopService(intent)
            SettingsStore.setEnabled(this, false)
            Toast.makeText(this, "已停止守护", Toast.LENGTH_SHORT).show()
        } else {
            ContextCompat.startForegroundService(this, intent)
            SettingsStore.setEnabled(this, true)
            Toast.makeText(this, "已开始守护", Toast.LENGTH_SHORT).show()
        }
    }
}
