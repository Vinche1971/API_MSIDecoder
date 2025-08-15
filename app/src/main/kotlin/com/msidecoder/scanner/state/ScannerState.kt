package com.msidecoder.scanner.state

enum class ScannerState {
    IDLE,    // Scanner stopped, no analysis running
    ACTIVE   // Scanner running, ImageAnalysis bound and processing
}

class ScannerStateManager {
    
    private var currentState: ScannerState = ScannerState.IDLE
    private val stateChangeListeners = mutableSetOf<(ScannerState) -> Unit>()
    
    fun getCurrentState(): ScannerState = currentState
    
    fun setState(newState: ScannerState) {
        if (currentState != newState) {
            currentState = newState
            notifyListeners()
        }
    }
    
    fun isActive(): Boolean = currentState == ScannerState.ACTIVE
    fun isIdle(): Boolean = currentState == ScannerState.IDLE
    
    fun toggle(): ScannerState {
        val newState = when (currentState) {
            ScannerState.IDLE -> ScannerState.ACTIVE
            ScannerState.ACTIVE -> ScannerState.IDLE
        }
        setState(newState)
        return newState
    }
    
    fun addStateChangeListener(listener: (ScannerState) -> Unit) {
        stateChangeListeners.add(listener)
    }
    
    fun removeStateChangeListener(listener: (ScannerState) -> Unit) {
        stateChangeListeners.remove(listener)
    }
    
    private fun notifyListeners() {
        stateChangeListeners.forEach { it(currentState) }
    }
    
    fun reset() {
        setState(ScannerState.IDLE)
    }
}