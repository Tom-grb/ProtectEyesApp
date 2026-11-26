package com.example.eyerest

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etThreshold = findViewById<EditText>(R.id.etThreshold)
        val etRest = findViewById<EditText>(R.id.etRest)

        etThreshold.setText(SettingsStore.threshold(this).toString())
        etRest.setText(SettingsStore.rest(this).toString())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val t = etThreshold.text.toString().toIntOrNull() ?: 30
            val y = etRest.text.toString().toIntOrNull() ?: 5
            SettingsStore.save(this, t, y)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnToggle).setOnClickListener { toggleService() }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
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
