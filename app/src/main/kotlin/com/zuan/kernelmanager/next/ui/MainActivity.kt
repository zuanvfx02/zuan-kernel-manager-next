/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)

package com.zuan.kernelmanager.next.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.view.WindowManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.zuan.kernelmanager.next.utils.RootPersistenceUtils
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.topjohnwu.superuser.Shell
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.ui.navigation.LiquidNavigationBar
import com.zuan.kernelmanager.next.ui.navigation.LiquidNavigationBar2
import com.zuan.kernelmanager.next.ui.navigation.NavigationRoute
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.feature.settings.availableColors
import com.zuan.kernelmanager.next.feature.settings.WeatherEffect
import com.zuan.kernelmanager.next.feature.settings.NavStyle
import com.zuan.kernelmanager.next.feature.settings.BgType
import com.zuan.kernelmanager.next.feature.settings.ExpressiveWallpaperCanvas
import com.zuan.kernelmanager.next.feature.settings.generateThemedBackgroundColor
import com.zuan.kernelmanager.next.ui.theme.ThemeMode
import com.zuan.kernelmanager.next.ui.theme.ZuanKernelManagerTheme
import com.zuan.kernelmanager.next.core.util.ContextUtils
import com.zuan.kernelmanager.next.core.kernel.KernelCore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import com.zuan.kernelmanager.next.ui.components.zkmHazeSource
import com.zuan.kernelmanager.next.ui.components.zkmHazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import androidx.lifecycle.lifecycleScope
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.mode.WorkingMode
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager
import com.zuan.kernelmanager.next.library.shell.AxeronShell
import com.zuan.kernelmanager.next.library.shell.ShizukuShell
import kotlinx.coroutines.launch

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

import com.zuan.kernelmanager.next.ui.navigation.StandardNavigationBar
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmRailActive
import com.zuan.kernelmanager.next.ui.adaptive.LocalZkmWindow
import com.zuan.kernelmanager.next.ui.adaptive.ZkmNavigationRail
import com.zuan.kernelmanager.next.ui.adaptive.ZkmRailItem
import com.zuan.kernelmanager.next.ui.adaptive.ZkmRailWidth
import com.zuan.kernelmanager.next.ui.adaptive.rememberZkmWindowInfo
import com.zuan.kernelmanager.next.ui.adaptive.zkmHorizontalSafeInsets
import com.zuan.kernelmanager.next.ui.adaptive.zkmStartInset
import com.zuan.kernelmanager.next.ui.navigation.MainSwipeScreen
import com.zuan.kernelmanager.next.ui.navigation.ActiveDialog
import com.zuan.kernelmanager.next.ui.navigation.ToolsMenuContent
import com.zuan.kernelmanager.next.feature.home.menu.ScreenTimeScreen
import com.zuan.kernelmanager.next.feature.home.menu.BatteryUsageScreen
import com.zuan.kernelmanager.next.feature.home.menu.ChargeStatsScreen
import com.zuan.kernelmanager.next.feature.home.menu.NetworkTrafficScreen
import com.zuan.kernelmanager.next.feature.home.menu.WakelockAnalyzerScreen
import com.zuan.kernelmanager.next.feature.storage.StorageScreen
import com.zuan.kernelmanager.next.ui.navigation.PowerMenuContent
import com.zuan.kernelmanager.next.ui.components.VideoWallpaperPlayer
import com.zuan.kernelmanager.next.ui.components.WeatherEffectOverlay

import com.zuan.kernelmanager.next.feature.update.UpdateChecker
import com.zuan.kernelmanager.next.feature.update.UpdateInfo
import com.zuan.kernelmanager.next.feature.update.UpdateDialog

import com.zuan.kernelmanager.next.feature.dashboard.OverallScreen
import com.zuan.kernelmanager.next.feature.home.HomeScreen
import com.zuan.kernelmanager.next.feature.soc.SoCScreen
import com.zuan.kernelmanager.next.feature.settings.SettingsScreen
import com.zuan.kernelmanager.next.feature.about.AboutScreen
import com.zuan.kernelmanager.next.feature.about.changelogs.ChangelogsScreen
import com.zuan.kernelmanager.next.feature.about.opensource.OpenSourceScreen
import com.zuan.kernelmanager.next.feature.settings.WallpaperStyleScreen
import com.zuan.kernelmanager.next.feature.general.GeneralScreen
import com.zuan.kernelmanager.next.feature.terminal.TerminalScreen
import com.zuan.kernelmanager.next.feature.setedit.SetEditScreen
import com.zuan.kernelmanager.next.feature.fps.FpsManagerScreen
import com.zuan.kernelmanager.next.feature.process.ProcessManagerScreen
import com.zuan.kernelmanager.next.feature.kernel.flasher.KernelFlasherScreen
import com.zuan.kernelmanager.next.feature.activitylauncher.ActivityLauncherScreen
import com.zuan.kernelmanager.next.feature.ksuweb.KsuWebuiScreen

import com.zuan.kernelmanager.next.feature.gpu.adreno.AdrenoScreen
import com.zuan.kernelmanager.next.feature.gpu.mtk.MtkScreen

import com.zuan.kernelmanager.next.feature.home.menu.DebloatFreezeScreen
import com.zuan.kernelmanager.next.feature.home.menu.ThermalDevicesScreen
import com.zuan.kernelmanager.next.feature.home.menu.DisplayScreen
import com.zuan.kernelmanager.next.feature.home.menu.BatteryControllerScreen
import com.zuan.kernelmanager.next.feature.home.menu.DozeModeScreen
import com.zuan.kernelmanager.next.feature.home.menu.Dex2oatScreen
import com.zuan.kernelmanager.next.feature.appmanager.AppManagerScreen
import com.zuan.kernelmanager.next.feature.miuithermal.MiuiThermalScreen

import com.zuan.kernelmanager.next.ui.IntroScreen
import com.zuan.kernelmanager.next.ui.components.ZkmHazeStyleCache

class MainActivity : AppCompatActivity() {
    private var isRoot = false
    private var showRootDialog by mutableStateOf(false)
    private var pendingRoute by mutableStateOf<String?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val checkRoot = Runnable {
        Shell.getShell { shell ->
            isRoot = shell.isRoot
            if (isRoot) {
                RootPersistenceUtils.applyRootExemptions(this@MainActivity)
            } else {
                // Only block the app when the user explicitly chose ROOT mode.
                if (!isRoot && WorkingModeManager.current.hasRoot) { showRootDialog = true }
            }
        }
    }

    /** React to the working mode picked in the intro / Settings. Never touches su unless ROOT. */
    private fun applyWorkingMode(mode: WorkingMode) {
        KernelRootRuntime.onWorkingModeChanged(applicationContext, mode)
        when (mode) {
            WorkingMode.ROOT -> {
                showRootDialog = false
                Thread(checkRoot).start()
            }
            WorkingMode.ADB -> {
                showRootDialog = false
                val shizukuRunning = ShizukuShell.isRunning()
                val axeronRunning = AxeronShell.isRunning()
                if (!shizukuRunning && !axeronRunning) {
                    android.widget.Toast.makeText(this, getString(R.string.mode_shizuku_missing), android.widget.Toast.LENGTH_LONG).show()
                } else {
                    if (shizukuRunning && !ShizukuShell.isGranted()) {
                        android.widget.Toast.makeText(this, getString(R.string.mode_shizuku_request), android.widget.Toast.LENGTH_LONG).show()
                        ShizukuShell.requestPermission()
                    }
                    if (axeronRunning && !AxeronShell.isGranted()) {
                        AxeronShell.requestPermission(this)
                    }
                }
            }
            WorkingMode.BASIC -> showRootDialog = false
        }
        KernelCore.initialize(applicationContext)
        KernelCore.rescanCapabilities(applicationContext)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ContextUtils.updateBaseContext(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
            val sharedPrefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            val dpi = sharedPrefs.getInt("app_custom_dpi", 0)
            if (dpi != 0) { overrideConfiguration.densityDpi = dpi }
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen: SplashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val routeExtra = intent?.getStringExtra("TARGET_ROUTE")
        if (!routeExtra.isNullOrBlank()) {
            pendingRoute = routeExtra
        }
        // Widgets / app shortcuts can ask to open a specific screen.
        com.zuan.kernelmanager.next.feature.widget.ZkmDeepLink.handle(intent)
        // Long-press app-icon shortcuts (published at runtime).
        com.zuan.kernelmanager.next.feature.widget.ZkmShortcuts.publish(this)

        val prefs = SettingsPreference.getInstance(this)
        val savedDpi = prefs.appDpi.value
        splashScreen.setKeepOnScreenCondition { false }
        
        enableEdgeToEdge()
        askNotificationPermission()

        // The mode decides whether a root shell may be opened at all, so load it first.
        WorkingModeManager.load(applicationContext)
        KernelCore.initialize(applicationContext)

        // Fires once with the saved mode (nothing while the intro is still pending) and again on
        // every change from the intro or from Settings.
        lifecycleScope.launch {
            WorkingModeManager.saved.collect { saved ->
                if (saved != null) applyWorkingMode(saved)
            }
        }

        setContent {
            val savedDpiState by prefs.appDpi.collectAsStateWithLifecycle()
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
            val hasCompletedIntro by prefs.hasCompletedIntro.collectAsStateWithLifecycle()
            // Versi Lite: splash sederhana, tanpa layer backdrop liquid, tanpa video wallpaper.
            val liteMode by prefs.liteMode.collectAsStateWithLifecycle()
            
            val isDynamicColor by prefs.isDynamicColor.collectAsStateWithLifecycle()
            val themeColorName by prefs.themeColorName.collectAsStateWithLifecycle()
            val isCustomColor by prefs.isCustomColor.collectAsStateWithLifecycle()
            val customPrimary by prefs.customPrimaryColor.collectAsStateWithLifecycle()
            val customSecondary by prefs.customSecondaryColor.collectAsStateWithLifecycle()
            val customTertiary by prefs.customTertiaryColor.collectAsStateWithLifecycle()
            
            var showCustomSplash by remember { mutableStateOf(true) }

            val currentDensity = LocalDensity.current
            val targetDpi = if (savedDpiState != 0) savedDpiState else savedDpi
            val appDensity = remember(targetDpi) {
                if (targetDpi > 0) { Density(targetDpi.toFloat() / 160f, currentDensity.fontScale) } 
                else { currentDensity }
            }
            
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
            }

            CompositionLocalProvider(LocalDensity provides appDensity) {
                val themeColors = remember(isDynamicColor, themeColorName, isCustomColor, customPrimary, customSecondary, customTertiary) {
                    when {
                        isCustomColor -> Triple(
                            Color(customPrimary),
                            Color(customSecondary),
                            Color(customTertiary)
                        )
                        isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> null
                        else -> {
                            val selectedColor = availableColors.find { it.name == themeColorName } ?: availableColors[1]
                            Triple(selectedColor.primary, selectedColor.secondary, selectedColor.tertiary)
                        }
                    }
                }
                
                ZuanKernelManagerTheme(
                    darkTheme = useDarkTheme,
                    customPrimary = themeColors?.first,
                    customSecondary = themeColors?.second,
                    customTertiary = themeColors?.third
                ) {
                    // MainActivity owns the wallpaper layer so every screen in the
                    // activity shares the exact same wallpaper/weather rendering.
                    val mainBackgroundHazeState = remember { HazeState() }

                    // [FIX CRASH] layerBackdrop TIDAK boleh membungkus capsule nav yang
                    // memanggil drawBackdrop() pada backdrop yang sama: itu membuat
                    // RenderNode saling menunjuk (siklus) -> RenderThread rekursi tanpa
                    // henti -> stack overflow di libhwui (prepareTreeImpl).
                    // Karena itu layer wallpaper direkam terpisah di sini (sibling dari
                    // konten), dan layer konten direkam di ZuanKernelManagerApp pada
                    // Scaffold saja (sibling dari nav bar). Keduanya digabung untuk capsule.
                    val rootLiquidBackdrop = rememberLayerBackdrop()

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().then(if (liteMode) Modifier else Modifier.layerBackdrop(rootLiquidBackdrop))) {
                            MainActivityWallpaperBackground(
                                hazeState = mainBackgroundHazeState
                            )
                        }

                        Crossfade(
                            targetState = when {
                                showCustomSplash -> "splash"
                                !hasCompletedIntro -> "intro"
                                else -> "main"
                            },
                            animationSpec = tween(durationMillis = 800),
                            label = "AppFlowTransition"
                        ) { state ->
                            when (state) {
                                "splash" -> {
                                    if (liteMode) {
                                        ZuanLiteSplashScreen(
                                            onSplashFinished = { showCustomSplash = false }
                                        )
                                    } else {
                                        Zuan3DCustomSplashScreen(
                                            onSplashFinished = { showCustomSplash = false }
                                        )
                                    }
                                }
                                "intro" -> {
                                    IntroScreen(
                                        onIntroCompleted = { },
                                        onDeclined = { exitProcess(0) }
                                    )
                                }
                                "main" -> {
                                    ZuanKernelManagerApp(
                                        showRootDialog = showRootDialog,
                                        backgroundHazeState = mainBackgroundHazeState,
                                        liquidBackdrop = rootLiquidBackdrop,
                                        pendingRoute = pendingRoute,
                                        onPendingRouteHandled = { pendingRoute = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val routeExtra = intent.getStringExtra("TARGET_ROUTE")
        if (!routeExtra.isNullOrBlank()) {
            pendingRoute = routeExtra
        }
        com.zuan.kernelmanager.next.feature.widget.ZkmDeepLink.handle(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        init {
            @Suppress("DEPRECATION")
            if (Shell.getCachedShell() == null) {
                Shell.setDefaultBuilder(
                    Shell.Builder.create()
                        .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                        .setTimeout(20),
                )
            }
        }
    }
    
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun MainActivityWallpaperBackground(
    hazeState: HazeState,
) {
    val context = LocalContext.current
    val prefs = remember { SettingsPreference.getInstance(context) }

    val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
    val bgType by prefs.bgType.collectAsStateWithLifecycle()
    val solidColor by prefs.solidColor.collectAsStateWithLifecycle()
    val expressiveThemeId by prefs.expressiveThemeId.collectAsStateWithLifecycle()

    val isCustomBg by prefs.isCustomBackground.collectAsStateWithLifecycle()
    val bgUriString by prefs.backgroundImageUri.collectAsStateWithLifecycle()
    val isVideo by prefs.isVideoWallpaper.collectAsStateWithLifecycle()
    val adaptiveEcoMode by prefs.adaptiveEcoMode.collectAsStateWithLifecycle()
    // Versi Lite tidak memutar video wallpaper (berat): jatuh ke warna latar tema.
    val liteMode by prefs.liteMode.collectAsStateWithLifecycle()
    val liteBlocksVideo = liteMode && prefs.isVideoWallpaperRaw()

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }
    val isPowerSaveMode = remember { powerManager?.isPowerSaveMode == true }
    val batteryStatus = remember {
        val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(null, ifilter)
    }
    val bLevel = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 100
    val bScale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: 100
    val bPct = if (bLevel >= 0 && bScale > 0) (bLevel * 100 / bScale) else 100
    val isEcoActive = adaptiveEcoMode && (bPct <= 15 || isPowerSaveMode)

    val effectiveIsVideo = if (isEcoActive) false else isVideo

    val useSystemWallpaper by prefs.useSystemWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by prefs.isBgBlur.collectAsStateWithLifecycle()
    val bgContrast by prefs.bgContrast.collectAsStateWithLifecycle()

    val weatherEffect by prefs.weatherEffect.collectAsStateWithLifecycle()
    val effectiveWeather = if (isEcoActive) WeatherEffect.NONE else weatherEffect
    val weatherIntensity by prefs.weatherIntensity.collectAsStateWithLifecycle()

    val bgSaturation by prefs.bgSaturation.collectAsStateWithLifecycle()
    val blurStrength by prefs.blurStrength.collectAsStateWithLifecycle()

    val isDynamicColor by prefs.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by prefs.themeColorName.collectAsStateWithLifecycle()
    val isCustomColor by prefs.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by prefs.customPrimaryColor.collectAsStateWithLifecycle()

    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
    }

    val effectivePrimary = remember(isDynamicColor, themeColorName, isCustomColor, customPrimary) {
        when {
            isCustomColor -> Color(customPrimary)
            isDynamicColor -> Color.Unspecified
            else -> {
                val selected = availableColors.find { it.name == themeColorName } ?: availableColors[1]
                selected.primary
            }
        }
    }

    val finalPrimary = if (effectivePrimary == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        effectivePrimary
    }

    val (tintedBackground, _) = generateThemedBackgroundColor(finalPrimary, useDarkTheme)

    val activityBackgroundColor = if (useSystemWallpaper) {
        Color.Transparent
    } else {
        when (bgType) {
            BgType.SYSTEM -> tintedBackground
            BgType.PRESET_SOLID -> Color(solidColor)
            BgType.EXPRESSIVE, BgType.GALLERY -> tintedBackground
        }
    }

    LaunchedEffect(useSystemWallpaper) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (useSystemWallpaper) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            runCatching {
                activity.window.setBackgroundDrawable(WallpaperManager.getInstance(activity).drawable)
            }
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            activity.window.setBackgroundDrawable(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(activityBackgroundColor)
            .zkmHazeSource(state = hazeState, zIndex = 0f)
    ) {
        if (!useSystemWallpaper) {
            when (bgType) {
                BgType.SYSTEM -> {
                    Box(Modifier.fillMaxSize().background(tintedBackground))
                }

                BgType.PRESET_SOLID -> {
                    Box(Modifier.fillMaxSize().background(Color(solidColor)))
                }

                BgType.EXPRESSIVE -> {
                    ExpressiveWallpaperCanvas(themeId = expressiveThemeId)
                }

                BgType.GALLERY -> {
                    if (isCustomBg && bgUriString != null && !liteBlocksVideo) {
                        val blurModifier = when {
                            isBgBlur && blurStrength > 0f -> Modifier.blur(blurStrength.dp)
                            isBgBlur -> Modifier.blur(20.dp)
                            else -> Modifier
                        }

                        val saturationMatrix = ColorMatrix().apply {
                            setToSaturation(bgSaturation)
                        }
                        val colorFilter = ColorFilter.colorMatrix(saturationMatrix)
                        val uri = Uri.parse(bgUriString)

                        if (effectiveIsVideo) {
                            VideoWallpaperPlayer(
                                uri = uri,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(blurModifier)
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(uri).build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(blurModifier),
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter
                            )
                        }

                        if (bgContrast > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = bgContrast))
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(tintedBackground))
                    }
                }
            }
        }

        // Weather is attached to the Activity-level wallpaper, so it remains part
        // of the background regardless of which MainActivity route is displayed.
        WeatherEffectOverlay(
            effect = effectiveWeather,
            intensity = weatherIntensity,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun ZuanKernelManagerApp(
    showRootDialog: Boolean = false,
    backgroundHazeState: HazeState,
    liquidBackdrop: Backdrop,
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {}
) {
    val context = LocalContext.current

    val contentHazeState = remember { HazeState() }

    // Layer konten (Scaffold saja, TANPA nav bar) + layer wallpaper dari MainActivity.
    val contentBackdrop = rememberLayerBackdrop()
    val navBackdrop = rememberCombinedBackdrop(liquidBackdrop, contentBackdrop)

    // Host dialog Android Liquid Glass: dialog liquid digambar di sini (satu window
    // dengan layer backdrop), bukan di window Dialog terpisah.
    val dialogHostState = remember { com.zuan.kernelmanager.next.ui.components.ZkmDialogHostState() }

    val navController = rememberNavController()

    val prefs = remember { SettingsPreference.getInstance(context) }
    val navStyle by prefs.navStyle.collectAsStateWithLifecycle()
    val navLabelState by prefs.navLabelState.collectAsStateWithLifecycle()
    val liteMode by prefs.liteMode.collectAsStateWithLifecycle()

    // Style kartu liquid aktif -> Surface di atas kaca dibuat transparan (lihat ZkmLiquidFlags).
    val cardGlassStyleNow by prefs.cardGlassStyle.collectAsStateWithLifecycle()
    androidx.compose.runtime.SideEffect {
        com.zuan.kernelmanager.next.ui.components.ZkmLiquidFlags.cardsLiquid =
            cardGlassStyleNow == com.zuan.kernelmanager.next.feature.settings.CardGlassStyle.LIQUID_GLASS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Deep link from a home-screen widget / app shortcut (see feature/widget).
    val pendingRouteFromWidget by com.zuan.kernelmanager.next.feature.widget.ZkmDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRouteFromWidget) {
        val target = pendingRouteFromWidget ?: return@LaunchedEffect
        com.zuan.kernelmanager.next.feature.widget.ZkmDeepLink.pending.value = null
        runCatching { navController.navigate(target) { launchSingleTop = true } }
    }

    LaunchedEffect(pendingRoute) {
        val target = pendingRoute ?: return@LaunchedEffect
        if (target.isNotBlank()) {
            runCatching { navController.navigate(target) { launchSingleTop = true } }
            onPendingRouteHandled()
        }
    }
    
    var activeDialog by remember { mutableStateOf(ActiveDialog.NONE) }

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var varHasCheckedUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!varHasCheckedUpdate) {
            varHasCheckedUpdate = true
            delay(1500)
            // "Show update dialog" off in Update Settings: stay quiet (the Home card still shows updates).
            if (!com.zuan.kernelmanager.next.feature.settings.SettingsPreference.getInstance(context).getShowUpdateDialog()) return@LaunchedEffect
            val info = UpdateChecker.checkForUpdate(context)
            if (info != null) {
                updateInfo = info
                showUpdateDialog = true
            }
        }
    }

    val showBottomBar = (currentRoute in listOf(
        NavigationRoute.Overall.route,
        NavigationRoute.Dashboard.route,
        NavigationRoute.SoC.route,
        NavigationRoute.About.route,
        NavigationRoute.MainSwipeContainer.route
    )) && (navStyle != NavStyle.MODERN_TABS)

    // Landscape / layar lebar: bottom bar melayang diganti NavigationRail di sisi kiri.
    val zkmWindow = rememberZkmWindowInfo()
    // Inset window utama (taskbar PC mode, status bar, cutout) dibagikan ke semua dialog & sheet.
    val zkmHostInsets = com.zuan.kernelmanager.next.ui.adaptive.rememberZkmHostInsets()
    // Rail hanya untuk gaya Classic Material. Capsule (iOS) tetap di bawah, hanya dikecilkan & ditengah.
    val useRail = showBottomBar && zkmWindow.isWide && navStyle == NavStyle.CLASSIC_MATERIAL
    val isMainSwipeRoute = currentRoute == NavigationRoute.MainSwipeContainer.route
    val railStartInset = zkmStartInset()

    val bottomBarHeight = 150.dp
    val bottomBarHeightPx = with(LocalDensity.current) { bottomBarHeight.toPx() }
    
    var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity.Zero 
        }
    }

    val startDestination = if (navStyle == NavStyle.MODERN_TABS) {
        NavigationRoute.MainSwipeContainer.route
    } else {
        NavigationRoute.Overall.route
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
      // Backdrop wallpaper dibagikan ke seluruh layar: kartu, toggle, slider,
      // dan dialog liquid glass menggambar efek dari layer wallpaper ini.
      androidx.compose.runtime.CompositionLocalProvider(
          com.zuan.kernelmanager.next.ui.components.LocalZkmLiquidBackdrop provides liquidBackdrop,
          com.zuan.kernelmanager.next.ui.components.LocalZkmDialogHost provides dialogHostState,
          LocalZkmWindow provides zkmWindow,
          com.zuan.kernelmanager.next.ui.adaptive.LocalZkmHostInsets provides zkmHostInsets,
          LocalZkmRailActive provides useRail
      ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // LAYER 1: Scaffold (Konten Menu & Navigasi)
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (liteMode) Modifier else Modifier.layerBackdrop(contentBackdrop))
                    .zkmHazeSource(state = contentHazeState, zIndex = 1f),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0.dp)
            ) { innerPadding ->
                
                val contentPadding = if (!showBottomBar) {
                    PaddingValues(bottom = 0.dp)
                } else {
                    innerPadding
                }

                // Landscape: geser konten dari rail & jauhkan dari cutout / bilah nav samping.
                // MainSwipeContainer (mode Modern Tabs) mengurus rail-nya sendiri.
                val wideInsetModifier = when {
                    !zkmWindow.isWide -> Modifier
                    isMainSwipeRoute -> Modifier
                    useRail -> Modifier
                        .padding(start = ZkmRailWidth + railStartInset)
                        .zkmHorizontalSafeInsets(WindowInsetsSides.End)
                    else -> Modifier.zkmHorizontalSafeInsets()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .then(wideInsetModifier)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -fullWidth / 3 },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        predictivePopExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> fullWidth },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        gatedComposable(navController, NavigationRoute.MainSwipeContainer.route) {
                            MainSwipeScreen(
                                navController = navController,
                                hazeState = backgroundHazeState,
                                onOpenDialog = { dialog -> activeDialog = dialog }
                            )
                        }
                        
                        gatedComposable(navController, NavigationRoute.Overall.route) { OverallScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.Dashboard.route) { HomeScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.SoC.route) { SoCScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.Settings.route) { SettingsScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.About.route) { AboutScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.WallpaperStyle.route) { WallpaperStyleScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.General.route) { GeneralScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.Terminal.route) { TerminalScreen() }
                        gatedComposable(navController, NavigationRoute.SetEdit.route) { SetEditScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.FpsManager.route) { FpsManagerScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.ProcessManager.route) { 
                            ProcessManagerScreen(
                                navController = navController,
                                hazeState = backgroundHazeState
                            ) 
                        }

                        gatedComposable(navController, NavigationRoute.KernelFlasher.route) { KernelFlasherScreen(rootNavController = navController) }
                        
                        gatedComposable(navController, NavigationRoute.ActivityLauncher.route) { ActivityLauncherScreen(rootNavController = navController) }
                        gatedComposable(navController, NavigationRoute.KsuWebUI.route) { KsuWebuiScreen(navController = navController) }
                        
                        gatedComposable(navController, NavigationRoute.Adreno.route) { AdrenoScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.Mtk.route) { MtkScreen(navController = navController) }
                        
                        gatedComposable(navController, NavigationRoute.DebloatFreeze.route) { DebloatFreezeScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.ThermalDevices.route) { ThermalDevicesScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.Display.route) { DisplayScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.BatteryController.route) { BatteryControllerScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.DozeMode.route) { DozeModeScreen(navController = navController) }
                        gatedComposable(navController, NavigationRoute.Dex2oat.route) { Dex2oatScreen(navController = navController) }

                        gatedComposable(navController, NavigationRoute.AppManager.route) { AppManagerScreen() }
                        gatedComposable(navController, NavigationRoute.MiuiThermal.route) { MiuiThermalScreen() }
                        gatedComposable(navController, NavigationRoute.ScreenTime.route) { ScreenTimeScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.BatteryUsage.route) { BatteryUsageScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.ChargeStats.route) { ChargeStatsScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.NetworkTraffic.route) { NetworkTrafficScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.WakelockAnalyzer.route) { WakelockAnalyzerScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.Storage.route) { StorageScreen(navController = navController, hazeState = backgroundHazeState) }
                        gatedComposable(navController, NavigationRoute.GameSpace.route) { com.zuan.kernelmanager.next.feature.gamespace.GameSpaceScreen(navController = navController, hazeState = backgroundHazeState) }
                        
                        gatedComposable(navController, NavigationRoute.Changelogs.route) { 
                            ChangelogsScreen(navController = navController, hazeState = backgroundHazeState) 
                        }
                        
                        gatedComposable(navController, NavigationRoute.UpdateSettings.route) {
                            com.zuan.kernelmanager.next.feature.update.UpdateSettingsScreen(navController = navController, hazeState = backgroundHazeState)
                        }

                        gatedComposable(navController, NavigationRoute.OpenSource.route) { 
                            OpenSourceScreen(navController = navController, hazeState = backgroundHazeState) 
                        }

                        gatedComposable(navController, NavigationRoute.HelpCenter.route) {
                            com.zuan.kernelmanager.next.feature.help.HelpCenterScreen(
                                navController = navController,
                                hazeState = backgroundHazeState
                            )
                        }
                    }
                }
            }
        }
        
        if (activeDialog != ActiveDialog.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .zkmHazeEffect(
                        state = contentHazeState,
                        style = ZkmHazeStyleCache.of(blurRadius = 25.dp, tint = HazeTint(Color.Transparent))
                    )
                    .background(Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { activeDialog = ActiveDialog.NONE },
                contentAlignment = Alignment.Center
            ) {
                // Area aman (taskbar PC mode, status bar, cutout) + tinggi maksimum dari ruang tersisa.
                com.zuan.kernelmanager.next.ui.adaptive.ZkmDialogSafeArea(horizontalMargin = 0.dp, verticalMargin = 8.dp) {
                    Box(modifier = Modifier.clickable(enabled = false) {}) {
                        when (activeDialog) {
                            ActiveDialog.TOOLS -> ToolsMenuContent(onDismiss = { activeDialog = ActiveDialog.NONE })
                            ActiveDialog.POWER -> PowerMenuContent(onDismiss = { activeDialog = ActiveDialog.NONE })
                            else -> {}
                        }
                    }
                }
            }
        }

        if (showUpdateDialog) {
            val currentUpdate = updateInfo
            if (currentUpdate != null) {
                UpdateDialog(
                    updateInfo = currentUpdate,
                    hazeState = contentHazeState,
                    onDismiss = { showUpdateDialog = false },
                    onUpdateClick = {
                        val downloadId = UpdateChecker.downloadUpdate(context, currentUpdate)
                        UpdateChecker.registerInstallReceiver(context, downloadId, currentUpdate.sha256)
                    }
                )
            }
        }

        if (useRail) {
            val railTabs = listOf(
                NavigationRoute.Overall,
                NavigationRoute.Dashboard,
                NavigationRoute.SoC,
                NavigationRoute.About
            )
            val railLabels = listOf(
                stringResource(R.string.nav_home),
                stringResource(R.string.nav_home),
                stringResource(R.string.nav_soc),
                stringResource(R.string.nav_about)
            )
            ZkmNavigationRail(
                items = railTabs.mapIndexed { i, r ->
                    ZkmRailItem(r.selectedIcon, r.unselectedIcon, railLabels[i])
                },
                selectedIndex = railTabs.indexOfFirst { it.route == currentRoute },
                onSelect = { index ->
                    val target = railTabs[index].route
                    if (currentRoute != target) {
                        navController.navigate(target) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                hazeState = backgroundHazeState,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        if (showBottomBar && !useRail) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(x = 0, y = bottomBarOffsetHeightPx.roundToInt()) }
                    // Landscape: capsule tidak melebar sepanjang layar, tetap kompak di tengah.
                    .then(if (zkmWindow.isWide) Modifier.widthIn(max = 420.dp) else Modifier)
            ) {
                when (navStyle) {
                    NavStyle.LIQUID_CAPSULE -> {
                        LiquidNavigationBar(
                            navController = navController, 
                            backdrop = navBackdrop,
                            labelState = navLabelState
                        )
                    }
                    NavStyle.LIQUID_CAPSULE_2 -> {
                        LiquidNavigationBar2(
                            navController = navController,
                            // [FIX] pakai state background (wallpaper ada di sini),
                            // bukan contentHazeState yang tidak berisi wallpaper —
                            // penyebab capsule 2 transparan saat custom background.
                            hazeState = backgroundHazeState,
                            labelState = navLabelState
                        )
                    }
                    NavStyle.CLASSIC_MATERIAL -> {
                        StandardNavigationBar(
                            navController = navController,
                            hazeState = backgroundHazeState,
                            labelState = navLabelState
                        )
                    }
                    else -> {}
                }
            }
        }

        AnimatedVisibility(visible = showRootDialog, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .zkmHazeEffect(state = contentHazeState, style = ZkmHazeStyleCache.of(blurRadius = 20.dp, tint = HazeTint(Color.Black.copy(alpha = 0.4f))))
                    .background(Color.Black.copy(alpha = 0.1f))
                    .clickable(enabled = false) {}
            )
        }
        AnimatedVisibility(
            visible = showRootDialog,
            enter = scaleIn(initialScale = 0.9f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .windowInsetsPadding(com.zuan.kernelmanager.next.ui.adaptive.zkmDialogWindowInsets())
                    .padding(24.dp)
            ) {
                GlassRootDialog(onUseAdb = {
                    WorkingModeManager.set(context, WorkingMode.ADB)
                })
            }
        }

        // Overlay dialog Android Liquid Glass — HARUS sibling terakhir (di atas nav bar).
        com.zuan.kernelmanager.next.ui.components.ZkmDialogHost(
            state = dialogHostState,
            backdrop = navBackdrop
        )
      }
    }
}

@Composable
fun GlassRootDialog(onUseAdb: () -> Unit = {}) {
    val dialogShape = RoundedCornerShape(28.dp)
    // Landscape: tinggi layar pendek -> batasi tinggi dialog & izinkan scroll supaya tombol terjangkau.
    val rootInsets = com.zuan.kernelmanager.next.ui.adaptive.zkmDialogWindowInsets()
    val rootDensity = LocalDensity.current
    val rootVerticalInset = with(rootDensity) { (rootInsets.getTop(rootDensity) + rootInsets.getBottom(rootDensity)).toDp() }
    val rootDialogMaxHeight = (rememberZkmWindowInfo().heightDp.dp - rootVerticalInset - 48.dp).coerceAtLeast(200.dp)
    Column(
        modifier = Modifier.widthIn(max = 360.dp)
            .heightIn(max = rootDialogMaxHeight)
            .clip(dialogShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape = dialogShape)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_app),
            contentDescription = stringResource(R.string.lp_a11y_app_logo),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Root Access Required",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Zuan Kernel Manager needs root access to modify kernel settings. Please root your device to continue.",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = { exitProcess(0) },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(text = "Exit Application", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(modifier = Modifier.height(10.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onUseAdb,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(text = stringResource(R.string.mode_root_denied_use_adb), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
    }
}
