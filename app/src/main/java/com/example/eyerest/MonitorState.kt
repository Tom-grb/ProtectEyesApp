package com.example.eyerest

object MonitorState {
    @Volatile var isRunning = false
    @Volatile var nextRemindInMs = 0L
    @Volatile var continuousMs = 0L
    @Volatile var todayMs = 0L
}
