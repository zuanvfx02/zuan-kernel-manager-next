package com.zuan.kernelmanager.next.library.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-safe boundary for controlling the FPS overlay service. */
object FpsOverlayController {
    private const val SERVICE_CLASS = "com.zuan.kernelmanager.next.service.FpsOverlayService"
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(context: Context, extras: Map<String, Any?> = emptyMap()) {
        com.zuan.kernelmanager.next.feature.settings.SettingsPreference.getInstance(context).setFpsOverlayEnabled(true)
        val intent = Intent().setClassName(context, SERVICE_CLASS)
        extras.forEach { (key, value) ->
            when (value) {
                is String -> intent.putExtra(key, value)
                is Int -> intent.putExtra(key, value)
                is Float -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        com.zuan.kernelmanager.next.feature.settings.SettingsPreference.getInstance(context).setFpsOverlayEnabled(false)
        context.stopService(Intent().setClassName(context, SERVICE_CLASS))
        _isRunning.value = false
    }

    fun setRunning(value: Boolean) {
        _isRunning.value = value
    }
}
