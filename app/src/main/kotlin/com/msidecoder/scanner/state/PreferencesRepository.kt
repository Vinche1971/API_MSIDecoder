package com.msidecoder.scanner.state

import android.content.Context
import android.content.SharedPreferences

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
    
    companion object {
        private const val PREFS_NAME = "msi_scanner_prefs"
        private const val KEY_SCANNER_STATE = "scanner_state"
        private const val KEY_TORCH_ENABLED = "torch_enabled"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_ZOOM_TYPE = "zoom_type"
    }
}