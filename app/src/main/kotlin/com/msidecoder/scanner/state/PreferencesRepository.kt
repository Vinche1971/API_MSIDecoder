package com.msidecoder.scanner.state

import android.content.Context
import android.content.SharedPreferences

/**
 * Data class to track last scan result for anti-republication
 */
data class LastScanResult(
    val data: String,
    val timestamp: Long
) {
    /**
     * Check if this result is recent (within specified timeoutMs)
     */
    fun isRecent(timeoutMs: Long = 800L): Boolean {
        return System.currentTimeMillis() - timestamp < timeoutMs
    }
    
    /**
     * Check if this result matches the given data
     */
    fun matches(otherData: String): Boolean {
        return data == otherData
    }
}

class PreferencesRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getScannerState(): ScannerState {
        val stateName = prefs.getString(KEY_SCANNER_STATE, ScannerState.IDLE.name)
        return try {
            ScannerState.valueOf(stateName ?: ScannerState.IDLE.name)
        } catch (e: IllegalArgumentException) {
            ScannerState.IDLE
        }
    }
    
    fun setScannerState(state: ScannerState) {
        prefs.edit().putString(KEY_SCANNER_STATE, state.name).apply()
    }
    
    fun getCameraControlsState(): CameraControlsState {
        val torchEnabled = prefs.getBoolean(KEY_TORCH_ENABLED, false)
        val zoomLevelName = prefs.getString(KEY_ZOOM_LEVEL, ZoomLevel.ZOOM_1X.name)
        val zoomTypeName = prefs.getString(KEY_ZOOM_TYPE, ZoomType.DIGITAL.name)
        
        val zoomLevel = try {
            ZoomLevel.valueOf(zoomLevelName ?: ZoomLevel.ZOOM_1X.name)
        } catch (e: IllegalArgumentException) {
            ZoomLevel.ZOOM_1X
        }
        
        val zoomType = try {
            ZoomType.valueOf(zoomTypeName ?: ZoomType.DIGITAL.name)
        } catch (e: IllegalArgumentException) {
            ZoomType.DIGITAL
        }
        
        return CameraControlsState(
            torchEnabled = torchEnabled,
            zoomLevel = zoomLevel,
            zoomType = zoomType
        )
    }
    
    fun setCameraControlsState(state: CameraControlsState) {
        prefs.edit()
            .putBoolean(KEY_TORCH_ENABLED, state.torchEnabled)
            .putString(KEY_ZOOM_LEVEL, state.zoomLevel.name)
            .putString(KEY_ZOOM_TYPE, state.zoomType.name)
            .apply()
    }
    
    // T-006: Last scan result tracking (anti-republication)
    fun getLastScanResult(): LastScanResult? {
        val data = prefs.getString(KEY_LAST_RESULT_DATA, null)
        val timestamp = prefs.getLong(KEY_LAST_RESULT_TIMESTAMP, 0L)
        
        return if (data != null && timestamp > 0) {
            LastScanResult(data, timestamp)
        } else {
            null
        }
    }
    
    fun setLastScanResult(data: String, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_LAST_RESULT_DATA, data)
            .putLong(KEY_LAST_RESULT_TIMESTAMP, timestamp)
            .apply()
    }
    
    fun clearLastScanResult() {
        prefs.edit()
            .remove(KEY_LAST_RESULT_DATA)
            .remove(KEY_LAST_RESULT_TIMESTAMP)
            .apply()
    }
    
    // T-006: App preferences
    fun getAlwaysStartStopped(): Boolean {
        return prefs.getBoolean(KEY_ALWAYS_START_STOPPED, false)
    }
    
    fun setAlwaysStartStopped(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_START_STOPPED, enabled).apply()
    }
    
    // T-006: User intended torch state (separate from system state)
    fun getUserIntendedTorchState(): Boolean {
        return prefs.getBoolean(KEY_USER_INTENDED_TORCH, false)
    }
    
    fun setUserIntendedTorchState(intended: Boolean) {
        prefs.edit().putBoolean(KEY_USER_INTENDED_TORCH, intended).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "msi_scanner_prefs"
        private const val KEY_SCANNER_STATE = "scanner_state"
        private const val KEY_TORCH_ENABLED = "torch_enabled"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_ZOOM_TYPE = "zoom_type"
        // T-006 additions
        private const val KEY_LAST_RESULT_DATA = "last_result_data"
        private const val KEY_LAST_RESULT_TIMESTAMP = "last_result_timestamp"
        private const val KEY_ALWAYS_START_STOPPED = "always_start_stopped"
        private const val KEY_USER_INTENDED_TORCH = "user_intended_torch"
    }
}