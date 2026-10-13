/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import com.zuan.kernelmanager.next.ui.MainActivity
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.battery.BatteryUtils
import com.zuan.kernelmanager.next.utils.RootPersistenceUtils
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import kotlin.math.abs
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BatteryMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "BatteryMonitorChannel"
        const val CHANNEL_NAME = "Battery Monitor Info"
        const val ACTION_STOP = "STOP_MONITOR"
        const val NOTIF_ID = 2
        private const val TAG = "BatteryMonitorService"

        @Volatile var isRunning = false

        /** Dipakai Quick Tools; menyimpan flag yang sama dengan toggle di Battery Controller. */
        fun setEnabled(context: Context, enable: Boolean) {
            context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("monitor_enabled", enable).apply()
            val intent = Intent(context, BatteryMonitorService::class.java)
            if (enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } else {
                intent.action = ACTION_STOP
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    // Root/sysfs reads must not run on the main thread. A single worker also prevents
    // concurrent shell sessions when battery broadcasts arrive in bursts.
    private val statsExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ZKM-BatteryStats").apply { isDaemon = true }
    }
    
    // --- Session Variables ---
    private var sessionStartTime = 0L
    private var startDeepSleep = 0L
    
    // Screen Tracking
    private var isScreenOn = true
    private var lastScreenStateChangeTime = 0L
    private var accumulatedScreenOnTime = 0L
    private var accumulatedScreenOffTime = 0L
    
    // Drain Tracking
    private var lastBatteryLevel = 0
    private var screenOnDrain = 0
    private var screenOffDrain = 0
    
    // Cache String
    private var cachedBigText: String = "Collecting data..."
    private var cachedSummary: String = "Initializing..."
    private var cachedTitle: String = "Battery Monitor"
    
    private var isShellReady = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 3000) 
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val now = SystemClock.elapsedRealtime()
            
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    accumulatedScreenOffTime += (now - lastScreenStateChangeTime)
                    lastScreenStateChangeTime = now
                    isScreenOn = true
                    updateNotification()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    accumulatedScreenOnTime += (now - lastScreenStateChangeTime)
                    lastScreenStateChangeTime = now
                    isScreenOn = false
                    updateNotification()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val currentLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    if (lastBatteryLevel != -1 && currentLevel < lastBatteryLevel) {
                        val drop = lastBatteryLevel - currentLevel
                        if (isScreenOn) {
                            screenOnDrain += drop
                        } else {
                            screenOffDrain += drop
                        }
                    }
                    lastBatteryLevel = currentLevel
                    updateNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        RootPersistenceUtils.applyRootExemptions(this)
        isRunning = true
        createNotificationChannel()
        
        // Reset Stats saat Service dibuat
        sessionStartTime = SystemClock.elapsedRealtime()
        startDeepSleep = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
        lastScreenStateChangeTime = SystemClock.elapsedRealtime()
        
        // Ambil status awal
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        lastBatteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isScreenOn = powerManager.isInteractive
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(receiver, filter)
        
        // Init Shell Background
        com.zuan.kernelmanager.next.core.mode.WorkingModeManager.load(applicationContext)
        Thread {
            try {
                if (com.zuan.kernelmanager.next.core.mode.WorkingModeManager.current.hasRoot && Shell.getShell().isRoot) {
                    isShellReady = true
                    MonitorReader.initializeShell()
                }
            } catch (e: Exception) { isShellReady = false }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences("battery_prefs", MODE_PRIVATE)
                .edit().putBoolean("monitor_enabled", false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        prepareStats()
        
        // Handling Android 14 Foreground Service
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } catch (e: Exception) {
                 startForeground(NOTIF_ID, buildNotification())
            }
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        
        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, 2000L)
        
        return START_STICKY
    }

    private fun updateNotification() {
        statsExecutor.execute {
            // Isi riwayat untuk Charge Stats / Battery Usage (dibatasi 15 dtk di dalam record()).
            runCatching { com.zuan.kernelmanager.next.library.device.battery.BatteryHistory.record(this) }
            prepareStats()
            val notification = buildNotification()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIF_ID, notification)
        }
    }

    private fun prepareStats() {
        try {
            val now = SystemClock.elapsedRealtime()
            val totalSessionTime = (now - sessionStartTime).coerceAtLeast(1)

            val currentSegment = now - lastScreenStateChangeTime
            val totalSOT = if (isScreenOn) accumulatedScreenOnTime + currentSegment else accumulatedScreenOnTime
            val totalScreenOff = if (!isScreenOn) accumulatedScreenOffTime + currentSegment else accumulatedScreenOffTime

            // --- HITUNG WAKTU ---
            val currentGlobalDeepSleep = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
            val sessionDeepSleep = (currentGlobalDeepSleep - startDeepSleep).coerceAtLeast(0)
            val awakeTime = (totalScreenOff - sessionDeepSleep).coerceAtLeast(0)

            // --- HITUNG DRAIN RATE (Safe Division) ---
            val sotHours = totalSOT / 3600000f
            val soffHours = totalScreenOff / 3600000f
            
            // Cegah error Infinity/NaN dengan cek > 0.01 jam
            val activeDrainRate = if (sotHours > 0.01f) (screenOnDrain / sotHours) else 0f
            val idleDrainRate = if (soffHours > 0.01f) (screenOffDrain / soffHours) else 0f

            // --- AMBIL DATA BATERAI LIVE ---
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val temp = tempRaw / 10.0
            val voltageRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0 // mV
            val voltage = voltageRaw / 1000f // Convert ke V
            
            val healthInt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0
            val health = when(healthInt) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                else -> "Normal"
            }

            // Arus & status (tanpa root dulu, root hanya fallback)
            val reading = com.zuan.kernelmanager.next.library.device.battery.BatterySampler.read(this)
            var currentMa = kotlin.math.abs(reading.currentMa).toInt()
            if (currentMa == 0) currentMa = kotlin.math.abs(BatteryUtils.getBatteryCurrentNow())
            val statusWord = com.zuan.kernelmanager.next.library.device.battery.BatterySampler.statusWord(reading)

            // --- FORMAT TAMPILAN (gaya Franco Kernel Manager) ---
            val sotPct = totalSOT * 100f / totalSessionTime
            val sleepPctOfOff = if (totalScreenOff > 0) sessionDeepSleep * 100f / totalScreenOff else 0f
            val awakePctOfOff = if (totalScreenOff > 0) awakeTime * 100f / totalScreenOff else 0f
            val offPct = totalScreenOff * 100f / totalSessionTime

            // Judul: 92% · 36°C · Discharging 717 mA
            cachedTitle = String.format(java.util.Locale.US, "%d%% · %.0f°C · %s %d mA", level, temp, statusWord, currentMa)
            cachedSummary = String.format(
                java.util.Locale.US, "Active drain: %.2f%% /hr · idle drain: %.2f%% /hr",
                activeDrainRate, idleDrainRate
            )
            cachedBigText = listOf(
                cachedSummary,
                String.format(java.util.Locale.US, "Screen on: %s (%.0f%%)", formatDuration(totalSOT), sotPct),
                String.format(java.util.Locale.US, "Screen off: %s (%.0f%%)", formatDuration(totalScreenOff), offPct),
                String.format(java.util.Locale.US, "Deep sleep: %s (%.1f%%)", formatDuration(sessionDeepSleep), sleepPctOfOff),
                String.format(java.util.Locale.US, "Awake: %s (%.1f%%)", formatDuration(awakeTime), awakePctOfOff)
            ).joinToString("\n")

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating stats: ${e.message}")
            cachedSummary = "Calculating..."
            cachedBigText = "Collecting battery data...\nPlease wait."
        }
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val sec = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m ${sec}s"
            m > 0 -> "${m}m ${sec}s"
            else -> "${sec}s"
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_ROUTE", com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.BatteryController.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(cachedTitle) // Judul utama
            .setContentText(cachedSummary) // Teks pendek saat collapsed
            .setStyle(NotificationCompat.BigTextStyle().bigText(cachedBigText)) // Teks panjang saat expanded
            .setSmallIcon(R.drawable.ic_battery_android_frame_full)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows detailed battery statistics"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        statsExecutor.shutdownNow()
        try { unregisterReceiver(receiver) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }
}
