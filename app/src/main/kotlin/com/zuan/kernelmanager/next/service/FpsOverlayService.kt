package com.zuan.kernelmanager.next.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import com.zuan.kernelmanager.next.utils.RootPersistenceUtils
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.data.fps.FpsRecorder
import com.zuan.kernelmanager.next.library.device.monitor.FpsReader
import com.zuan.kernelmanager.next.library.device.monitor.MonitorReader
import com.zuan.kernelmanager.next.library.service.FpsOverlayController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FpsOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    companion object {
        var isRunning = false
        const val TAG = "FpsOverlayService"
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2.5f
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var overlayParams: WindowManager.LayoutParams
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private var styleMode by mutableStateOf(0)
    private var pcVariant by mutableStateOf(0)
    private var androidOrientation by mutableStateOf(0)
    private var colorHex by mutableStateOf("#00FF00")
    private var textSizeSp by mutableStateOf(14f)
    private var bgAlpha by mutableStateOf(0.55f)
    private var widthScale by mutableStateOf(1f)
    private var cornerRadius by mutableStateOf(10f)
    private var bgMode by mutableStateOf(0)
    private var showBorder by mutableStateOf(true)
    private var showShadow by mutableStateOf(false)
    private var sampleIntervalMs by mutableStateOf(1000L)
    private var position by mutableStateOf("TopLeft")

    private var showFps by mutableStateOf(true)
    private var showFrameTime by mutableStateOf(false)
    private var showOnePercent by mutableStateOf(false)
    private var showZeroOnePercent by mutableStateOf(false)
    private var showCpu by mutableStateOf(true)
    private var showCpuFreq by mutableStateOf(false)
    private var showGpuLoad by mutableStateOf(false)
    private var showGpuFreq by mutableStateOf(false)
    private var showGpuUsage by mutableStateOf(false)
    private var showCpuTemp by mutableStateOf(false)
    private var showGpuTemp by mutableStateOf(false)
    private var showWatts by mutableStateOf(true)
    private var showTemp by mutableStateOf(true)
    private var showRam by mutableStateOf(true)
    private var showRamPercent by mutableStateOf(false)
    private var showBattery by mutableStateOf(false)
    private var showRefresh by mutableStateOf(false)
    private var showRender by mutableStateOf(false)
    private var showJank by mutableStateOf(false)
    private var showRecordControl by mutableStateOf(true)
    private var showSparkline by mutableStateOf(false)
    private var showSizeControls by mutableStateOf(true)

    // Skala & mode ringkas diatur langsung dari overlay (tombol -/+ , cubit, collapse) dan diingat.
    private val prefs by lazy { getSharedPreferences("zkm_fps_overlay", MODE_PRIVATE) }
    private var overlayScale by mutableStateOf(1f)
    private var collapsed by mutableStateOf(false)

    private fun setScale(value: Float) {
        overlayScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
        prefs.edit().putFloat("scale", overlayScale).apply()
    }

    private fun toggleCollapsed() {
        collapsed = !collapsed
        prefs.edit().putBoolean("collapsed", collapsed).apply()
    }

    private var initialX = 20
    private var initialY = 200
    private var overlayX = 20
    private var overlayY = 200
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private lateinit var settingsPreference: SettingsPreference

    private fun loadInitialSettings() {
        settingsPreference = SettingsPreference.getInstance(applicationContext)
        styleMode = settingsPreference.fpsStyle.value
        pcVariant = settingsPreference.fpsPcVariant.value
        androidOrientation = settingsPreference.fpsOrientation.value
        colorHex = settingsPreference.fpsColor.value
        textSizeSp = settingsPreference.fpsSize.value
        widthScale = settingsPreference.fpsWidthScale.value
        bgAlpha = settingsPreference.fpsAlpha.value
        cornerRadius = settingsPreference.fpsRadius.value
        bgMode = settingsPreference.fpsBgMode.value
        showBorder = settingsPreference.fpsBorder.value
        showShadow = settingsPreference.fpsShadow.value
        sampleIntervalMs = settingsPreference.fpsInterval.value
        position = settingsPreference.fpsPosition.value

        showFps = settingsPreference.fpsShowFps.value
        showFrameTime = settingsPreference.fpsShowFrameTime.value
        showOnePercent = settingsPreference.fpsShowOnePercent.value
        showZeroOnePercent = settingsPreference.fpsShowZeroOnePercent.value
        showCpu = settingsPreference.fpsShowCpu.value
        showCpuFreq = settingsPreference.fpsShowCpuFreq.value
        showGpuLoad = settingsPreference.fpsShowGpuUsage.value
        showGpuFreq = settingsPreference.fpsShowGpuFreq.value
        showWatts = settingsPreference.fpsShowWatts.value
        showTemp = settingsPreference.fpsShowTemp.value
        showRam = settingsPreference.fpsShowRam.value
        showRamPercent = settingsPreference.fpsShowRamPercent.value
        showBattery = settingsPreference.fpsShowBattery.value
        showRefresh = settingsPreference.fpsShowRefresh.value
        showRender = settingsPreference.fpsShowRender.value
        showJank = settingsPreference.fpsShowJank.value
        showRecordControl = settingsPreference.fpsShowRecord.value
        showSparkline = settingsPreference.fpsShowSpark.value
        showSizeControls = settingsPreference.fpsShowSizeControls.value
    }

    override fun onCreate() {
        super.onCreate()
        RootPersistenceUtils.applyRootExemptions(this)
        loadInitialSettings()
        savedStateRegistryController.performRestore(null)
        overlayScale = prefs.getFloat("scale", 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        collapsed = prefs.getBoolean("collapsed", false)
        // Revived by START_STICKY in a fresh process: load the working mode + Shizuku state and the
        // user's saved FPS reader mode *before* the first sample, otherwise the service saw BASIC.
        com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime.initialize(applicationContext)
        FpsReader.init(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val savedX = settingsPreference.fpsPosX.value
        val savedY = settingsPreference.fpsPosY.value
        overlayParams = createLayoutParams(savedX, savedY)
        startForegroundNotification()
        isRunning = true
        FpsOverlayController.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { readConfig(it) }
        if (overlayView == null) {
            setupOverlay()
        } else {
            applyBoundaryConstraints()
        }
        return START_STICKY
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            metrics.bounds.width() to metrics.bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun applyBoundaryConstraints() {
        overlayView?.post {
            overlayView?.let { v ->
                val (screenWidth, screenHeight) = getScreenSize()
                
                val viewWidth = v.width.coerceAtLeast(v.measuredWidth)
                val viewHeight = v.height.coerceAtLeast(v.measuredHeight)

                if (viewWidth <= 0 || viewHeight <= 0) return@let

                val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
                val safetyMargin = (5 * resources.displayMetrics.density).toInt()
                val maxY = (screenHeight - viewHeight - safetyMargin).coerceAtLeast(0)

                val oldX = overlayParams.x
                val oldY = overlayParams.y

                overlayParams.x = overlayParams.x.coerceIn(0, maxX)
                overlayParams.y = overlayParams.y.coerceIn(0, maxY)

                if (overlayParams.x != oldX || overlayParams.y != oldY) {
                    try {
                        windowManager.updateViewLayout(overlayView, overlayParams)
                        settingsPreference.setFpsPos(overlayParams.x, overlayParams.y)
                    } catch (e: Exception) {
                        Log.e(TAG, "Update Layout Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun readConfig(it: Intent) {
        if (it.hasExtra("STYLE")) styleMode = it.getIntExtra("STYLE", 0)
        if (it.hasExtra("PC_VARIANT")) pcVariant = it.getIntExtra("PC_VARIANT", 0)
        if (it.hasExtra("ORIENTATION")) androidOrientation = it.getIntExtra("ORIENTATION", 0)
        it.getStringExtra("COLOR")?.let { c -> colorHex = c }
        if (it.hasExtra("SIZE")) textSizeSp = it.getFloatExtra("SIZE", 14f)
        if (it.hasExtra("WIDTH_SCALE")) widthScale = it.getFloatExtra("WIDTH_SCALE", 1f)
        if (it.hasExtra("ALPHA")) bgAlpha = it.getFloatExtra("ALPHA", 0.55f)
        if (it.hasExtra("RADIUS")) cornerRadius = it.getFloatExtra("RADIUS", 10f)
        if (it.hasExtra("BG_MODE")) bgMode = it.getIntExtra("BG_MODE", 0)
        if (it.hasExtra("BORDER")) showBorder = it.getBooleanExtra("BORDER", true)
        if (it.hasExtra("SHADOW")) showShadow = it.getBooleanExtra("SHADOW", false)
        if (it.hasExtra("INTERVAL")) sampleIntervalMs = it.getIntExtra("INTERVAL", 1000).toLong().coerceIn(250L, 5000L)
        if (it.hasExtra("POSITION")) {
            position = it.getStringExtra("POSITION") ?: position
            resetPosition(position)
        }
        showFps = it.getBooleanExtra("SHOW_FPS", showFps)
        showFrameTime = it.getBooleanExtra("SHOW_FRAMETIME", showFrameTime)
        showOnePercent = it.getBooleanExtra("SHOW_1LOW", showOnePercent)
        showZeroOnePercent = it.getBooleanExtra("SHOW_01LOW", showZeroOnePercent)
        showCpu = it.getBooleanExtra("SHOW_CPU", showCpu)
        showCpuFreq = it.getBooleanExtra("SHOW_CPU_FREQ", showCpuFreq)
        showGpuLoad = it.getBooleanExtra("SHOW_GPU_LOAD", showGpuLoad)
        showGpuFreq = it.getBooleanExtra("SHOW_GPU_FREQ", showGpuFreq)
        showWatts = it.getBooleanExtra("SHOW_WATTS", showWatts)
        showTemp = it.getBooleanExtra("SHOW_TEMP", showTemp)
        showRam = it.getBooleanExtra("SHOW_RAM", showRam)
        showRamPercent = it.getBooleanExtra("SHOW_RAM_PERCENT", showRamPercent)
        showBattery = it.getBooleanExtra("SHOW_BATTERY", showBattery)
        showRefresh = it.getBooleanExtra("SHOW_REFRESH", showRefresh)
        showRender = it.getBooleanExtra("SHOW_RENDER", showRender)
        showJank = it.getBooleanExtra("SHOW_JANK", showJank)
        showRecordControl = it.getBooleanExtra("SHOW_RECORD", showRecordControl)
        showSparkline = it.getBooleanExtra("SHOW_SPARK", showSparkline)
        showSizeControls = it.getBooleanExtra("SHOW_SIZE_CONTROLS", showSizeControls)
        // Reset ukuran dari menu Overlay (opsional); perubahan dari overlay itu sendiri tidak ditimpa.
        if (it.hasExtra("OVERLAY_SCALE")) setScale(it.getFloatExtra("OVERLAY_SCALE", 1f))
    }

    private fun setupOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FpsOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FpsOverlayService)
            setViewTreeViewModelStoreOwner(this@FpsOverlayService)
            setContent {
                MainOverlayContent(
                    styleMode = styleMode,
                    pcVariant = pcVariant,
                    orientation = androidOrientation,
                    colorHex = colorHex,
                    fontSize = textSizeSp,
                    bgAlpha = bgAlpha,
                    widthScale = widthScale,
                    cornerRadius = cornerRadius,
                    bgMode = bgMode,
                    showBorder = showBorder,
                    showShadow = showShadow,
                    sampleIntervalMs = sampleIntervalMs,
                    metrics = MetricsState(
                        showFps, showFrameTime, showOnePercent, showZeroOnePercent,
                        showCpu, showCpuFreq, showGpuLoad, showGpuFreq, showWatts, showTemp,
                        showRam, showRamPercent, showBattery, showRefresh, showRender, showJank,
                        showRecordControl, showSparkline
                    ),
                    overlayScale = overlayScale,
                    collapsed = collapsed,
                    showSizeControls = showSizeControls,
                    onScaleChange = { setScale(it) },
                    onToggleCollapse = { toggleCollapsed() },
                    onDrag = { dx, dy -> dragBy(dx, dy) }
                )
            }
        }
        windowManager.addView(overlayView, overlayParams)
        applyBoundaryConstraints()
    }

    /** Geser overlay; posisi preset (center/kanan/bawah) diubah dulu ke koordinat absolut TOP|START. */
    private fun dragBy(dx: Float, dy: Float) {
        val view = overlayView ?: return
        if (overlayParams.gravity != (Gravity.TOP or Gravity.START)) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            overlayParams.gravity = Gravity.TOP or Gravity.START
            overlayX = loc[0]; overlayY = loc[1]
        }
        overlayX += dx.toInt(); overlayY += dy.toInt()
        updateOverlayPosition(overlayX, overlayY)
    }

    private fun resetPosition(p: String) {
        if (overlayView == null) return
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val margin = (16 * metrics.density).roundToInt()
        when (p) {
            "TopLeft" -> { overlayParams.gravity = Gravity.TOP or Gravity.START; overlayParams.x = margin; overlayParams.y = 100 }
            "TopCenter" -> { overlayParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; overlayParams.x = 0; overlayParams.y = 100 }
            "TopRight" -> { overlayParams.gravity = Gravity.TOP or Gravity.END; overlayParams.x = margin; overlayParams.y = 100 }
            "CenterLeft" -> { overlayParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START; overlayParams.x = margin; overlayParams.y = 0 }
            "Center" -> { overlayParams.gravity = Gravity.CENTER; overlayParams.x = 0; overlayParams.y = 0 }
            "CenterRight" -> { overlayParams.gravity = Gravity.CENTER_VERTICAL or Gravity.END; overlayParams.x = margin; overlayParams.y = 0 }
            "BottomLeft" -> { overlayParams.gravity = Gravity.BOTTOM or Gravity.START; overlayParams.x = margin; overlayParams.y = 90 }
            "BottomCenter" -> { overlayParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; overlayParams.x = 0; overlayParams.y = 90 }
            "BottomRight" -> { overlayParams.gravity = Gravity.BOTTOM or Gravity.END; overlayParams.x = margin; overlayParams.y = 90 }
        }
        overlayX = overlayParams.x
        overlayY = overlayParams.y
        if (overlayView != null) {
            windowManager.updateViewLayout(overlayView, overlayParams)
            settingsPreference.setFpsPos(overlayParams.x, overlayParams.y)
            applyBoundaryConstraints()
        }
    }

    private fun updateOverlayPosition(xPos: Int, yPos: Int) {
        val (screenWidth, screenHeight) = getScreenSize()
        val viewWidth = overlayView?.width?.coerceAtLeast(overlayView?.measuredWidth ?: 0) ?: 200
        val viewHeight = overlayView?.height?.coerceAtLeast(overlayView?.measuredHeight ?: 0) ?: 100
        val maxX = (screenWidth - viewWidth).coerceAtLeast(0)
        val safetyMargin = (5 * resources.displayMetrics.density).toInt()
        val maxY = (screenHeight - viewHeight - safetyMargin).coerceAtLeast(0)

        overlayParams.x = xPos.coerceIn(0, maxX)
        overlayParams.y = yPos.coerceIn(0, maxY)
        if (overlayView != null) {
            windowManager.updateViewLayout(overlayView, overlayParams)
            settingsPreference.setFpsPos(overlayParams.x, overlayParams.y)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyBoundaryConstraints()
    }

    private fun createLayoutParams(xPos: Int, yPos: Int): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = xPos
        params.y = yPos
        return params
    }

    private fun startForegroundNotification() {
        val channelId = "fps_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "FPS Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openIntent = Intent(this, com.zuan.kernelmanager.next.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_ROUTE", com.zuan.kernelmanager.next.ui.navigation.NavigationRoute.FpsManager.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.lp_notification_overlay))
            .setContentIntent(pendingIntent)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        startForeground(1, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isRunning) {
            val restartServiceIntent = Intent(applicationContext, FpsOverlayService::class.java).apply {
                setPackage(packageName)
            }
            val restartServicePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    1,
                    restartServiceIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    1,
                    restartServiceIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
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
        FpsRecorder.stopRecording(this)
        super.onDestroy()
        isRunning = false
        FpsOverlayController.setRunning(false)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        store.clear()
    }
}

data class MetricsState(
    val fps: Boolean,
    val frameTime: Boolean,
    val onePercent: Boolean,
    val zeroOnePercent: Boolean,
    val cpu: Boolean,
    val cpuFreq: Boolean,
    val gpuLoad: Boolean,
    val gpuFreq: Boolean,
    val watt: Boolean,
    val temp: Boolean,
    val ram: Boolean,
    val ramPercent: Boolean,
    val battery: Boolean,
    val refresh: Boolean,
    val render: Boolean,
    val jank: Boolean,
    val recordControl: Boolean,
    val sparkline: Boolean
)

@Composable
fun MainOverlayContent(
    styleMode: Int,
    pcVariant: Int,
    orientation: Int,
    colorHex: String,
    fontSize: Float,
    bgAlpha: Float,
    widthScale: Float,
    cornerRadius: Float,
    bgMode: Int,
    showBorder: Boolean,
    showShadow: Boolean,
    sampleIntervalMs: Long,
    metrics: MetricsState,
    overlayScale: Float = 1f,
    collapsed: Boolean = false,
    showSizeControls: Boolean = true,
    onScaleChange: (Float) -> Unit = {},
    onToggleCollapse: () -> Unit = {},
    onDrag: (Float, Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val customColor = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Green)
    var fps by remember { mutableFloatStateOf(0f) }
    var cpu by remember { mutableIntStateOf(0) }
    var cpuFreq by remember { mutableFloatStateOf(0f) }
    var gpuLoad by remember { mutableIntStateOf(-1) }
    var gpuLoadEstimated by remember { mutableStateOf(false) }
    var gpuFreq by remember { mutableFloatStateOf(0f) }
    var watt by remember { mutableFloatStateOf(0f) }
    var temp by remember { mutableFloatStateOf(0f) }
    var ramUsed by remember { mutableIntStateOf(0) }
    var ramTotal by remember { mutableIntStateOf(0) }
    var ramPercent by remember { mutableIntStateOf(0) }
    var battery by remember { mutableIntStateOf(0) }
    var voltage by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableFloatStateOf(0f) }
    var refresh by remember { mutableFloatStateOf(0f) }
    var renderer by remember { mutableStateOf("FPS") }
    var currentPackage by remember { mutableStateOf("") }
    var oneLow by remember { mutableFloatStateOf(0f) }
    var zeroOneLow by remember { mutableFloatStateOf(0f) }
    var jankPercent by remember { mutableFloatStateOf(0f) }
    var stutterCount by remember { mutableIntStateOf(0) }
    var isRec by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    val samples = remember { ArrayDeque<Float>() }

    LaunchedEffect(styleMode, pcVariant, sampleIntervalMs, metrics) {
        withContext(Dispatchers.IO) {
            var sampleIndex = 0L
            var lastPackageLookup = 0L
            var cachedPackage = ""
            while (isActive) {
                val started = System.currentTimeMillis()
                sampleIndex++
                runCatching {
                    fps = if (FpsRecorder.isRecording || metrics.fps || metrics.frameTime || metrics.onePercent || metrics.zeroOnePercent || metrics.sparkline) FpsReader.getRealFps() else 0f
                    val includeRenderer = metrics.render || (FpsRecorder.isRecording && sampleIndex % 4L == 0L)
                    val snapshot = MonitorReader.getEnhancedSnapshot(context, includeRenderer)
                    cpu = snapshot.cpuLoad
                    cpuFreq = snapshot.cpuFreqMHz
                    gpuLoad = snapshot.gpuLoad
                    gpuLoadEstimated = snapshot.gpuLoadEstimated
                    gpuFreq = snapshot.gpuFreqMHz
                    watt = snapshot.watt
                    temp = snapshot.temp
                    ramUsed = snapshot.ram.usedMb
                    ramTotal = snapshot.ram.totalMb
                    ramPercent = snapshot.ram.percent
                    battery = snapshot.batteryLevel
                    voltage = snapshot.batteryVoltageMv
                    current = snapshot.batteryCurrentMa
                    refresh = snapshot.refreshRateHz
                    if (metrics.render) renderer = snapshot.renderer.ifBlank { "FPS" }
                    samples.addLast(fps)
                    while (samples.size > 60) samples.removeFirst()
                    val valid = samples.filter { it > 0f }
                    oneLow = valid.sorted().let { values ->
                        if (values.isEmpty()) 0f else values.take((values.size * 0.01).toInt().coerceAtLeast(1)).average().toFloat()
                    }
                    zeroOneLow = valid.sorted().let { values ->
                        if (values.isEmpty()) 0f else values.take((values.size * 0.001).toInt().coerceAtLeast(1)).average().toFloat()
                    }
                    val targetFrame = 1000f / (refresh.takeIf { it > 1f } ?: 60f)
                    val frameTime = if (fps > 0.1f) 1000f / fps else 0f
                    val jank = frameTime > targetFrame * 1.5f && frameTime > 0f
                    if (jank) stutterCount += 1
                    if (samples.isNotEmpty()) jankPercent = stutterCount.coerceAtMost(samples.size) * 100f / samples.size
                    val now = System.currentTimeMillis()
                    isRec = FpsRecorder.isRecording
                    if (isRec && now - lastPackageLookup >= 1000L) {
                        cachedPackage = MonitorReader.getForegroundPackage()
                        lastPackageLookup = now
                    }
                    val pkg = if (isRec) cachedPackage else currentPackage
                    currentPackage = pkg
                    isPaused = FpsRecorder.isPaused
                    if (isRec) {
                        FpsRecorder.tick(context, pkg, snapshot, fps)
                    }
                }
                delay((sampleIntervalMs - (System.currentTimeMillis() - started)).coerceAtLeast(50L))
            }
        }
    }

    val frameTime = if (fps > 0.1f) 1000f / fps else 0f
    val values = LiveValues(
        fps = fps, frameTime = frameTime, oneLow = oneLow, zeroOneLow = zeroOneLow,
        cpu = cpu, cpuFreq = cpuFreq, gpuLoad = gpuLoad, gpuFreq = gpuFreq, gpuLoadEstimated = gpuLoadEstimated, watt = watt,
        temp = temp, ramUsed = ramUsed, ramTotal = ramTotal, ramPercent = ramPercent,
        battery = battery, voltage = voltage, current = current, refresh = refresh,
        renderer = renderer, jankPercent = jankPercent, stutterCount = stutterCount,
        sparkline = samples.toList()
    )

    // Seluruh overlay (semua gaya) diskalakan lewat density -> teks, padding, ikon ikut membesar/mengecil.
    val baseDensity = LocalDensity.current
    val latestScale by rememberUpdatedState(overlayScale)
    val latestOnScale by rememberUpdatedState(onScaleChange)
    val latestOnDrag by rememberUpdatedState(onDrag)
    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density * overlayScale, baseDensity.fontScale)) {
        val radius = RoundedCornerShape(cornerRadius.dp)
        val baseBackground = when (bgMode) {
            1 -> Color.Black.copy(alpha = (bgAlpha * 0.55f).coerceIn(0f, 1f))
            2 -> Color.Transparent
            else -> Color.Black.copy(alpha = bgAlpha.coerceIn(0f, 1f))
        }
        val modifier = Modifier
            .then(if (showShadow) Modifier.shadow(12.dp, radius) else Modifier)
            .background(baseBackground, radius)
            .then(if (showBorder) Modifier.border(1.dp, Color.White.copy(alpha = 0.18f), radius) else Modifier)
            // Satu jari = geser overlay, cubit dua jari = perbesar / perkecil.
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) latestOnScale((latestScale * zoom).coerceIn(FpsOverlayService.MIN_SCALE, FpsOverlayService.MAX_SCALE))
                    if (pan.x != 0f || pan.y != 0f) latestOnDrag(pan.x, pan.y)
                }
            }
            .padding(8.dp)

        if (collapsed) {
            // Mode ringkas: hanya angka FPS + tombol buka kembali.
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    String.format("%.0f", fps),
                    color = if (fps >= 55f) customColor else if (fps >= 30f) Color(0xFFFFD95A) else Color(0xFFFF6B6B),
                    fontSize = (fontSize * 1.2f).sp, fontWeight = FontWeight.ExtraBold
                )
                Text("FPS", color = Color.White.copy(alpha = 0.6f), fontSize = (fontSize * 0.6f).sp)
                OverlayIconButton(Icons.Rounded.OpenInFull, onToggleCollapse)
            }
        } else {
        Column(modifier = modifier.widthIn(min = (120f * widthScale).dp, max = (450f * widthScale).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (styleMode) {
                0 -> AndroidStyleOverlay(orientation, customColor, fontSize, widthScale, metrics, values)
                1 -> PcStyleOverlay(pcVariant, fontSize, widthScale, customColor, metrics, values)
                2 -> MiniMonitorOverlay(fontSize, widthScale, customColor, metrics, values)
                else -> PcStyleOverlay(pcVariant, fontSize, widthScale, customColor, metrics, values)
            }

            if (metrics.recordControl || showSizeControls) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.16f), thickness = 0.5.dp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (metrics.recordControl) RecordControlButton(isRec, isPaused, context, sampleIntervalMs)
                    if (showSizeControls) {
                        OverlayIconButton(Icons.Rounded.Remove) { onScaleChange(overlayScale - 0.15f) }
                        Text(
                            "${(overlayScale * 100).roundToInt()}%",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Medium
                        )
                        OverlayIconButton(Icons.Rounded.Add) { onScaleChange(overlayScale + 0.15f) }
                        OverlayIconButton(Icons.Rounded.CloseFullscreen, onToggleCollapse)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun OverlayIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
    }
}

data class LiveValues(
    val fps: Float,
    val frameTime: Float,
    val oneLow: Float,
    val zeroOneLow: Float,
    val cpu: Int,
    val cpuFreq: Float,
    val gpuLoad: Int,
    val gpuFreq: Float,
    val watt: Float,
    val temp: Float,
    val ramUsed: Int,
    val ramTotal: Int,
    val ramPercent: Int,
    val battery: Int,
    val voltage: Float,
    val current: Float,
    val refresh: Float,
    val renderer: String,
    val jankPercent: Float,
    val stutterCount: Int,
    val sparkline: List<Float>,
    val gpuLoadEstimated: Boolean = false
) {
    /** "N/A" when the GPU load is unknown, "~NN%" when it is estimated from the clock ratio. */
    val gpuLoadText: String
        get() = when {
            gpuLoad < 0 -> "N/A"
            gpuLoadEstimated -> "~$gpuLoad%"
            else -> "$gpuLoad%"
        }

    /** MHz as a number, "N/A" when the GPU clock is not readable. */
    val gpuFreqNumText: String get() = if (gpuFreq > 0f) "${gpuFreq.toInt()}" else "N/A"
}

@Composable
fun RecordControlButton(isRec: Boolean, isPaused: Boolean, context: Context, interval: Long) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(50)).clickable {
            if (isRec) FpsRecorder.stopRecording(context)
            else FpsRecorder.startRecording(MonitorReader.getForegroundPackage().ifBlank { "Unknown App" }, interval)
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when { isPaused -> Icons.Default.Pause; isRec -> Icons.Default.Stop; else -> Icons.Default.FiberManualRecord },
            contentDescription = null,
            tint = when { isPaused -> Color.Yellow; isRec -> Color.Red; else -> Color.White.copy(alpha = 0.8f) },
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun AndroidStyleOverlay(orientation: Int, color: Color, size: Float, widthScale: Float, m: MetricsState, v: LiveValues) {
    val items = buildList<Pair<String, String>> {
        if (m.fps) add("FPS" to String.format("%.0f", v.fps))
        if (m.frameTime) add("Frame" to String.format("%.1f ms", v.frameTime))
        if (m.onePercent) add("1% Low" to String.format("%.0f", v.oneLow))
        if (m.zeroOnePercent) add("0.1% Low" to String.format("%.0f", v.zeroOneLow))
        if (m.cpu) add("CPU" to "${v.cpu}%")
        if (m.cpuFreq) add("CPU F" to String.format("%.0f MHz", v.cpuFreq))
        if (m.gpuLoad) add("GPU" to "${v.gpuLoadText}")
        if (m.gpuFreq) add("GPU F" to (if (v.gpuFreq > 0f) String.format("%.0f MHz", v.gpuFreq) else "N/A"))
        if (m.ram) add("RAM" to "${v.ramUsed} MB")
        if (m.ramPercent) add("RAM" to "${v.ramPercent}%")
        if (m.watt) add("PWR" to String.format("%.1f W", v.watt))
        if (m.temp) add("TMP" to String.format("%.1f°C", v.temp))
        if (m.battery) add("BAT" to "${v.battery}%")
        if (m.refresh) add("Hz" to String.format("%.0f", v.refresh))
        if (m.render) add("API" to v.renderer)
        if (m.jank) add("JANK" to String.format("%.1f%%", v.jankPercent))
    }
    if (orientation == 0) Column(horizontalAlignment = Alignment.Start, modifier = Modifier.widthIn(min = (100f * widthScale).dp)) { items.forEach { AndroidRow(it.first, it.second, color, size) } }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = (400f * widthScale).dp)) { items.forEach { AndroidRow(it.first, it.second, color, size) } }
}

@Composable
fun AndroidRow(label: String, value: String, color: Color, size: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", color = Color.LightGray, fontSize = (size * 0.68f).sp)
        Text(value, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PcStyleOverlay(variant: Int, size: Float, widthScale: Float, accent: Color, m: MetricsState, v: LiveValues) {
    val mono = FontFamily.Monospace
    val green = Color(0xFF8CFF6A)
    val cyan = Color(0xFF67D9FF)
    val yellow = Color(0xFFFFD95A)
    val white = Color.White
    Column(modifier = Modifier.widthIn(min = (140f * widthScale).dp, max = (420f * widthScale).dp)) {
        when (variant) {
            0 -> NvidiaCounterStyle(size, accent, m, v, mono, green, white, yellow)
            1 -> NvidiaDetailedStyle(size, accent, m, v, mono, green, cyan, yellow, white)
            2 -> AfterburnerStyle(size, m, v, mono, cyan, yellow)
            3 -> RtssStyle(size, m, v, mono, green, white)
            4 -> SteamHudStyle(size, accent, m, v, white)
            5 -> EsportsHudStyle(size, accent, m, v, cyan)
            else -> TelemetryStyle(size, accent, m, v, mono)
        }
    }
}

@Composable
private fun NvidiaCounterStyle(size: Float, accent: Color, m: MetricsState, v: LiveValues, mono: FontFamily, green: Color, white: Color, yellow: Color) {
    if (m.fps) Text(String.format("%.0f FPS", v.fps), color = if (v.fps >= 55f) green else if (v.fps >= 30f) yellow else Color(0xFFFF6B6B), fontSize = (size * 1.45f).sp, fontFamily = mono, fontWeight = FontWeight.ExtraBold)
    if (m.onePercent) CounterLine("1% LOW", String.format("%.0f", v.oneLow), white, size, mono)
    if (m.frameTime) CounterLine("FT", String.format("%.1f ms", v.frameTime), white, size, mono)
    if (m.refresh) CounterLine("VRR", String.format("%.0f Hz", v.refresh), accent, size, mono)
    if (m.gpuLoad || m.gpuFreq) CounterLine("GPU", buildString { if (m.gpuLoad) append("${v.gpuLoadText}"); if (m.gpuFreq) append(" ${v.gpuFreqNumText}${if (v.gpuFreq > 0f) "M" else ""}") }, green, size, mono)
    if (m.cpu) CounterLine("CPU", "${v.cpu}%", Color(0xFF80C8FF), size, mono)
    if (m.temp) CounterLine("SOC", String.format("%.1f°C", v.temp), Color(0xFFFF9A5C), size, mono)
}

@Composable
private fun NvidiaDetailedStyle(size: Float, accent: Color, m: MetricsState, v: LiveValues, mono: FontFamily, green: Color, cyan: Color, yellow: Color, white: Color) {
    if (m.fps) Text(String.format("%02d FPS", v.fps.roundToInt()), color = green, fontSize = (size * 1.65f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (m.onePercent) SmallBadge("1% ${v.oneLow.roundToInt()}", yellow, size)
        if (m.zeroOnePercent) SmallBadge("0.1% ${v.zeroOneLow.roundToInt()}", yellow, size)
        if (m.frameTime) SmallBadge(String.format("%.1fms", v.frameTime), white, size)
    }
    if (m.gpuLoad || m.gpuFreq) DualStat("GPU", listOfNotNull(if(m.gpuLoad) "${v.gpuLoadText}" else null, if(m.gpuFreq) (if (v.gpuFreq > 0f) "${v.gpuFreq.toInt()}MHz" else "N/A") else null).joinToString(" / "), green, white, size, mono)
    if (m.cpu || m.cpuFreq) DualStat("CPU", listOfNotNull(if(m.cpu) "${v.cpu}%" else null, if(m.cpuFreq) "${v.cpuFreq.toInt()}MHz" else null).joinToString(" / "), cyan, white, size, mono)
    if (m.ram || m.ramPercent) DualStat("RAM", if (m.ramPercent) "${v.ramPercent}%" else "${v.ramUsed}MB", cyan, white, size, mono)
    if (m.temp || m.watt) DualStat("POWER", listOfNotNull(if(m.temp) String.format("%.1f°C", v.temp) else null, if(m.watt) String.format("%.1fW", v.watt) else null).joinToString(" / "), Color(0xFFFFA45B), white, size, mono)
    if (m.render) Text(v.renderer, color = accent, fontSize = (size * 0.8f).sp, fontFamily = mono)
}

@Composable
private fun AfterburnerStyle(size: Float, m: MetricsState, v: LiveValues, mono: FontFamily, cyan: Color, yellow: Color) {
    Text("ZKM TELEMETRY", color = Color.White, fontSize = (size * 0.75f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(3.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            if (m.fps) AfterburnerLine("FPS", "%.0f".format(v.fps), cyan, size, mono)
            if (m.onePercent) AfterburnerLine("1%", "%.0f".format(v.oneLow), yellow, size, mono)
            if (m.gpuLoad) AfterburnerLine("GPU", "${v.gpuLoadText}", cyan, size, mono)
            if (m.gpuFreq) AfterburnerLine("GCLK", v.gpuFreqNumText, cyan, size, mono)
        }
        Column(Modifier.weight(1f)) {
            if (m.cpu) AfterburnerLine("CPU", "${v.cpu}%", yellow, size, mono)
            if (m.temp) AfterburnerLine("TEMP", "%.1f°".format(v.temp), yellow, size, mono)
            if (m.ramPercent) AfterburnerLine("RAM", "${v.ramPercent}%", Color.White, size, mono)
            if (m.watt) AfterburnerLine("PWR", "%.1fW".format(v.watt), Color.White, size, mono)
        }
    }
}

@Composable
private fun RtssStyle(size: Float, m: MetricsState, v: LiveValues, mono: FontFamily, green: Color, white: Color) {
    val rows = buildList {
        if (m.fps) add("FPS" to "%.0f".format(v.fps))
        if (m.onePercent) add("1% LOW" to "%.0f".format(v.oneLow))
        if (m.frameTime) add("FRAME" to "%.1f ms".format(v.frameTime))
        if (m.cpu) add("CPU" to "${v.cpu}%")
        if (m.gpuLoad) add("GPU" to "${v.gpuLoadText}")
        if (m.temp) add("TEMP" to "%.1f°C".format(v.temp))
        if (m.ram) add("RAM" to "${v.ramUsed}M")
        if (m.jank) add("JANK" to "%.1f%%".format(v.jankPercent))
    }
    rows.take(8).forEach { CounterLine(it.first, it.second, if (it.first == "FPS") green else white, size, mono) }
}

@Composable
private fun SteamHudStyle(size: Float, accent: Color, m: MetricsState, v: LiveValues, white: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (m.fps) Text("${v.fps.roundToInt()}", color = white, fontSize = (size * 1.35f).sp, fontWeight = FontWeight.Black)
        Column {
            if (m.onePercent) Text("1% ${v.oneLow.roundToInt()}", color = accent, fontSize = (size * 0.68f).sp)
            if (m.temp) Text("${v.temp.roundToInt()}°", color = Color(0xFFFFA45B), fontSize = (size * 0.68f).sp)
        }
        if (m.sparkline && v.sparkline.size > 1) Sparkline(v.sparkline, accent, Modifier.width(55.dp).height(22.dp))
    }
}

@Composable
private fun EsportsHudStyle(size: Float, accent: Color, m: MetricsState, v: LiveValues, cyan: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m.fps) Text("${v.fps.roundToInt()}", color = Color.White, fontSize = (size * 1.25f).sp, fontWeight = FontWeight.ExtraBold)
            if (m.gpuLoad) Text("GPU ${v.gpuLoadText}", color = cyan, fontSize = (size * 0.65f).sp)
            if (m.cpu) Text("CPU ${v.cpu}%", color = accent, fontSize = (size * 0.65f).sp)
            if (m.refresh) Text("${v.refresh.roundToInt()}Hz", color = Color.White.copy(alpha = 0.8f), fontSize = (size * 0.65f).sp)
        }
    }
}

@Composable
private fun TelemetryStyle(size: Float, accent: Color, m: MetricsState, v: LiveValues, mono: FontFamily) {
    if (m.sparkline && v.sparkline.size > 1) Sparkline(v.sparkline, accent, Modifier.fillMaxWidth().height(30.dp))
    if (m.fps) Text("${v.fps.roundToInt()} FPS", color = accent, fontSize = (size * 1.1f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (m.frameTime) Text("${"%.1f".format(v.frameTime)}ms", color = Color.White, fontSize = (size * 0.7f).sp, fontFamily = mono)
        if (m.cpu) Text("C${v.cpu}%", color = Color(0xFF80C8FF), fontSize = (size * 0.7f).sp, fontFamily = mono)
        if (m.gpuLoad) Text("G${v.gpuLoadText}", color = Color(0xFF8CFF6A), fontSize = (size * 0.7f).sp, fontFamily = mono)
    }
}

@Composable
fun MiniMonitorOverlay(size: Float, widthScale: Float, color: Color, m: MetricsState, v: LiveValues) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = (90f * widthScale).dp)) {
        if (m.fps) Text("${v.fps.roundToInt()}", color = Color.White, fontSize = (size * 1.5f).sp, fontWeight = FontWeight.Black)
        Text("FPS", color = Color.Gray, fontSize = (size * 0.58f).sp)
        Row(horizontalArrangement = Arrangement.Center) {
            if (m.cpu) Text("C ${v.cpu}%", color = Color(0xFF00BFFF), fontSize = (size * 0.72f).sp, modifier = Modifier.padding(end = 6.dp))
            if (m.gpuLoad) Text("G ${v.gpuLoadText}", color = Color(0xFF8CFF6A), fontSize = (size * 0.72f).sp, modifier = Modifier.padding(end = 6.dp))
            if (m.temp) Text("${v.temp.roundToInt()}°", color = Color(0xFFFF8C00), fontSize = (size * 0.72f).sp)
        }
        if (m.sparkline && v.sparkline.size > 1) Sparkline(v.sparkline, color, Modifier.width(70.dp).height(18.dp))
    }
}

@Composable
private fun CounterLine(label: String, value: String, color: Color, size: Float, mono: FontFamily) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.72f), fontSize = (size * 0.75f).sp, fontFamily = mono)
        Text(value, color = color, fontSize = (size * 0.78f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AfterburnerLine(label: String, value: String, color: Color, size: Float, mono: FontFamily) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = (size * 0.62f).sp, fontFamily = mono)
        Text(value, color = color, fontSize = (size * 0.72f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DualStat(label: String, value: String, labelColor: Color, valueColor: Color, size: Float, mono: FontFamily) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = labelColor, fontSize = (size * 0.72f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = (size * 0.72f).sp, fontFamily = mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallBadge(text: String, color: Color, size: Float) {
    Text(text, color = color, fontSize = (size * 0.62f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 4.dp, vertical = 2.dp))
}

@Composable
private fun Sparkline(points: List<Float>, color: Color, modifier: Modifier) {
    if (points.isEmpty()) return
    Canvas(modifier) {
        val max = points.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val min = points.minOrNull() ?: 0f
        val range = (max - min).coerceAtLeast(1f)
        val step = size.width / (points.lastIndex.coerceAtLeast(1))
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = index * step
            val y = size.height - ((point - min) / range) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MainOverlayContentPreview() {
    com.zuan.kernelmanager.next.ui.theme.ZuanKernelManagerTheme {
        MainOverlayContent(
            styleMode = 1,
            pcVariant = 0,
            orientation = 0,
            colorHex = "#00FF00",
            fontSize = 14f,
            bgAlpha = 0.55f,
            widthScale = 0.3f,
            cornerRadius = 10f,
            bgMode = 0,
            showBorder = true,
            showShadow = false,
            sampleIntervalMs = 1000L,
            metrics = MetricsState(
                fps = true,
                frameTime = true,
                onePercent = true,
                zeroOnePercent = false,
                cpu = true,
                cpuFreq = false,
                gpuLoad = true,
                gpuFreq = false,
                watt = true,
                temp = true,
                ram = true,
                ramPercent = false,
                battery = false,
                refresh = true,
                render = false,
                jank = false,
                recordControl = true,
                sparkline = true
            )
        )
    }
}
