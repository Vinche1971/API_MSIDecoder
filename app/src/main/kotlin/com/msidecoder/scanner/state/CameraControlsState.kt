package com.msidecoder.scanner.state

data class CameraControlsState(
    val torchEnabled: Boolean = false,
    val zoomLevel: ZoomLevel = ZoomLevel.ZOOM_1X,
    val zoomType: ZoomType = ZoomType.DIGITAL
)

enum class ZoomLevel(val ratio: Float, val displayText: String) {
    ZOOM_1X(1.0f, "1×"),
    ZOOM_2X(2.0f, "2×"),
    ZOOM_3X(3.0f, "3×");
    
    fun next(): ZoomLevel = when(this) {
        ZOOM_1X -> ZOOM_2X
        ZOOM_2X -> ZOOM_3X
        ZOOM_3X -> ZOOM_1X
    }
}

enum class ZoomType {
    OPTICAL,    // Using telephoto lens
    DIGITAL     // Using digital zoom/crop
}

class CameraControlsManager {
    
    private var currentState = CameraControlsState()
    private val stateChangeListeners = mutableSetOf<(CameraControlsState) -> Unit>()
    
    fun getCurrentState(): CameraControlsState = currentState
    
    fun toggleTorch(): CameraControlsState {
        currentState = currentState.copy(torchEnabled = !currentState.torchEnabled)
        notifyListeners()
        return currentState
    }
    
    fun setTorch(enabled: Boolean): CameraControlsState {
        if (currentState.torchEnabled != enabled) {
            currentState = currentState.copy(torchEnabled = enabled)
            notifyListeners()
        }
        return currentState
    }
    
    fun cycleZoom(maxZoomRatio: Float): CameraControlsState {
        val nextZoom = currentState.zoomLevel.next()
        // Don't exceed max zoom ratio
        val effectiveZoom = if (nextZoom.ratio > maxZoomRatio) ZoomLevel.ZOOM_1X else nextZoom
        
        currentState = currentState.copy(zoomLevel = effectiveZoom)
        notifyListeners()
        return currentState
    }
    
    fun setZoomType(type: ZoomType): CameraControlsState {
        if (currentState.zoomType != type) {
            currentState = currentState.copy(zoomType = type)
            notifyListeners()
        }
        return currentState
    }
    
    fun reset(): CameraControlsState {
        currentState = CameraControlsState()
        notifyListeners()
        return currentState
    }
    
    fun addStateChangeListener(listener: (CameraControlsState) -> Unit) {
        stateChangeListeners.add(listener)
    }
    
    fun removeStateChangeListener(listener: (CameraControlsState) -> Unit) {
        stateChangeListeners.remove(listener)
    }
    
    private fun notifyListeners() {
        stateChangeListeners.forEach { it(currentState) }
    }
}