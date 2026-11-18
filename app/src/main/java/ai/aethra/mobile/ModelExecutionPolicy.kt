package ai.aethra.mobile

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

data class DeviceReadiness(
    val totalRamGb: Double,
    val availableRamGb: Double,
    val freeStorageGb: Double,
    val thermalStatus: Int?,
    val lowMemory: Boolean,
    val safeConcurrentLimit: Int,
    val warnings: List<String>,
)

object ModelExecutionPolicy {
    private const val PREFS = "aethra_runtime_settings"
    private const val KEY_MAX_MODELS = "max_concurrent_models"
    private const val RESERVED_RAM_GB = 2.0

    fun requestedMaxModels(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_MODELS, 1).coerceIn(1, 4)

    fun saveRequestedMaxModels(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MAX_MODELS, value.coerceIn(1, 4)).apply()
    }

    fun inspect(context: Context): DeviceReadiness {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val total = memory.totalMem / 1_073_741_824.0
        val available = memory.availMem / 1_073_741_824.0
        val stats = StatFs(context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath)
        val storage = stats.availableBytes / 1_073_741_824.0
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else null

        // Hardware estimate only. The current app has one validated LiteRT-LM runtime.
        val hardwareCap = when {
            total < 6.0 -> 1
            total < 10.0 -> 1
            total < 16.0 -> 2
            else -> 3
        }
        val warnings = buildList {
            if (memory.lowMemory) add("Android reports low memory.")
            if (available < RESERVED_RAM_GB) add("Less than 2 GB RAM is currently available.")
            if (storage < 3.0) add("Less than 3 GB free storage is available.")
            if (thermal != null && thermal >= PowerManager.THERMAL_STATUS_SEVERE) add("Device temperature is high; model loading is paused.")
        }
        return DeviceReadiness(total, available, storage, thermal, memory.lowMemory, hardwareCap, warnings)
    }

    fun effectiveMaxModels(context: Context): Int =
        minOf(requestedMaxModels(context), inspect(context).safeConcurrentLimit, 1)

    fun assertCanLoad(context: Context, requiredRamGb: Int, requiredStorageGb: Double = 1.0) {
        val device = inspect(context)
        check(!device.lowMemory) { "Android is in low-memory mode. Close other apps and try again." }
        check(device.thermalStatus == null || device.thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
            "Phone temperature is too high. Let it cool before loading an AI model."
        }
        check(device.totalRamGb >= requiredRamGb) {
            "This model needs a device with at least $requiredRamGb GB RAM. Detected ${"%.1f".format(device.totalRamGb)} GB."
        }
        check(device.availableRamGb >= RESERVED_RAM_GB) {
            "Not enough free RAM. At least 2 GB must remain available for Android and AETHRA."
        }
        check(device.freeStorageGb >= requiredStorageGb) {
            "Not enough free storage. Free at least $requiredStorageGb GB before downloading/loading."
        }
    }
}
