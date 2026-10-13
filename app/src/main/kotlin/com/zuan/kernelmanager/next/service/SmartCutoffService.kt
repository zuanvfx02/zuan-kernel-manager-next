/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.battery.BatteryUtils
import com.zuan.kernelmanager.next.utils.RootPersistenceUtils

class SmartCutoffService : Service() {

    companion object {
        const val CHANNEL_ID = "SmartCutoffChannel"
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_UPDATE_LIMIT = "UPDATE_LIMIT" // Action Baru
        const val EXTRA_LIMIT = "limit_threshold"
        const val EXTRA_RESUME = "resume_threshold"

        const val HYSTERESIS_GAP = 3
    }

    private var limitThreshold: Int = 80
    private var resumeThreshold: Int = 80 - HYSTERESIS_GAP
    private var isCutOffActive = false 
    private var isChargerPlugged = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                
                isChargerPlugged = plugged != 0
                handleBatteryLogic(level, status)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        RootPersistenceUtils.applyRootExemptions(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_STOP_SERVICE -> {
                getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("cutoff_enabled", false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_LIMIT -> {
                // Update limit secara realtime
                val oldLimit = limitThreshold
                limitThreshold = intent.getIntExtra(EXTRA_LIMIT, 80)
                val oldResume = resumeThreshold
                resumeThreshold = intent.getIntExtra(EXTRA_RESUME, limitThreshold - HYSTERESIS_GAP).coerceAtMost(limitThreshold - 1)
                if (oldLimit != limitThreshold || oldResume != resumeThreshold) {
                    updateNotification("Limit updated to $limitThreshold%")
                    // Paksa cek ulang kondisi baterai sekarang juga
                    checkBatteryStateNow()
                }
                // Jangan return, biarkan lanjut agar service tetap foreground
            }
            else -> {
                // Start awal
                limitThreshold = intent.getIntExtra(EXTRA_LIMIT, 80)
                resumeThreshold = intent.getIntExtra(EXTRA_RESUME, limitThreshold - HYSTERESIS_GAP).coerceAtMost(limitThreshold - 1)
            }
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.lp_notification_smart_cutoff))
            .setContentText(getString(R.string.lp_smart_cutoff_monitoring, limitThreshold))
            .setSmallIcon(R.drawable.ic_battery_android_frame_shield)
            .setContentIntent(getOpenAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(1, notification)
        
        // Register receiver hanya jika belum terdaftar (cek null safety jika perlu, 
        // tapi di onStartCommand aman dipanggil berkali-kali asalkan di-handle)
        try {
            unregisterReceiver(batteryReceiver) // Unregister dulu biar gak double
        } catch (e: Exception) { /* Igonore if not registered */ }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        return START_REDELIVER_INTENT
    }

    private fun checkBatteryStateNow() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            handleBatteryLogic(level, status)
        }
    }

    private fun handleBatteryLogic(level: Int, status: Int) {
        if (!isChargerPlugged) {
            if (isCutOffActive) {
                BatteryUtils.setChargingEnabled(true)
                isCutOffActive = false
            }
            return
        }

        // Logic Cut-off
        if (level >= limitThreshold && !isCutOffActive) {
            BatteryUtils.setChargingEnabled(false) 
            isCutOffActive = true
            updateNotification("Charging stopped at $level%")
        } 
        // Logic Resume (Hysteresis)
        else if (level <= resumeThreshold && isCutOffActive) {
            BatteryUtils.setChargingEnabled(true)
            isCutOffActive = false
            updateNotification("Charging resumed at $level%")
        }
    }
    
    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.lp_notification_smart_cutoff))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_battery_android_frame_shield)
            .setContentIntent(getOpenAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val openIntent = Intent(this, com.zuan.kernelmanager.next.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_ROUTE", com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.BatteryController.route)
        }
        return PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartServiceIntent = Intent(applicationContext, SmartCutoffService::class.java).apply {
            setPackage(packageName)
        }
        val restartServicePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            android.util.Log.e("ZKM", "Unhandled operation failure", e)
        }
        BatteryUtils.setChargingEnabled(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Smart Cut-off Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
