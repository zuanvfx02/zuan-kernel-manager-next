/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zuan.kernelmanager.next.library.device.gpu

import com.zuan.kernelmanager.next.library.shell.RootIpcManager
import com.zuan.kernelmanager.next.core.kernel.KernelRootRuntime
import com.zuan.kernelmanager.next.core.kernel.KernelControlEngine


object AdrenoUtils {

    // === HELPER IPC UNTUK VIEWMODEL ===
    // Jalur cepat = Binder daemon. Kalau daemon belum tersambung (mis. saat ViewModel pertama kali
    // load), JANGAN jatuh ke File(path) milik UID aplikasi: sysfs/procfs tidak terbaca oleh app
    // sehingga hasilnya "tidak ada" dan fitur terlihat tidak didukung. Fallback-nya root shell.
    fun checkExists(path: String): Boolean {
        val remote = runCatching { RootIpcManager.ipc?.nodeExists(path) }.getOrNull()
        return remote ?: KernelRootRuntime.nodeExistsBlocking(path)
    }

    fun readData(path: String): String {
        val remote = runCatching { RootIpcManager.ipc?.readNode(path) }.getOrNull()
        if (remote != null) return remote.trim()
        return KernelRootRuntime.readNodeBlocking(path).trim()
    }

    fun writeData(path: String, value: String, verify: Boolean = true): Boolean {
        return try {
            KernelControlEngine.setKernelNodeBlocking(path, value, verify = verify).success
        } catch (e: Exception) { false }
    }

    fun listDirs(path: String): List<String> {
        val remote = runCatching { RootIpcManager.ipc?.listDirectories(path) }.getOrNull()
        return remote ?: KernelRootRuntime.listDirectoriesBlocking(path)
    }

    // === ADRENO PATHS ===
    const val KGSL_3D0_DIR = "/sys/class/kgsl/kgsl-3d0"
    const val MIN_FREQ_GPU = "$KGSL_3D0_DIR/min_clock_mhz"
    const val MAX_FREQ_GPU = "$KGSL_3D0_DIR/max_clock_mhz"
    const val CURRENT_FREQ_GPU = "$KGSL_3D0_DIR/gpuclk"
    const val AVAILABLE_FREQ_GPU = "$KGSL_3D0_DIR/gpu_available_frequencies"
    const val GOV_GPU = "$KGSL_3D0_DIR/devfreq/governor"
    const val AVAILABLE_GOV_GPU = "$KGSL_3D0_DIR/devfreq/available_governors"
    
    // Node berbasis Hz (satuan pasti): devfreq min/max_freq dan max_gpuclk. Dipakai sebagai jalur
    // utama untuk menulis, dan sebagai fallback baca bila node *_clock_mhz tidak ada di kernel.
    const val MAX_GPUCLK = "$KGSL_3D0_DIR/max_gpuclk"
    const val MIN_GPUCLK = "$KGSL_3D0_DIR/min_gpuclk"
    const val DEVFREQ_MIN_FREQ = "$KGSL_3D0_DIR/devfreq/min_freq"
    const val DEVFREQ_MAX_FREQ = "$KGSL_3D0_DIR/devfreq/max_freq"
    const val DEVFREQ_AVAILABLE_FREQ = "$KGSL_3D0_DIR/devfreq/available_frequencies"

    const val ADRENO_BOOST = "$KGSL_3D0_DIR/devfreq/adrenoboost"
    const val GPU_THROTTLING = "$KGSL_3D0_DIR/throttling"
    
    // === QCOM BUS DCVS PATHS ===
    const val BUS_DCVS_DIR = "/sys/devices/system/cpu/bus_dcvs"
    
    // === DEVFREQ BUSMON & UFSHC ===
    const val DEVFREQ_DIR = "/sys/class/devfreq"
    const val BUSMON_DIR = "$DEVFREQ_DIR/kgsl-busmon"

    // === ADRENO IDLER & SIMPLE GPU CONSTANTS (FIXED) ===
    const val IDLER_DIR = "/sys/module/adreno_idler/parameters"
    const val IDLER_ACTIVE = "$IDLER_DIR/adreno_idler_active"
    const val IDLER_IDLEWAIT = "$IDLER_DIR/adreno_idler_idlewait"
    const val IDLER_DOWNDIFF = "$IDLER_DIR/adreno_idler_downdifferential"
    const val IDLER_WORKLOAD = "$IDLER_DIR/adreno_idler_idleworkload"
    
    const val SIMPLE_GPU_DIR = "/sys/module/simple_gpu_algorithm/parameters"
    const val SIMPLE_GPU_ACTIVATE = "$SIMPLE_GPU_DIR/simple_gpu_activate"
    const val SIMPLE_GPU_LAZINESS = "$SIMPLE_GPU_DIR/simple_laziness"
    const val SIMPLE_RAMP_THRESHOLD = "$SIMPLE_GPU_DIR/simple_ramp_threshold"

    fun isAdreno(): Boolean = checkExists(KGSL_3D0_DIR)
    fun hasAdrenoIdler(): Boolean = checkExists(IDLER_DIR)
    fun hasSimpleGpu(): Boolean = checkExists(SIMPLE_GPU_DIR)
    fun hasGpuThrottling(): Boolean = checkExists(GPU_THROTTLING)
    fun hasBusDcvs(): Boolean = checkExists(BUS_DCVS_DIR)
    fun hasBusmon(): Boolean = checkExists(BUSMON_DIR)

    // Pencari UFSHC Dinamis
    fun getUfshcPath(): String? {
        val dirs = listDirs(DEVFREQ_DIR)
        val ufshcDir = dirs.find { it.endsWith(".ufshc", ignoreCase = true) || it.endsWith("ufshc", ignoreCase = true) }
        return if (ufshcDir != null) "$DEVFREQ_DIR/$ufshcDir" else null
    }

    /** Hz (>= 1_000_000) -> MHz; angka kecil dianggap sudah MHz. */
    private fun toMhz(raw: String): String {
        val v = raw.trim().toLongOrNull() ?: return raw.trim()
        return if (v >= 1_000_000L) (v / 1_000_000L).toString() else v.toString()
    }

    fun readFreqGPU(filePath: String): String {
        val out = readData(filePath)
        return if (out.isNotEmpty()) toMhz(out) else ""
    }

    private fun firstMhz(vararg paths: String): String {
        for (p in paths) {
            val v = readFreqGPU(p)
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    /** Min/Max dalam MHz; fallback ke node Hz kalau kernel tidak punya *_clock_mhz. */
    fun readMinFreqMHz(): String = firstMhz(MIN_FREQ_GPU, MIN_GPUCLK, DEVFREQ_MIN_FREQ)
    fun readMaxFreqMHz(): String = firstMhz(MAX_FREQ_GPU, MAX_GPUCLK, DEVFREQ_MAX_FREQ)

    /**
     * Menulis batas clock GPU (MHz dari UI).
     *
     * Bug lama: nilai SELALU dikonversi ke Hz lalu ditulis ke min_clock_mhz/max_clock_mhz (node
     * bersatuan MHz), sehingga write tidak berefek / ter-clamp. Sekarang urutan tulis memakai node
     * yang satuannya pasti (Hz) lebih dulu, node *_clock_mhz (MHz) hanya cadangan terakhir.
     * Urutan min/max juga dijaga supaya kernel tidak pernah melihat min > max.
     */
    fun setGpuClockMHz(target: String, mhz: Long): Boolean {
        if (mhz <= 0L) return false
        val isMin = target == "min"
        val hz = mhz * 1_000_000L

        val curMin = readMinFreqMHz().toLongOrNull()
        val curMax = readMaxFreqMHz().toLongOrNull()
        if (isMin && curMax != null && mhz > curMax) setGpuClockMHz("max", mhz)   // naikkan max dulu
        if (!isMin && curMin != null && mhz < curMin) setGpuClockMHz("min", mhz)  // turunkan min dulu

        val candidates = if (isMin) {
            listOf(MIN_FREQ_GPU to mhz.toString(), MIN_GPUCLK to mhz.toString(), DEVFREQ_MIN_FREQ to hz.toString())
        } else {
            listOf(MAX_GPUCLK to hz.toString(), DEVFREQ_MAX_FREQ to hz.toString(), MAX_FREQ_GPU to mhz.toString())
        }
        for ((path, value) in candidates) {
            if (!checkExists(path)) continue
            // verify=false: cukup tahu write-nya diterima; kandidat berikutnya hanya dicoba bila
            // write ditolak (node read-only/gagal), bukan karena clamp ke OPP terdekat.
            if (!writeData(path, value, verify = false)) continue
            val now = readFreqGPU(path).toLongOrNull()
            return now == null || now == mhz
        }
        return false
    }

    /** Kompatibilitas API lama: filePath menentukan min/max. */
    fun writeFreqGPU(filePath: String, frequencyMHz: String): Boolean {
        val mhz = frequencyMHz.trim().toLongOrNull() ?: return false
        val target = if (filePath.contains("min", ignoreCase = true)) "min" else "max"
        return setGpuClockMHz(target, mhz)
    }

    fun readAvailableFreqGPU(): List<String> {
        val raw = readData(AVAILABLE_FREQ_GPU).ifBlank { readData(DEVFREQ_AVAILABLE_FREQ) }
        return raw.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { toMhz(it) }
            .distinct()
            .sortedBy { it.toLongOrNull() ?: 0L }
    }

    fun readParam(path: String): String = if (checkExists(path)) readData(path) else "N/A"

    // === BUS DCVS METHODS ===
    fun getBusComponents(): List<String> = listDirs(BUS_DCVS_DIR).sorted()

    fun getBusAvailableFreqs(busName: String): List<String> {
        val rootPath = "$BUS_DCVS_DIR/$busName/available_frequencies"
        val childPath = "$BUS_DCVS_DIR/$busName/0/available_frequencies"
        val content = if (checkExists(rootPath)) readData(rootPath) else if (checkExists(childPath)) readData(childPath) else ""
        return content.split(" ").filter { it.isNotBlank() }
    }
    
    fun getBusMinFreq(busName: String): String {
        val dirs = listDirs("$BUS_DCVS_DIR/$busName")
        val targetDir = dirs.find { checkExists("$BUS_DCVS_DIR/$busName/$it/min_freq") }
        return if (targetDir != null) readData("$BUS_DCVS_DIR/$busName/$targetDir/min_freq") else "N/A"
    }
    
    fun getBusMaxFreq(busName: String): String {
        val dirs = listDirs("$BUS_DCVS_DIR/$busName")
        val targetDir = dirs.find { checkExists("$BUS_DCVS_DIR/$busName/$it/max_freq") }
        return if (targetDir != null) readData("$BUS_DCVS_DIR/$busName/$targetDir/max_freq") else "N/A"
    }

    // === SMART SET BUS (BRUTE-FORCE BOUNDARY) ===
    fun setBusFreq(busName: String, target: String, freq: String) { 
        val targetFreq = freq.trim().toLongOrNull() ?: return
        val dirs = listDirs("$BUS_DCVS_DIR/$busName").ifEmpty { return }
        
        val availFreqs = getBusAvailableFreqs(busName).mapNotNull { it.toLongOrNull() }
        val absoluteMax = if (availFreqs.isNotEmpty()) availFreqs.maxOrNull().toString() else "9999999999"
        val absoluteMin = if (availFreqs.isNotEmpty()) availFreqs.minOrNull().toString() else "0"

        dirs.forEach { dirName ->
            val minPath = "$BUS_DCVS_DIR/$busName/$dirName/min_freq"
            val maxPath = "$BUS_DCVS_DIR/$busName/$dirName/max_freq"
            
            if (checkExists(minPath) && checkExists(maxPath)) {
                val currentMin = try { readData(minPath).trim().toLong() } catch(e:Exception) { 0L }
                val currentMax = try { readData(maxPath).trim().toLong() } catch(e:Exception) { Long.MAX_VALUE }

                if (target == "min") {
                    writeData(maxPath, absoluteMax, verify = false)
                    writeData(minPath, freq, verify = false)
                    val restoredMax = if (currentMax < targetFreq) freq else currentMax.toString()
                    writeData(maxPath, restoredMax, verify = false)
                } else { 
                    writeData(minPath, absoluteMin, verify = false)
                    writeData(maxPath, freq, verify = false)
                    val restoredMin = if (currentMin > targetFreq) freq else currentMin.toString()
                    writeData(minPath, restoredMin, verify = false)
                }
            }
        }
    }
}
