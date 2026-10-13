/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.zuan.kernelmanager.next.feature.process

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.zuan.kernelmanager.next.utils.RootPersistenceUtils
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.MainActivity
import com.zuan.kernelmanager.next.feature.overlay.overlayDrag
import com.zuan.kernelmanager.next.library.device.process.ProcessFilter
import com.zuan.kernelmanager.next.library.device.process.ProcessUtils
import com.zuan.kernelmanager.next.library.device.process.SortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max

class FloatingProcessService : Service() {

    // [PENTING] Menambahkan state global agar UI tahu service sedang jalan atau tidak
    companion object {
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    
    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "floating_monitor_channel"

    // Simpan ukuran saat ini agar bisa di-update
    private var currentWidth = 0
    private var currentHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        RootPersistenceUtils.applyRootExemptions(this)
        isRunning = true // Set status aktif
        startForegroundServiceNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Ukuran awal ringkas seperti monitor referensi (Width 200dp, Height 290dp)
        val metrics = resources.displayMetrics
        currentWidth = (200 * metrics.density).toInt()
        currentHeight = (290 * metrics.density).toInt()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            currentWidth, // Gunakan ukuran fix
            currentHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        floatingView = ComposeView(this).apply {
            val lifecycleOwner = MyLifecycleOwner()
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            val viewModelStore = ViewModelStore()
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = viewModelStore
            })
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            
            setContent {
                FloatingScreenContent(
                    onClose = { stopSelf() },
                    onDrag = { x, y -> updateWindowPosition(x, y) },
                    onResize = { dx, dy -> updateWindowSize(dx, dy) },
                    onCollapseChanged = { collapsed -> updateCollapsed(collapsed) }
                )
            }
        }

        windowManager.addView(floatingView, params)
    }
    
    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Process Monitor Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_ROUTE", com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.ProcessManager.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fps_notification_title))
            .setContentText(getString(R.string.fps_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateWindowPosition(deltaX: Float, deltaY: Float) {
        params.x += deltaX.toInt()
        params.y += deltaY.toInt()
        try { windowManager.updateViewLayout(floatingView, params) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
    }

    /** Collapse = jendela menyusut jadi header saja; expand = kembali ke tinggi terakhir. */
    private fun updateCollapsed(collapsed: Boolean) {
        params.height = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else currentHeight
        try { windowManager.updateViewLayout(floatingView, params) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
    }

    private fun updateWindowSize(deltaX: Float, deltaY: Float) {
        // Update size dengan batas minimal
        val metrics = resources.displayMetrics
        val minWidth = (160 * metrics.density).toInt()
        val minHeight = (120 * metrics.density).toInt()

        currentWidth = max(minWidth, currentWidth + deltaX.toInt())
        currentHeight = max(minHeight, currentHeight + deltaY.toInt())

        params.width = currentWidth
        params.height = currentHeight
        
        try { windowManager.updateViewLayout(floatingView, params) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning) {
            val restartServiceIntent = Intent(applicationContext, FloatingProcessService::class.java).apply {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false // Set status mati
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (e: Exception) { android.util.Log.w("ZKM", "Optional operation failed; continuing", e) }
        }
    }
}

// Helper Lifecycle
class MyLifecycleOwner : androidx.savedstate.SavedStateRegistryOwner {
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
    fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
}

private val ProcCardColor = Color(0xF5FAFAFA)
private val ProcTextPrimary = Color(0xFF1C1B1F)
private val ProcTextSecondary = Color(0xFF8A8A8E)
private val ProcAccent = Color(0xFF3B82F6)
private val ProcHot = Color(0xFFE5484D)

/**
 * Process Monitor — tampilan kartu terang (mirip monitor referensi):
 *  - Header: pin (kunci posisi) · "Processes" · "Apps"/"All" (ketuk untuk ganti) · collapse · tutup
 *  - "Apps" = hanya aplikasi Android; "All" = semua proses (termasuk proses native/kernel)
 *  - Baris: ikon app, nama, CPU%
 */
@Composable
fun FloatingScreenContent(
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onCollapseChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var processList by remember { mutableStateOf(emptyList<com.zuan.kernelmanager.next.library.device.process.ProcessData>()) }
    var showAll by remember { mutableStateOf(false) }
    var pinned by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    val iconCache = remember { HashMap<String, ImageBitmap?>() }

    LaunchedEffect(showAll, collapsed) {
        if (collapsed) return@LaunchedEffect // tidak perlu polling saat hanya header yang terlihat
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    // Ambil banyak dulu, baru disaring: "Apps" = hanya aplikasi yang benar-benar
                    // terpasang (digabung per paket), "All" = semua proses termasuk native/kernel.
                    val raw = ProcessUtils.getTopProcesses(
                        context, 400, SortType.CPU, ProcessFilter.ALL, includeSelf = true
                    )
                    val newData = if (showAll) raw.take(30) else raw.toAppRows().take(30)
                    withContext(Dispatchers.Main) {
                        processList = newData
                        isError = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ZKM", "Unhandled operation failure", e)
                    withContext(Dispatchers.Main) { isError = true }
                }
                delay(1500)
            }
        }
    }

    // Ukuran font overlay tidak ikut "Ukuran font" sistem, supaya proporsinya sama dengan referensi.
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 1f)) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Box(
            modifier = (if (collapsed) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .padding(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ProcCardColor,
                shadowElevation = 6.dp,
                modifier = if (collapsed) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {

                    // --- HEADER (area drag, kecuali bila di-pin) ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (pinned) Modifier else Modifier.overlayDrag(onDrag)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (pinned) ProcAccent else ProcTextSecondary,
                            modifier = Modifier.size(16.dp).clickable { pinned = !pinned }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Processes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ProcTextPrimary,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (showAll) "All" else "Apps",
                            fontSize = 12.sp,
                            color = ProcTextSecondary,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showAll = !showAll }
                                .padding(horizontal = 2.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = if (collapsed) Icons.Rounded.OpenInFull else Icons.Rounded.CloseFullscreen,
                            contentDescription = null,
                            tint = ProcTextPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    collapsed = !collapsed
                                    onCollapseChanged(collapsed)
                                }
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.btn_cancel),
                            tint = ProcTextPrimary,
                            modifier = Modifier.size(16.dp).clickable { onClose() }
                        )
                    }

                    // --- DAFTAR PROSES ---
                    if (!collapsed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (isError) {
                                item { Text(stringResource(R.string.fps_error), color = ProcHot, fontSize = 12.sp) }
                            } else {
                                items(processList, key = { "${it.packageName}#${it.pid}" }) { process ->
                                    ProcessRow(process, iconCache)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // --- RESIZE HANDLE (pojok kanan bawah) ---
            if (!collapsed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onResize(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = stringResource(R.string.fps_resize),
                        tint = ProcTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    }
}

private fun cpuOf(p: com.zuan.kernelmanager.next.library.device.process.ProcessData): Float =
    p.cpu.removeSuffix("%").toFloatOrNull() ?: 0f

/** Mode "Apps": hanya paket terpasang, semua proses satu paket (mis. :push, :service) dijumlahkan. */
private fun List<com.zuan.kernelmanager.next.library.device.process.ProcessData>.toAppRows():
    List<com.zuan.kernelmanager.next.library.device.process.ProcessData> =
    filter { it.isInstalledApp }
        .groupBy { it.packageName.substringBefore(':') }
        .map { (_, procs) ->
            val main = procs.maxByOrNull { cpuOf(it) } ?: procs.first()
            main.copy(cpu = String.format(java.util.Locale.US, "%.1f%%", procs.sumOf { cpuOf(it).toDouble() }))
        }
        .sortedByDescending { cpuOf(it) }

@Composable
private fun ProcessRow(
    process: com.zuan.kernelmanager.next.library.device.process.ProcessData,
    iconCache: MutableMap<String, ImageBitmap?>
) {
    val bitmap = remember(process.packageName, process.icon) {
        iconCache.getOrPut(process.packageName) { process.icon?.toImageBitmapOrNull(64) }
    }
    val cpuValue = process.cpu.removeSuffix("%").toFloatOrNull() ?: 0f

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
            )
        } else {
            // Proses native / kernel (surfaceflinger, audioserver, dst) -> ikon pinguin seperti referensi.
            Image(
                painter = painterResource(R.drawable.ic_process_native),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = process.appName,
            style = TextStyle(fontSize = 11.sp, lineHeight = 13.sp),
            color = ProcTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = String.format(java.util.Locale.getDefault(), "%.1f%%", cpuValue),
            fontSize = 10.5.sp,
            color = if (cpuValue >= 50f) ProcHot else ProcTextSecondary,
            maxLines = 1
        )
    }
}

private fun Drawable.toImageBitmapOrNull(size: Int): ImageBitmap? = runCatching {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()
