@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.zuan.kernelmanager.next.feature.fps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zuan.kernelmanager.next.R
import com.zuan.kernelmanager.next.feature.settings.SettingsPreference
import com.zuan.kernelmanager.next.library.service.FpsOverlayController
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.zuan.kernelmanager.next.ui.theme.zkmIsDarkTheme
import com.zuan.kernelmanager.next.ui.components.zkmGlassCard
import com.zuan.kernelmanager.next.ui.components.zkmGlassTint
import com.zuan.kernelmanager.next.ui.components.LocalZkmHazeState
import com.zuan.kernelmanager.next.ui.theme.zkmIconShape
import com.zuan.kernelmanager.next.ui.theme.ZkmIconGlyph
import com.zuan.kernelmanager.next.ui.components.ZkmSwitch
import com.zuan.kernelmanager.next.ui.components.ZkmSlider

private data class OverlayMetric(val label: String, val key: String, val enabled: Boolean, val onToggle: (Boolean) -> Unit)

@Composable
fun FpsManagerOverlayContent(
    cardBg: Color,
    contentColor: Color,
    subContentColor: Color,
    activeColor: Color,
    elevation: Dp,
    isGlassActive: Boolean = false,
    hazeState: HazeState,
    glassModifier: Modifier = Modifier,
    topPadding: Dp
) {
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreference.getInstance(context) }
    var isOverlayEnabled by remember { mutableStateOf(FpsOverlayController.isRunning.value) }
    var selectedStyle by remember { mutableIntStateOf(settingsPrefs.fpsStyle.value) }
    var pcVariant by remember { mutableIntStateOf(settingsPrefs.fpsPcVariant.value) }
    var androidOrientation by remember { mutableIntStateOf(settingsPrefs.fpsOrientation.value) }
    var selectedColorHex by remember { mutableStateOf(settingsPrefs.fpsColor.value) }
    var textSize by remember { mutableFloatStateOf(settingsPrefs.fpsSize.value) }
    var widthScale by remember { mutableFloatStateOf(settingsPrefs.fpsWidthScale.value) }
    var bgAlpha by remember { mutableFloatStateOf(settingsPrefs.fpsAlpha.value) }
    var cornerRadius by remember { mutableFloatStateOf(settingsPrefs.fpsRadius.value) }
    var bgMode by remember { mutableIntStateOf(settingsPrefs.fpsBgMode.value) }
    var showBorder by remember { mutableStateOf(settingsPrefs.fpsBorder.value) }
    var showShadow by remember { mutableStateOf(settingsPrefs.fpsShadow.value) }
    var sampleInterval by remember { mutableLongStateOf(settingsPrefs.fpsInterval.value) }
    var position by remember { mutableStateOf(settingsPrefs.fpsPosition.value) }

    var showFps by remember { mutableStateOf(settingsPrefs.fpsShowFps.value) }
    var showFrameTime by remember { mutableStateOf(settingsPrefs.fpsShowFrameTime.value) }
    var showOnePercent by remember { mutableStateOf(settingsPrefs.fpsShowOnePercent.value) }
    var showZeroOnePercent by remember { mutableStateOf(settingsPrefs.fpsShowZeroOnePercent.value) }
    var showCpu by remember { mutableStateOf(settingsPrefs.fpsShowCpu.value) }
    var showCpuFreq by remember { mutableStateOf(settingsPrefs.fpsShowCpuFreq.value) }
    var showGpuLoad by remember { mutableStateOf(settingsPrefs.fpsShowGpuUsage.value) }
    var showGpuFreq by remember { mutableStateOf(settingsPrefs.fpsShowGpuFreq.value) }
    var showWatts by remember { mutableStateOf(settingsPrefs.fpsShowWatts.value) }
    var showTemp by remember { mutableStateOf(settingsPrefs.fpsShowTemp.value) }
    var showRam by remember { mutableStateOf(settingsPrefs.fpsShowRam.value) }
    var showRamPercent by remember { mutableStateOf(settingsPrefs.fpsShowRamPercent.value) }
    var showBattery by remember { mutableStateOf(settingsPrefs.fpsShowBattery.value) }
    var showRefresh by remember { mutableStateOf(settingsPrefs.fpsShowRefresh.value) }
    var showRender by remember { mutableStateOf(settingsPrefs.fpsShowRender.value) }
    var showJank by remember { mutableStateOf(settingsPrefs.fpsShowJank.value) }
    var showRecord by remember { mutableStateOf(settingsPrefs.fpsShowRecord.value) }
    var showSparkline by remember { mutableStateOf(settingsPrefs.fpsShowSpark.value) }
    var showSizeControls by remember { mutableStateOf(settingsPrefs.fpsShowSizeControls.value) }
    var showColorDialog by remember { mutableStateOf(false) }

    // [BUG FIX] Sebelumnya polling `while(true) + delay(700)` untuk membaca
    // StateFlow — boros CPU/baterai dan bisa telat 700ms. StateFlow cukup
    // di-collect: update diterima instan tanpa loop.
    LaunchedEffect(Unit) {
        FpsOverlayController.isRunning.collect { running -> isOverlayEnabled = running }
    }

    fun updateService(pos: String? = null) {
        settingsPrefs.setFpsStyle(selectedStyle)
        settingsPrefs.setFpsPcVariant(pcVariant)
        settingsPrefs.setFpsOrientation(androidOrientation)
        settingsPrefs.setFpsColor(selectedColorHex)
        settingsPrefs.setFpsSize(textSize)
        settingsPrefs.setFpsWidthScale(widthScale)
        settingsPrefs.setFpsAlpha(bgAlpha)
        settingsPrefs.setFpsRadius(cornerRadius)
        settingsPrefs.setFpsBgMode(bgMode)
        settingsPrefs.setFpsBorder(showBorder)
        settingsPrefs.setFpsShadow(showShadow)
        settingsPrefs.setFpsInterval(sampleInterval)
        settingsPrefs.setFpsPosition(pos ?: position)

        settingsPrefs.setFpsShowFps(showFps)
        settingsPrefs.setFpsShowFrameTime(showFrameTime)
        settingsPrefs.setFpsShowOnePercent(showOnePercent)
        settingsPrefs.setFpsShowZeroOnePercent(showZeroOnePercent)
        settingsPrefs.setFpsShowCpu(showCpu)
        settingsPrefs.setFpsShowCpuFreq(showCpuFreq)
        settingsPrefs.setFpsShowGpuUsage(showGpuLoad)
        settingsPrefs.setFpsShowGpuFreq(showGpuFreq)
        settingsPrefs.setFpsShowWatts(showWatts)
        settingsPrefs.setFpsShowTemp(showTemp)
        settingsPrefs.setFpsShowRam(showRam)
        settingsPrefs.setFpsShowRamPercent(showRamPercent)
        settingsPrefs.setFpsShowBattery(showBattery)
        settingsPrefs.setFpsShowRefresh(showRefresh)
        settingsPrefs.setFpsShowRender(showRender)
        settingsPrefs.setFpsShowJank(showJank)
        settingsPrefs.setFpsShowRecord(showRecord)
        settingsPrefs.setFpsShowSpark(showSparkline)
        settingsPrefs.setFpsShowSizeControls(showSizeControls)

        if (!isOverlayEnabled) return
        val extras = buildMap<String, Any?> {
            put("STYLE", selectedStyle); put("PC_VARIANT", pcVariant); put("ORIENTATION", androidOrientation)
            put("COLOR", selectedColorHex); put("SIZE", textSize); put("WIDTH_SCALE", widthScale); put("ALPHA", bgAlpha)
            put("RADIUS", cornerRadius); put("BG_MODE", bgMode); put("BORDER", showBorder); put("SHADOW", showShadow)
            put("INTERVAL", sampleInterval.toInt()); put("POSITION", pos ?: position)
            put("SHOW_FPS", showFps); put("SHOW_FRAMETIME", showFrameTime); put("SHOW_1LOW", showOnePercent); put("SHOW_01LOW", showZeroOnePercent)
            put("SHOW_CPU", showCpu); put("SHOW_CPU_FREQ", showCpuFreq); put("SHOW_GPU_LOAD", showGpuLoad); put("SHOW_GPU_FREQ", showGpuFreq)
            put("SHOW_WATTS", showWatts); put("SHOW_TEMP", showTemp); put("SHOW_RAM", showRam); put("SHOW_RAM_PERCENT", showRamPercent)
            put("SHOW_BATTERY", showBattery); put("SHOW_REFRESH", showRefresh); put("SHOW_RENDER", showRender); put("SHOW_JANK", showJank)
            put("SHOW_RECORD", showRecord); put("SHOW_SPARK", showSparkline); put("SHOW_SIZE_CONTROLS", showSizeControls)
        }
        FpsOverlayController.start(context, extras)
    }

    val metrics = listOf(
        OverlayMetric(stringResource(R.string.fps_metric_fps), "fps", showFps) { showFps = it }, OverlayMetric(stringResource(R.string.fps_metric_frame_time), "frame", showFrameTime) { showFrameTime = it },
        OverlayMetric(stringResource(R.string.fps_metric_one_percent_low), "1low", showOnePercent) { showOnePercent = it }, OverlayMetric(stringResource(R.string.fps_metric_zero_one_percent_low), "01low", showZeroOnePercent) { showZeroOnePercent = it },
        OverlayMetric(stringResource(R.string.fps_metric_cpu_load), "cpu", showCpu) { showCpu = it }, OverlayMetric(stringResource(R.string.fps_metric_cpu_clock), "cpuf", showCpuFreq) { showCpuFreq = it },
        OverlayMetric(stringResource(R.string.fps_metric_gpu_load), "gpu", showGpuLoad) { showGpuLoad = it }, OverlayMetric(stringResource(R.string.fps_metric_gpu_clock), "gpuf", showGpuFreq) { showGpuFreq = it },
        OverlayMetric(stringResource(R.string.fps_metric_power), "watt", showWatts) { showWatts = it }, OverlayMetric(stringResource(R.string.fps_metric_soc_temp), "temp", showTemp) { showTemp = it },
        OverlayMetric(stringResource(R.string.fps_metric_ram), "ram", showRam) { showRam = it }, OverlayMetric(stringResource(R.string.fps_metric_ram_percent), "ramp", showRamPercent) { showRamPercent = it },
        OverlayMetric(stringResource(R.string.fps_metric_battery), "bat", showBattery) { showBattery = it }, OverlayMetric(stringResource(R.string.fps_metric_refresh), "hz", showRefresh) { showRefresh = it },
        OverlayMetric(stringResource(R.string.fps_metric_renderer), "api", showRender) { showRender = it }, OverlayMetric(stringResource(R.string.fps_metric_jank), "jank", showJank) { showJank = it }
    )

    LazyColumn(
        contentPadding = PaddingValues(top = topPadding + 8.dp, bottom = 100.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { OverlayMasterSwitch(isOverlayEnabled, activeColor, contentColor, subContentColor, isGlassActive, glassModifier) { checked ->
            if (checked) {
                if (!Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, context.getString(R.string.lp_toast_grant_overlay), Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    isOverlayEnabled = true
                    updateService("TopLeft")
                }
            } else { FpsOverlayController.stop(context); isOverlayEnabled = false }
        } }

        item {
            val alpha by animateFloatAsState(if (isOverlayEnabled) 1f else 0.48f, label = "overlayAlpha")
            Column(Modifier.alpha(alpha)) {
                Spacer(Modifier.height(18.dp))
                FpsOverlaySectionTitle(stringResource(R.string.fps_overlay_presets), activeColor)
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(stringResource(R.string.fps_style_android) to 0, stringResource(R.string.fps_style_pc) to 1, stringResource(R.string.fps_style_mini) to 2).forEach { (label, value) ->
                        StyleChip(label, value, selectedStyle, activeColor) { selectedStyle = value; updateService() }
                    }
                }

                AnimatedVisibility(selectedStyle == 1) {
                    Column {
                        Spacer(Modifier.height(16.dp)); FpsOverlaySectionTitle(stringResource(R.string.fps_pc_overlay_variants), activeColor)
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val variants = listOf(
                                stringResource(R.string.fps_preset_nvidia_counter) to 0,
                                stringResource(R.string.fps_preset_nvidia_detailed) to 1,
                                stringResource(R.string.fps_preset_afterburner) to 2,
                                stringResource(R.string.fps_preset_rtss_compact) to 3,
                                stringResource(R.string.fps_preset_steam_hud) to 4,
                                stringResource(R.string.fps_preset_esports_hud) to 5,
                                stringResource(R.string.fps_preset_telemetry) to 6
                            )
                            variants.forEach { (label, value) -> StyleChip(label, value, pcVariant, activeColor) { pcVariant = value; updateService() } }
                        }
                        Text(stringResource(R.string.fps_pc_overlay_desc), color = subContentColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }

                AnimatedVisibility(selectedStyle == 0) {
                    Column {
                        Spacer(Modifier.height(16.dp)); FpsOverlaySectionTitle(stringResource(R.string.fps_android_layout), activeColor)
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StyleChip(stringResource(R.string.fps_vertical), 0, androidOrientation, activeColor) { androidOrientation = 0; updateService() }
                            StyleChip(stringResource(R.string.fps_horizontal), 1, androidOrientation, activeColor) { androidOrientation = 1; updateService() }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp)); FpsOverlaySectionTitle(stringResource(R.string.fps_telemetry_metrics), activeColor)
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.forEach { metric -> MetricChip(metric.label, metric.enabled, activeColor) { metric.onToggle(it); updateService() } }
                }

                Spacer(Modifier.height(20.dp)); FpsOverlaySectionTitle(stringResource(R.string.fps_position), activeColor)
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                            "TopLeft" to stringResource(R.string.fps_position_top_left),
                            "TopCenter" to stringResource(R.string.fps_position_top_center),
                            "TopRight" to stringResource(R.string.fps_position_top_right),
                            "CenterLeft" to stringResource(R.string.fps_position_center_left),
                            "Center" to stringResource(R.string.fps_position_center),
                            "CenterRight" to stringResource(R.string.fps_position_center_right),
                            "BottomLeft" to stringResource(R.string.fps_position_bottom_left),
                            "BottomCenter" to stringResource(R.string.fps_position_bottom_center),
                            "BottomRight" to stringResource(R.string.fps_position_bottom_right)
                        ).forEach { (key, label) ->
                        StyleChip(label, if(position == key) 1 else 0, 1, activeColor) { position = key; updateService(key) }
                    }
                }
                Text(stringResource(R.string.fps_overlay_drag_desc), color = subContentColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                Spacer(Modifier.height(12.dp)); FpsOverlaySectionTitle(stringResource(R.string.fps_sampling), activeColor)
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250L, 500L, 1000L, 2000L).forEach { ms ->
                        StyleChip(stringResource(R.string.fps_sampling_interval_ms, ms), if(sampleInterval == ms) 1 else 0, 1, activeColor) { sampleInterval = ms; updateService() }
                    }
                }
                Text(stringResource(R.string.fps_sampling_desc), color = subContentColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                Spacer(Modifier.height(16.dp)); FpsOverlaySectionTitle(stringResource(R.string.sect_appearance), activeColor)
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = cardBg)) {
                    Column(Modifier.padding(16.dp)) {
                        AppearanceSlider(Icons.Default.FormatSize, stringResource(R.string.fps_text_size), textSize, 8f..30f, stringResource(R.string.fps_appearance_value_sp, textSize.toInt()), activeColor, { textSize = it }, { updateService() })
                        Spacer(Modifier.height(14.dp)); AppearanceSlider(Icons.Default.AspectRatio, stringResource(R.string.fps_width_scale), widthScale, 0f..2.5f, stringResource(R.string.fps_appearance_value_scale, widthScale), activeColor, { widthScale = it }, { updateService() })
                        Spacer(Modifier.height(14.dp)); AppearanceSlider(Icons.Default.Opacity, stringResource(R.string.fps_background_alpha), bgAlpha, 0f..1f, stringResource(R.string.fps_appearance_value_percent, (bgAlpha * 100).toInt()), activeColor, { bgAlpha = it }, { updateService() })
                        Spacer(Modifier.height(14.dp)); AppearanceSlider(Icons.Default.RoundedCorner, stringResource(R.string.fps_corner_radius), cornerRadius, 0f..28f, stringResource(R.string.fps_appearance_value_dp, cornerRadius.toInt()), activeColor, { cornerRadius = it }, { updateService() })
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricChip(stringResource(R.string.fps_solid), bgMode == 0, activeColor) { bgMode = 0; updateService() }
                            MetricChip(stringResource(R.string.fps_soft_glass), bgMode == 1, activeColor) { bgMode = 1; updateService() }
                            MetricChip(stringResource(R.string.fps_transparent), bgMode == 2, activeColor) { bgMode = 2; updateService() }
                            MetricChip(stringResource(R.string.fps_border), showBorder, activeColor) { showBorder = it; updateService() }
                            MetricChip(stringResource(R.string.fps_shadow), showShadow, activeColor) { showShadow = it; updateService() }
                            MetricChip(stringResource(R.string.fps_record_control), showRecord, activeColor) { showRecord = it; updateService() }
                            MetricChip(stringResource(R.string.fps_sparkline), showSparkline, activeColor) { showSparkline = it; updateService() }
                            MetricChip(stringResource(R.string.fps_size_controls), showSizeControls, activeColor) { showSizeControls = it; updateService() }
                        }
                    }
                }

                Text(stringResource(R.string.fps_size_controls_desc), color = subContentColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                Spacer(Modifier.height(12.dp)); FpsOverlaySectionTitle(stringResource(R.string.fps_color_theme), activeColor)
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { showColorDialog = true }, enabled = isOverlayEnabled) {
                        Icon(Icons.Default.Palette, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.fps_custom_color))
                    }
                    Spacer(Modifier.width(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(listOf("#00FF00" to Color.Green, "#00BFFF" to Color.Cyan, "#FFB000" to Color(0xFFFFB000), "#FF4D9D" to Color(0xFFFF4D9D), "#FFFFFF" to Color.White)) { (hex, color) ->
                            ColorPresetCircle(color, selectedColorHex == hex) { selectedColorHex = hex; updateService() }
                        }
                    }
                }
            }
        }
    }

    if (showColorDialog) SimpleColorPickerDialog(
        initialColor = runCatching { Color(android.graphics.Color.parseColor(selectedColorHex)) }.getOrDefault(Color.Green),
        onDismiss = { showColorDialog = false }, activeColor = activeColor, cardBg = cardBg, contentColor = contentColor,
        onColorSelected = { color -> selectedColorHex = String.format("#%06X", 0xFFFFFF and color.toArgb()); showColorDialog = false; updateService() }
    )
}

@Composable
fun StyleChip(label: String, value: Int, selectedValue: Int, activeColor: Color, onClick: () -> Unit) {
    FilterChip(selected = value == selectedValue, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = activeColor.copy(alpha = 0.2f), selectedLabelColor = activeColor),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = value == selectedValue, selectedBorderColor = activeColor))
}

@Composable
fun OverlayMasterSwitch(isEnabled: Boolean, activeColor: Color, contentColor: Color, subContentColor: Color, isGlassActive: Boolean = false, glassModifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(zkmIconShape(CircleShape)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    ZkmIconGlyph(icon = if (isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff, tint = if(isEnabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant, iconSize = 24.dp)
                }
                Spacer(Modifier.width(16.dp)); Column {
                    Text(if(isEnabled) stringResource(R.string.fps_overlay_active) else stringResource(R.string.fps_overlay_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                    Text(if(isEnabled) stringResource(R.string.fps_overlay_running_desc) else stringResource(R.string.fps_overlay_enable_desc), style = MaterialTheme.typography.bodyMedium, color = subContentColor)
                }
            }
            ZkmSwitch(checked = isEnabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = activeColor))
        }
    }
    // [FIX] glassModifier sudah membawa clip+blur+border sendiri; border ekstra
    // di sini membuat efek "double card" (card di dalam card).
    if (isGlassActive) Box(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp).zkmGlassCard(true, LocalZkmHazeState.current, RoundedCornerShape(28.dp), zkmGlassTint(if (isEnabled) activeColor else MaterialTheme.colorScheme.surfaceContainer, zkmIsDarkTheme(), 0.35f), 24.dp)) { body() }
    else Card(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp), shape=RoundedCornerShape(28.dp), colors=CardDefaults.cardColors(containerColor=if(isEnabled) activeColor.copy(alpha=.15f) else MaterialTheme.colorScheme.surfaceContainer)) { body() }
}

@Composable
fun MetricChip(label: String, selected: Boolean, activeColor: Color, onToggle: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onToggle(!selected) }, label = { Text(label) }, leadingIcon = if(selected) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null,
        shape=RoundedCornerShape(12.dp), colors=FilterChipDefaults.filterChipColors(selectedContainerColor=activeColor.copy(alpha=.2f), selectedLabelColor=activeColor, selectedLeadingIconColor=activeColor))
}

@Composable
fun AppearanceSlider(icon: ImageVector, label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, displayValue: String, activeColor: Color, onValueChange: (Float) -> Unit, onFinished: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(icon, null, Modifier.size(20.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); Text(label, style=MaterialTheme.typography.labelLarge) }
            Text(displayValue, style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.Bold, color=activeColor)
        }
        ZkmSlider(value=value, onValueChange=onValueChange, valueRange=valueRange, onValueChangeFinished=onFinished)
    }
}

@Composable
fun ColorPresetCircle(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(color).border(if(selected) 3.dp else 1.dp, if(selected) Color.White else Color.White.copy(alpha=.2f), CircleShape).clickable(onClick=onClick), contentAlignment=Alignment.Center) {
        if(selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint=if(color.luminance() > .5f) Color.Black else Color.White)
    }
}

@Composable
fun FpsOverlaySectionTitle(text: String, color: Color) {
    Text(text, style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.Bold, color=color, modifier=Modifier.padding(horizontal=16.dp, vertical=6.dp))
}

@Composable
fun SimpleColorPickerDialog(initialColor: Color, onDismiss: () -> Unit, activeColor: Color, cardBg: Color, contentColor: Color, onColorSelected: (Color) -> Unit) {
    var color by remember(initialColor) { mutableStateOf(initialColor) }
    com.zuan.kernelmanager.next.ui.components.ZkmDialog(onDismissRequest = onDismiss) {
        com.zuan.kernelmanager.next.ui.components.ZkmDialogPanel(containerColor = cardBg) {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(22.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                Text(stringResource(R.string.fps_color_dialog_title), style=MaterialTheme.typography.titleLarge, color=contentColor, fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.size(76.dp).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha=.25f), CircleShape))
                Spacer(Modifier.height(10.dp))
                Text(String.format("#%06X", 0xFFFFFF and color.toArgb()), color=contentColor, fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.White).forEach { c -> Box(Modifier.size(34.dp).clip(CircleShape).background(c).clickable { color=c }) }
                }
                Spacer(Modifier.height(18.dp)); Button(onClick={onColorSelected(color)}, colors=ButtonDefaults.buttonColors(containerColor=activeColor)) { Text(stringResource(R.string.btn_apply)) }
            }
        }
    }
}
