/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.utils

import android.content.Context
import android.os.Process
import android.util.Log
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.mode.WorkingModeManager

object RootPersistenceUtils {
    private const val TAG = "ZKM_RootPersistence"

    /**
     * Applies full root-level persistence optimizations to exempt the app from
     * battery saver, doze, phantom process killer, app standby, and background restrictions.
     */
    fun applyRootExemptions(context: Context) {
        if (!WorkingModeManager.current.hasRoot) return
        val pkg = context.packageName

        val commands = listOf(
            // 1. Whitelist from battery optimization & doze
            "dumpsys deviceidle whitelist +$pkg",
            // 2. Allow unrestricted background execution
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            // 3. Set app standby bucket to EXEMPTED (so Android never throttles/kills it)
            "am set-standby-bucket $pkg EXEMPTED 2>/dev/null",
            // 4. Disable Phantom Process Killer (Android 12+)
            "device_config put activity_manager max_phantom_processes 2147483647 2>/dev/null",
            "settings put global settings_enable_monitor_phantom_procs 0 2>/dev/null"
        )

        for (cmd in commands) {
            KernelRootRuntime.executeBlocking(cmd)
        }

        // Also protect current process against Low Memory Killer
        protectCurrentProcess()
    }

    /**
     * Sets oom_score_adj to -1000 so the Linux OOM Killer will never terminate this process.
     */
    fun protectCurrentProcess() {
        if (!WorkingModeManager.current.hasRoot) return
        try {
            val pid = Process.myPid()
            KernelRootRuntime.executeBlocking("echo -1000 > /proc/$pid/oom_score_adj")
            Log.d(TAG, "Process $pid protected with oom_score_adj = -1000")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set oom_score_adj: ${e.message}")
        }
    }
}
