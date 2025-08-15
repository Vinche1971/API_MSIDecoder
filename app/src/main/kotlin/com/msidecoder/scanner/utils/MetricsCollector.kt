package com.msidecoder.scanner.utils

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class MetricsCollector {
    
    private val frameCount = AtomicInteger(0)
    private val totalProcessingTime = AtomicLong(0)
    private val queueSize = AtomicInteger(0)
    private var lastFpsCalculation = System.currentTimeMillis()
    private var lastFrameCount = 0
    
    // EMA smoothing for FPS (alpha = 0.1 for stability)
    private var smoothedFps = 0.0
    private val fpsAlpha = 0.1
    
    // Current frame info
    @Volatile var currentWidth = 0
        private set
    @Volatile var currentHeight = 0
        private set
    @Volatile var currentRotation = 0
        private set
    
    fun onFrameStart() {
        queueSize.incrementAndGet()
    }
    
    fun onFrameProcessed(processingTimeMs: Long, width: Int, height: Int, rotation: Int) {
        frameCount.incrementAndGet()
        totalProcessingTime.addAndGet(processingTimeMs)
        queueSize.decrementAndGet()
        
        currentWidth = width
        currentHeight = height
        currentRotation = rotation
        
        updateFps()
    }
    
    private fun updateFps() {
        val now = System.currentTimeMillis()
        val timeDelta = now - lastFpsCalculation
        
        if (timeDelta >= 1000) { // Update every second
            val currentFrames = frameCount.get()
            val framesDelta = currentFrames - lastFrameCount
            val instantFps = (framesDelta * 1000.0) / timeDelta
            
            if (smoothedFps == 0.0) {
                smoothedFps = instantFps
            } else {
                smoothedFps = fpsAlpha * instantFps + (1 - fpsAlpha) * smoothedFps
            }
            
            lastFpsCalculation = now
            lastFrameCount = currentFrames
        }
    }
    
    fun getFps(): Double = smoothedFps
    
    fun getAverageProcessingTime(): Double {
        val frames = frameCount.get()
        return if (frames > 0) {
            totalProcessingTime.get().toDouble() / frames
        } else 0.0
    }
    
    fun getLastProcessingTime(): Long = totalProcessingTime.get()
    
    fun getQueueSize(): Int = queueSize.get()
    
    fun getResolution(): String = "${currentWidth}x${currentHeight}"
    
    fun getFrameCount(): Int = frameCount.get()
    
    fun reset() {
        frameCount.set(0)
        totalProcessingTime.set(0)
        queueSize.set(0)
        smoothedFps = 0.0
        lastFpsCalculation = System.currentTimeMillis()
        lastFrameCount = 0
    }
    
    data class Snapshot(
        val fps: Double,
        val avgProcessingTimeMs: Double,
        val queueSize: Int,
        val resolution: String,
        val frameCount: Int,
        val rotation: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun getSnapshot(): Snapshot {
        return Snapshot(
            fps = getFps(),
            avgProcessingTimeMs = getAverageProcessingTime(),
            queueSize = getQueueSize(),
            resolution = getResolution(),
            frameCount = getFrameCount(),
            rotation = currentRotation
        )
    }
}