package com.msidecoder.scanner.scanner

import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Arbitrator that coordinates ML Kit and MSI scanners with priority logic
 * 
 * Priority: ML Kit first (whitelist formats) → MSI fallback (if no ML Kit result)
 */
class ScannerArbitrator(
    private val mlkitScanner: MLKitScanner,
    private val msiScanner: MSIScanner,
    private val executor: Executor
) {
    
    companion object {
        private const val TAG = "ScannerArbitrator"
        private const val MSI_TIMEOUT_MS = 50L // Max time for MSI scan
    }
    
    // Metrics tracking
    private var lastMLKitTimeMs = 0L
    private var lastMSITimeMs = 0L
    private var mlkitHits = 0
    private var msiHits = 0
    
    /**
     * Scan frame with both scanners using priority logic
     * 
     * @param nv21Data Frame data in NV21 format
     * @param width Frame width
     * @param height Frame height  
     * @param rotationDegrees Frame rotation
     * @param callback Final result callback
     */
    fun scanFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        val arbitrationStartTime = System.currentTimeMillis()
        val resultDelivered = AtomicBoolean(false)
        
        // Priority 1: Try ML Kit first (blocking)
        mlkitScanner.scanFrame(nv21Data, width, height, rotationDegrees) { mlkitResult ->
            lastMLKitTimeMs = when (mlkitResult) {
                is ScanResult.Success -> mlkitResult.processingTimeMs
                is ScanResult.Error -> System.currentTimeMillis() - arbitrationStartTime
                else -> System.currentTimeMillis() - arbitrationStartTime
            }
            
            when (mlkitResult) {
                is ScanResult.Success -> {
                    // ML Kit found something in whitelist → immediate result
                    mlkitHits++
                    Log.d(TAG, "ML Kit SUCCESS: ${mlkitResult.format} → immediate delivery")
                    
                    if (resultDelivered.compareAndSet(false, true)) {
                        callback(mlkitResult)
                    }
                }
                
                is ScanResult.Error -> {
                    Log.w(TAG, "ML Kit ERROR → trying MSI fallback")
                    tryMSIFallback(nv21Data, width, height, rotationDegrees, callback, resultDelivered)
                }
                
                is ScanResult.NoResult -> {
                    Log.d(TAG, "ML Kit no result → trying MSI fallback")
                    tryMSIFallback(nv21Data, width, height, rotationDegrees, callback, resultDelivered)
                }
            }
        }
    }
    
    /**
     * Try MSI scanner as fallback when ML Kit fails/finds nothing
     */
    private fun tryMSIFallback(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit,
        resultDelivered: AtomicBoolean
    ) {
        // Run MSI scanner on executor with timeout
        executor.execute {
            val msiStartTime = System.currentTimeMillis()
            
            try {
                msiScanner.scanFrame(nv21Data, width, height, rotationDegrees) { msiResult ->
                    lastMSITimeMs = when (msiResult) {
                        is ScanResult.Success -> msiResult.processingTimeMs
                        is ScanResult.Error -> System.currentTimeMillis() - msiStartTime
                        else -> System.currentTimeMillis() - msiStartTime
                    }
                    
                    when (msiResult) {
                        is ScanResult.Success -> {
                            msiHits++
                            Log.d(TAG, "MSI SUCCESS: ${msiResult.format}")
                            
                            if (resultDelivered.compareAndSet(false, true)) {
                                callback(msiResult)
                            }
                        }
                        
                        else -> {
                            Log.d(TAG, "MSI no result → overall no detection")
                            
                            if (resultDelivered.compareAndSet(false, true)) {
                                callback(ScanResult.NoResult)
                            }
                        }
                    }
                }
                
            } catch (exception: Exception) {
                Log.e(TAG, "MSI fallback failed", exception)
                lastMSITimeMs = System.currentTimeMillis() - msiStartTime
                
                if (resultDelivered.compareAndSet(false, true)) {
                    callback(ScanResult.NoResult)
                }
            }
        }
    }
    
    /**
     * Get current scan metrics for overlay display
     */
    data class ScanMetrics(
        val mlkitTimeMs: Long,
        val msiTimeMs: Long,
        val mlkitHits: Int,
        val msiHits: Int
    )
    
    fun getMetrics(): ScanMetrics {
        return ScanMetrics(
            mlkitTimeMs = lastMLKitTimeMs,
            msiTimeMs = lastMSITimeMs,
            mlkitHits = mlkitHits,
            msiHits = msiHits
        )
    }
    
    /**
     * Reset hit counters
     */
    fun resetHitCounters() {
        mlkitHits = 0
        msiHits = 0
        Log.d(TAG, "Hit counters reset")
    }
    
    /**
     * Clean up scanner resources
     */
    fun close() {
        mlkitScanner.close()
        msiScanner.close()
        Log.d(TAG, "ScannerArbitrator closed")
    }
}