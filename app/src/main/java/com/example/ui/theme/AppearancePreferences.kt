package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val wireValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

@Immutable
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val simpleLook: Boolean = false,
    val isDefaultSimpleLook: Boolean = false,
    val isBlurSupportedByDevice: Boolean = true,
    val isLowRamDevice: Boolean = false
)

object DeviceCapabilityDetector {
    private const val LOW_RAM_TOTAL_BYTES = 2_000_000_000L // ~2 GB threshold

    fun isBlurApiSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.S

    fun shouldDefaultToSimpleLook(
        sdkInt: Int,
        isLowRamDevice: Boolean,
        totalMemoryBytes: Long = 0L
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.S) return true
        if (isLowRamDevice) return true
        if (totalMemoryBytes in 1L until LOW_RAM_TOTAL_BYTES) return true
        return false
    }

    fun inspectDevice(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): DeviceCapabilitySnapshot {
        val am = runCatching {
            context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }.getOrNull()
        val isLowRam = runCatching { am?.isLowRamDevice == true }.getOrDefault(false)
        val totalMem = runCatching {
            val info = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(0L)
        val blurSupported = isBlurApiSupported(sdkInt)
        val defaultSimple = shouldDefaultToSimpleLook(
            sdkInt = sdkInt,
            isLowRamDevice = isLowRam,
            totalMemoryBytes = totalMem
        )
        return DeviceCapabilitySnapshot(
            blurSupportedByDevice = blurSupported,
            isLowRamDevice = isLowRam || (totalMem in 1L until LOW_RAM_TOTAL_BYTES),
            defaultSimpleLook = defaultSimple
        )
    }
}

@Immutable
data class DeviceCapabilitySnapshot(
    val blurSupportedByDevice: Boolean,
    val isLowRamDevice: Boolean,
    val defaultSimpleLook: Boolean
)

interface AppearanceController {
    val settings: StateFlow<AppearanceSettings>
    fun setThemeMode(mode: ThemeMode)
    fun setSimpleLook(enabled: Boolean)
    fun resetToDeviceDefault()
}

/**
 * Local SharedPreferences-backed store for Appearance settings (Theme Mode & Simple Look).
 */
class AppearancePrefsStore(context: Context) : AppearanceController {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val capability = DeviceCapabilityDetector.inspectDevice(appContext)

    private val _settings = MutableStateFlow(readCurrent())
    override val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    private fun readCurrent(): AppearanceSettings {
        val themeMode = ThemeMode.fromWire(prefs.getString(KEY_THEME_MODE, null))
        val simpleLook = if (prefs.contains(KEY_SIMPLE_LOOK)) {
            prefs.getBoolean(KEY_SIMPLE_LOOK, capability.defaultSimpleLook)
        } else {
            capability.defaultSimpleLook
        }
        return AppearanceSettings(
            themeMode = themeMode,
            simpleLook = simpleLook,
            isDefaultSimpleLook = capability.defaultSimpleLook,
            isBlurSupportedByDevice = capability.blurSupportedByDevice,
            isLowRamDevice = capability.isLowRamDevice
        )
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.wireValue).apply()
        _settings.value = readCurrent()
    }

    override fun setSimpleLook(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMPLE_LOOK, enabled).apply()
        _settings.value = readCurrent()
    }

    override fun resetToDeviceDefault() {
        prefs.edit().remove(KEY_SIMPLE_LOOK).remove(KEY_THEME_MODE).apply()
        _settings.value = readCurrent()
    }

    companion object {
        private const val PREFS_NAME = "school_report_appearance_prefs"
        private const val KEY_THEME_MODE = "d1_theme_mode"
        private const val KEY_SIMPLE_LOOK = "d1_simple_look"
    }
}

val LocalAppearanceController = staticCompositionLocalOf<AppearanceController?> { null }
