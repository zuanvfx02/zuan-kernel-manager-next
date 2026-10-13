/*
 * Copyright (c) 2026 ZKM
 * GPL-3.0-or-later
 */
package com.zuan.kernelmanager.next.feature.gamespace

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.zuan.kernelmanager.next.utils.RootPersistenceUtils
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import com.zuan.kernelmanager.next.library.device.usage.UsageStatsHelper
import com.zuan.kernelmanager.next.library.shell.PrivilegedAccess
import com.zuan.kernelmanager.next.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pengawas Game Space: memantau app di depan, dan saat sebuah game terdaftar dibuka ia
 *  1. menerapkan profil (DND, heads-up, brightness, game mode, refresh rate) lewat [GameSpaceEngine],
 *  2. memunculkan panel samping [GameBarService] + crosshair bila diaktifkan,
 * lalu memulihkan semuanya ketika game ditinggalkan.
 */
class GameDetectorService : Service() {

    companion object {
        @Volatile var isRunning = false
            private set
        @Volatile var currentGame: String? = null
            private set

        private const val CHANNEL = "zkm_gamespace"
        private const val NOTIF_ID = 130
        private const val POLL_MS = 1500L
        /** Berapa kali berturut-turut game harus hilang sebelum sesi dianggap selesai (cegah kedip). */
        private const val MISS_LIMIT = 4
        /** Foreground "kosong" (mis. game sedang rotasi / recreate) hanya dihitung setelah selang jauh lebih lama. */
        private const val BLANK_LIMIT = 10

        fun start(c: Context) {
            val i = Intent(c, GameDetectorService::class.java)
            runCatching { ContextCompat.startForegroundService(c, i) }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, GameDetectorService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var misses = 0
    private var blanks = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        RootPersistenceUtils.applyRootExemptions(this)
        isRunning = true
        startForegroundCompat(buildNotification(null))
        // Sisa sesi no-root (game mode / background limiter) dipulihkan bila proses ZKM sempat mati di tengah game.
        scope.launch(Dispatchers.IO) { runCatching { NonRootBoost.restoreStale(applicationContext) } }
        loop = scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(POLL_MS)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning) {
            val restartServiceIntent = Intent(applicationContext, GameDetectorService::class.java).apply {
                setPackage(packageName)
            }
            val restartServicePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        endSession()
        isRunning = false
        super.onDestroy()
    }

    // ---- inti ------------------------------------------------------------------------------

    private fun foregroundPackage(): String {
        val viaUsage = UsageStatsHelper.foregroundPackage(this)
        if (viaUsage.isNotEmpty()) return viaUsage
        // Tanpa Usage access: cadangan dumpsys (hanya bila shell tersedia).
        return if (PrivilegedAccess.shellUsable()) runCatching { MonitorReader.getForegroundPackage() }.getOrDefault("") else ""
    }

    private fun tick() {
        val fg = foregroundPackage()
        val active = currentGame
        when {
            fg.isNotEmpty() && fg != packageName && GameSpacePrefs.isGame(this, fg) -> {
                misses = 0
                blanks = 0
                if (active != fg) beginSession(fg) else GameHubEngine.tick(this, fg)
            }
            active != null && fg.isEmpty() -> {
                // Tidak ada event resume terbaru: umum terjadi saat rotasi layar atau game me-recreate activity
                // (PAUSED -> STOPPED -> RESUMED). Itu BUKAN keluar dari game, jadi toleransinya panjang;
                // kalau tidak, panel yang sedang dibuka ikut dimatikan di tengah rotasi.
                if (++blanks >= BLANK_LIMIT) endSession()
            }
            active != null -> {
                // App lain benar-benar di depan. Membuka ZKM sendiri dari panel juga dianggap keluar dari game.
                blanks = 0
                if (++misses >= MISS_LIMIT) endSession()
            }
        }
    }

    private fun beginSession(pkg: String) {
        if (currentGame != null) endSession()
        currentGame = pkg
        val settings = GameSpacePrefs.settingsFor(this, pkg)
        val canOverlay = Settings.canDrawOverlays(this)
        // Splash Energy Cube: gelapkan layar game lebih dulu, optimasi berjalan di belakangnya (5 dtk).
        val splash = canOverlay && RootBoost.isOn(this, RootBoost.SPLASH) && SplashBus.shouldShow(pkg)
        if (splash) runCatching { EnergyCubeSplashService.start(this) }
        GameSpaceEngine.enter(this, pkg, settings)
        // Terapkan mode CPU terpilih (termasuk isi Custom Mode) + fitur Root Tools; hasilnya jadi teks splash yang jujur.
        // Balance bawaan tidak disentuh (supaya tweak CPU manual pengguna tidak ditimpa); Dynamic/Performance/Custom diterapkan.
        val startMode = CpuMode.fromOrdinal(GameSpacePrefs.cpuMode(this))
        if (startMode != CpuMode.BALANCE || CustomModeStore.get(this, startMode).enabled) {
            runCatching { CpuModeEngine.apply(this, startMode) }
        }
        val report = runCatching { RootBoost.apply(this, pkg) }.getOrNull()
        if (splash) SplashBus.report = report ?: RootBoost.Report(emptyList(), 0, false)
        updateNotification(pkg)
        if (canOverlay) {
            if (settings.showPanel) runCatching { GameBarService.start(this) }
            if (settings.crosshair) runCatching { CrosshairService.start(this) }
            if (GameHubPrefs.pluginOn(this, GameHubEngine.PLUGIN_STOPWATCH)) runCatching { StopwatchService.start(this) }
        }
    }

    private fun endSession() {
        if (currentGame == null && GameSpaceEngine.activePackage == null) return
        currentGame = null
        misses = 0
        blanks = 0
        runCatching { EnergyCubeSplashService.stop(this) }
        runCatching { GameSubOverlayService.stop(this) }
        runCatching { GameBarService.stop(this) }
        runCatching { CrosshairService.stop(this) }
        runCatching { StopwatchService.stop(this) }
        runCatching { GameSpaceEngine.exit(this) }
        updateNotification(null)
    }

    // ---- notifikasi ------------------------------------------------------------------------

    private fun buildNotification(pkg: String?): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.gs_notif_channel), NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val text = if (pkg == null) getString(R.string.gs_notif_idle)
        else getString(R.string.gs_notif_playing, UsageStatsHelper.appLabel(this, pkg))
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.ext_gamespace))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(pkg: String?) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(pkg))
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIF_ID, n)
    }
}
