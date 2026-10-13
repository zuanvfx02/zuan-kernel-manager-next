/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.feature.home.menu

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zuan.kernelmanager.next.service.BatteryMonitorService
import com.zuan.kernelmanager.next.service.SmartCutoffService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- DATA CLASS BARU UNTUK GRAFIK REAL-TIME ---
data class ChartPoint(
    val timeStamp: Long,
    val value: Int,
    val isCharging: Boolean
)

class BatteryControllerViewModel(application: Application) : AndroidViewModel(application) {

    // --- DATA UTAMA ---
    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    private val _chargingStats = MutableStateFlow<ChargingStats?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _isAutoRefresh = MutableStateFlow(true)
    private val _selectedTab = MutableStateFlow(0)
    
    // --- DATA TRACKING & GRAFIK M3 EXPRESSIVE ---
    private val _peakTemp = MutableStateFlow(0f)
    private val _peakChargeCurrent = MutableStateFlow(0)
    private val _peakDischargeCurrent = MutableStateFlow(0)
    private val _currentHistory = MutableStateFlow<List<ChartPoint>>(emptyList())
    
    val peakTemp: StateFlow<Float> = _peakTemp
    val peakChargeCurrent: StateFlow<Int> = _peakChargeCurrent
    val peakDischargeCurrent: StateFlow<Int> = _peakDischargeCurrent
    val currentHistory: StateFlow<List<ChartPoint>> = _currentHistory
    
    // --- FEATURE STATES ---
    private val _chargingLimit = MutableStateFlow(100)
    private val _smartCutoffLimit = MutableStateFlow(80f)
    private val _smartCutoffEnabled = MutableStateFlow(false)
    private val _monitorEnabled = MutableStateFlow(false)
    private val _isFastChargeEnabled = MutableStateFlow(false)
    private val _isFastChargeSupported = MutableStateFlow(false)
    private val _isBypassEnabled = MutableStateFlow(false)
    private val _isBypassSupported = MutableStateFlow(false)
    private val _isBatterySaverEnabled = MutableStateFlow(false)
    private val _batteryCapacity = MutableStateFlow(100)
    private val _chargingSpeed = MutableStateFlow(2000)
    private val _isChargingEnabled = MutableStateFlow(true)
    private val _thermalSconfig = MutableStateFlow("0")
    private val _hasThermalSconfig = MutableStateFlow(false)
    private val _isSmartChargeSupported = MutableStateFlow(false)

    // --- CHARGE CONTROL (tab baru) ---
    private val _speedEnabled = MutableStateFlow(false)
    private val _speedTargetMa = MutableStateFlow(3000)
    private val _actualUpperMa = MutableStateFlow(0)
    private val _resumeBelow = MutableStateFlow(70f)
    private val _percentLimitSupported = MutableStateFlow(false)
    private val _bmsUevent = MutableStateFlow("")
    private val _usbUevent = MutableStateFlow("")

    val speedEnabled: StateFlow<Boolean> = _speedEnabled
    val speedTargetMa: StateFlow<Int> = _speedTargetMa
    val actualUpperMa: StateFlow<Int> = _actualUpperMa
    val resumeBelow: StateFlow<Float> = _resumeBelow
    val percentLimitSupported: StateFlow<Boolean> = _percentLimitSupported
    val bmsUevent: StateFlow<String> = _bmsUevent
    val usbUevent: StateFlow<String> = _usbUevent

    // Exposed Flows
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo
    val chargingStats: StateFlow<ChargingStats?> = _chargingStats
    val isLoading: StateFlow<Boolean> = _isLoading
    val selectedTab: StateFlow<Int> = _selectedTab
    val chargingLimit: StateFlow<Int> = _chargingLimit
    val smartCutoffLimit: StateFlow<Float> = _smartCutoffLimit
    val smartCutoffEnabled: StateFlow<Boolean> = _smartCutoffEnabled
    val monitorEnabled: StateFlow<Boolean> = _monitorEnabled
    val isFastChargeEnabled: StateFlow<Boolean> = _isFastChargeEnabled
    val isFastChargeSupported: StateFlow<Boolean> = _isFastChargeSupported
    val isBypassEnabled: StateFlow<Boolean> = _isBypassEnabled
    val isBypassSupported: StateFlow<Boolean> = _isBypassSupported
    val isBatterySaverEnabled: StateFlow<Boolean> = _isBatterySaverEnabled
    val batteryCapacity: StateFlow<Int> = _batteryCapacity
    val chargingSpeed: StateFlow<Int> = _chargingSpeed
    val isChargingEnabled: StateFlow<Boolean> = _isChargingEnabled
    val thermalSconfig: StateFlow<String> = _thermalSconfig
    val hasThermalSconfig: StateFlow<Boolean> = _hasThermalSconfig
    val isSmartChargeSupported: StateFlow<Boolean> = _isSmartChargeSupported

    val chargingSpeedOptions = listOf(500, 1000, 1500, 2000, 2500, 3000, 5000)
    val chargingLimitOptions = listOf(50, 60, 70, 80, 85, 90, 95, 100)
    
    private val PREFS_NAME = "battery_prefs"
    private val KEY_CUTOFF_LIMIT = "cutoff_limit"
    private val KEY_MONITOR_ENABLED = "monitor_enabled"
    private val KEY_RESUME_BELOW = "resume_below"
    private val KEY_SPEED_ENABLED = "speed_override_enabled"
    private val KEY_SPEED_TARGET = "speed_override_target"
    private val KEY_SPEED_DEFAULT = "speed_override_default_ma"

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                if (_isAutoRefresh.value) {
                    // Dijalankan berurutan: tidak ada refresh yang numpuk kalau shell root lambat.
                    withContext(Dispatchers.IO) { refreshData() }
                }
                delay(2000)
            }
        }
    }

    private fun refreshData() {
        val context = getApplication<Application>().applicationContext
        val info = BatteryControllerUtils.getBatteryInfo(context)
        val stats = BatteryControllerUtils.getChargingStats()

        if (info.temperature > _peakTemp.value) _peakTemp.value = info.temperature
        if (info.isCharging) {
            if (info.currentNow > _peakChargeCurrent.value) _peakChargeCurrent.value = info.currentNow
        } else {
            if (info.currentNow > _peakDischargeCurrent.value) _peakDischargeCurrent.value = info.currentNow
        }

        val historyList = _currentHistory.value.toMutableList()
        historyList.add(ChartPoint(System.currentTimeMillis(), info.currentNow, info.isCharging))
        if (historyList.size > 18) historyList.removeAt(0)

        // uevent hanya dibaca saat tab Charge Control (1) / Raw Data (2) terbuka, supaya tab lain tetap ringan.
        val tab = _selectedTab.value
        if (tab == 1 || tab == 2) {
            val bms = BatteryControllerUtils.readBatteryUevent()
            _bmsUevent.value = bms
            _actualUpperMa.value = BatteryControllerUtils.getActualUpperCurrentMa(BatteryControllerUtils.parseUevent(bms))
            if (tab == 2) _usbUevent.value = BatteryControllerUtils.readUsbUevent()
        }

        _batteryInfo.value = info
        _chargingStats.value = stats
        _currentHistory.value = historyList
    }

    fun resetPeaks() {
        val info = _batteryInfo.value
        _peakTemp.value = info?.temperature ?: 0f
        _peakChargeCurrent.value = 0
        _peakDischargeCurrent.value = 0
    }

    fun loadBatteryData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            
            val info = BatteryControllerUtils.getBatteryInfo(context)
            val stats = BatteryControllerUtils.getChargingStats()
            val capacity = BatteryControllerUtils.getBatteryCapacity()
            
            _peakTemp.value = info.temperature
            if (info.isCharging) _peakChargeCurrent.value = info.currentNow else _peakDischargeCurrent.value = info.currentNow
            
            val fastChargeSupported = BatteryControllerUtils.isFastChargeSupported()
            val fastChargeStatus = BatteryControllerUtils.getFastChargeStatus()
            val bypassSupported = BatteryControllerUtils.isBypassSupported()
            val bypassStatus = BatteryControllerUtils.getBypassStatus()
            val batterySaver = BatteryControllerUtils.isBatterySaverEnabled()
            val smartChargeSup = BatteryControllerUtils.isSmartChargeSupported()
            val hasThermal = BatteryControllerUtils.hasThermalSconfig()
            val thermalVal = if (hasThermal) BatteryControllerUtils.getThermalSconfig() else "0"
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val percentLimit = BatteryControllerUtils.isPercentChargeLimitSupported()
            val bmsRaw = BatteryControllerUtils.readBatteryUevent()
            val actualUpper = BatteryControllerUtils.getActualUpperCurrentMa(BatteryControllerUtils.parseUevent(bmsRaw))
            val savedResume = prefs.getFloat(KEY_RESUME_BELOW, 70f)
            val savedSpeedOn = prefs.getBoolean(KEY_SPEED_ENABLED, false)
            val savedSpeedTarget = prefs.getInt(KEY_SPEED_TARGET, if (actualUpper > 0) actualUpper else 3000)
            val savedCutoff = prefs.getFloat(KEY_CUTOFF_LIMIT, 80f)
            val savedMonitor = prefs.getBoolean(KEY_MONITOR_ENABLED, false)
            val savedCutoffEnabled = prefs.getBoolean("cutoff_enabled", false)
            
            val isCutoffRunning = isServiceRunning(context, SmartCutoffService::class.java)
            val isMonitorRunning = isServiceRunning(context, BatteryMonitorService::class.java)
            
            if (savedMonitor && !isMonitorRunning) {
                withContext(Dispatchers.Main) { toggleMonitor(context, true) }
            }
            if (savedCutoffEnabled && !isCutoffRunning) {
                withContext(Dispatchers.Main) { toggleSmartCutoff(context, true) }
            }

            withContext(Dispatchers.Main) {
                _batteryInfo.value = info
                _chargingStats.value = stats
                _batteryCapacity.value = capacity
                
                _isFastChargeSupported.value = fastChargeSupported
                _isFastChargeEnabled.value = fastChargeStatus
                _isBypassSupported.value = bypassSupported
                _isBypassEnabled.value = bypassStatus
                _isBatterySaverEnabled.value = batterySaver
                _isSmartChargeSupported.value = smartChargeSup
                _hasThermalSconfig.value = hasThermal
                _thermalSconfig.value = thermalVal
                
                _percentLimitSupported.value = percentLimit
                _actualUpperMa.value = actualUpper
                _resumeBelow.value = savedResume
                _speedEnabled.value = savedSpeedOn
                _speedTargetMa.value = savedSpeedTarget
                _chargingSpeed.value = savedSpeedTarget
                _smartCutoffLimit.value = savedCutoff
                _smartCutoffEnabled.value = savedCutoffEnabled || isCutoffRunning
                _monitorEnabled.value = savedMonitor
                
                _isLoading.value = false
            }
        }
    }

    fun onTabSelected(index: Int) { _selectedTab.value = index }
    fun setChargingSpeed(mA: Int) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setChargingSpeed(mA)) _chargingSpeed.value = mA } }
    fun setChargingLimit(percentage: Int) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setChargingLimit(percentage)) _chargingLimit.value = percentage } }
    
    fun setSmartCutoffLimit(context: Context, limit: Float) {
        _smartCutoffLimit.value = limit
        // Resume harus selalu di bawah stop (minimal selisih 2%).
        if (_resumeBelow.value > limit - 2f) _resumeBelow.value = (limit - 2f).coerceAtLeast(20f)
        persistProtection(context)
        pushProtectionToService(context)
    }

    fun setResumeBelow(context: Context, value: Float) {
        _resumeBelow.value = value.coerceAtMost(_smartCutoffLimit.value - 2f).coerceAtLeast(20f)
        persistProtection(context)
        pushProtectionToService(context)
    }

    private fun persistProtection(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_CUTOFF_LIMIT, _smartCutoffLimit.value)
            .putFloat(KEY_RESUME_BELOW, _resumeBelow.value)
            .apply()
    }

    private fun pushProtectionToService(context: Context) {
        if (!_smartCutoffEnabled.value) return
        val intent = Intent(context, SmartCutoffService::class.java).apply {
            action = SmartCutoffService.ACTION_UPDATE_LIMIT
            putExtra(SmartCutoffService.EXTRA_LIMIT, _smartCutoffLimit.value.toInt())
            putExtra(SmartCutoffService.EXTRA_RESUME, _resumeBelow.value.toInt())
        }
        context.startService(intent)
    }

    // --- CHARGING SPEED OVERRIDE ---
    /** Geser slider: hanya update UI. Tulis ke kernel dilakukan saat slider dilepas (commitSpeed). */
    fun previewSpeed(mA: Int) { _speedTargetMa.value = mA }

    fun commitSpeed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SPEED_TARGET, _speedTargetMa.value).apply()
        if (_speedEnabled.value) applySpeed(_speedTargetMa.value)
    }

    fun toggleSpeedOverride(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled) {
                // Simpan nilai bawaan sekali supaya OFF bisa mengembalikannya.
                if (prefs.getInt(KEY_SPEED_DEFAULT, 0) <= 0) {
                    val def = BatteryControllerUtils.getUsbCurrentMaxMa()
                        .takeIf { it > 0 } ?: _actualUpperMa.value
                    if (def > 0) prefs.edit().putInt(KEY_SPEED_DEFAULT, def).apply()
                }
                val ok = BatteryControllerUtils.setChargingSpeed(_speedTargetMa.value)
                if (ok) {
                    prefs.edit().putBoolean(KEY_SPEED_ENABLED, true).apply()
                    _speedEnabled.value = true
                }
            } else {
                val def = prefs.getInt(KEY_SPEED_DEFAULT, 0)
                val ok = if (def > 0) BatteryControllerUtils.setChargingSpeed(def) else true
                if (ok) {
                    prefs.edit().putBoolean(KEY_SPEED_ENABLED, false).apply()
                    _speedEnabled.value = false
                }
            }
        }
    }

    private fun applySpeed(mA: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (BatteryControllerUtils.setChargingSpeed(mA)) _chargingSpeed.value = mA
        }
    }

    fun toggleSmartCutoff(context: Context, enable: Boolean) {
        _smartCutoffEnabled.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("cutoff_enabled", enable).apply()
        val intent = Intent(context, SmartCutoffService::class.java)
        if (enable) {
            intent.putExtra(SmartCutoffService.EXTRA_LIMIT, _smartCutoffLimit.value.toInt())
            intent.putExtra(SmartCutoffService.EXTRA_RESUME, _resumeBelow.value.toInt())
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } else {
            intent.action = SmartCutoffService.ACTION_STOP_SERVICE
            context.startService(intent)
        }
    }
    
    fun toggleMonitor(context: Context, enable: Boolean) {
        _monitorEnabled.value = enable
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_MONITOR_ENABLED, enable).apply()
        if (enable) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(500) 
                withContext(Dispatchers.Main) {
                    val intent = Intent(context, BatteryMonitorService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                }
            }
        } else {
            val intent = Intent(context, BatteryMonitorService::class.java)
            intent.action = BatteryMonitorService.ACTION_STOP
            context.startService(intent)
        }
    }

    fun toggleFastCharge(enabled: Boolean) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setFastCharge(enabled)) _isFastChargeEnabled.value = enabled } }
    fun toggleBypass(enabled: Boolean) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setBypassCharging(enabled)) _isBypassEnabled.value = enabled } }
    fun toggleBatterySaver(enabled: Boolean) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setBatterySaver(enabled)) _isBatterySaverEnabled.value = enabled } }
    fun toggleCharging(enabled: Boolean) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setChargingEnabled(enabled)) _isChargingEnabled.value = enabled } }
    fun updateThermalSconfig(value: String) { viewModelScope.launch(Dispatchers.IO) { if (BatteryControllerUtils.setThermalSconfig(value)) _thermalSconfig.value = value } }
    
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }
}
