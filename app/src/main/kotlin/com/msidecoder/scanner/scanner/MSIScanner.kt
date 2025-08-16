package com.msidecoder.scanner.scanner

import android.util.Log
import com.msidecoder.scanner.msi.MsiRoiDetector
import com.msidecoder.scanner.msi.RoiStats

/**
 * MSI barcode scanner - T-101 ROI Detection implementation
 * 
 * Phase 1: Implements ROI detection using anisotropic gradient energy
 * Future phases will add pattern analysis and checksum validation.
 */
class MSIScanner {
    
    companion object {
        private const val TAG = "MSIScanner"
    }
    
    private val roiDetector = MsiRoiDetector()
    private var debugManager: com.msidecoder.scanner.msi.MsiDebugManager? = null
    
    init {
        Log.d(TAG, "MSIScanner initialized (T-101 ROI Detection)")
    }
    
    /**
     * Set debug manager for monitoring pipeline
     */
    fun setDebugManager(manager: com.msidecoder.scanner.msi.MsiDebugManager?) {
        debugManager = manager
        Log.d(TAG, "T-101: DEBUG - Debug manager set: $manager")
    }
    
    /**
     * Scan NV21 frame data for MSI barcodes - T-101 ROI Detection
     * 
     * @param nv21Data Frame data in NV21 format
     * @param width Frame width
     * @param height Frame height  
     * @param rotationDegrees Frame rotation (0, 90, 180, 270)
     * @param callback Result callback
     */
    fun scanFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "T-101: Starting ROI detection ${width}x${height}, rotation=$rotationDegrees")
            
            // T-101: Start frame monitoring
            debugManager?.startFrame()
            debugManager?.updateStage(com.msidecoder.scanner.msi.MsiStage.ROI_EXTRACT)
            
            // T-101: Detect ROI candidates using anisotropic gradient energy
            val roiCandidates = roiDetector.detectROI(nv21Data, width, height, rotationDegrees)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            if (roiCandidates.isNotEmpty()) {
                val bestCandidate = roiCandidates.first()
                Log.d(TAG, "T-101: Found ${roiCandidates.size} ROI candidates, best: ${bestCandidate.x},${bestCandidate.y} ${bestCandidate.width}x${bestCandidate.height} score=${bestCandidate.score}")
                
                // Create ROI stats for debug monitoring
                val roiStats = RoiStats(
                    candidatesFound = roiCandidates.size,
                    bestScore = bestCandidate.score,
                    bestCandidate = bestCandidate,
                    processingTimeMs = processingTime,
                    gradientThreshold = 0.3f, // From MsiRoiDetector
                    morphoKernelSize = 15,     // From MsiRoiDetector
                    estimatedAngle = bestCandidate.orientationDegrees,  // T-102: Include orientation
                    rectificationTimeMs = bestCandidate.rectifiedRoi?.processingTimeMs ?: 0L, // T-103: Rectification time
                    rectificationSuccess = bestCandidate.rectifiedRoi != null // T-103: Rectification success
                )
                
                // T-101: Send ROI stats to debug manager
                Log.d(TAG, "T-101: DEBUG - Adding ROI stats to manager: $debugManager")
                debugManager?.addRoiStats(roiStats)
                Log.d(TAG, "T-101: DEBUG - Updating stage to COMPLETE")
                debugManager?.updateStage(com.msidecoder.scanner.msi.MsiStage.COMPLETE, success = true)
                
                // TODO T-102+: Implement pattern analysis on ROI candidates
                // For now, just log the detection without decoding
                Log.d(TAG, "T-101: ROI detection completed in ${processingTime}ms - no decoding yet (Phase 1)")
                callback(ScanResult.NoResult)
                
            } else {
                Log.d(TAG, "T-101: No ROI candidates found in ${processingTime}ms")
                
                // T-101: Send empty ROI stats to debug manager
                val roiStats = RoiStats(
                    candidatesFound = 0,
                    bestScore = 0.0f,
                    bestCandidate = null,
                    processingTimeMs = processingTime,
                    gradientThreshold = 0.3f,
                    morphoKernelSize = 15
                )
                Log.d(TAG, "T-101: DEBUG - Adding empty ROI stats to manager: $debugManager")
                debugManager?.addRoiStats(roiStats)
                Log.d(TAG, "T-101: DEBUG - Updating stage to COMPLETE (no candidates)")
                debugManager?.updateStage(com.msidecoder.scanner.msi.MsiStage.COMPLETE, success = true)
                
                callback(ScanResult.NoResult)
            }
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "T-101: ROI detection failed in ${processingTime}ms", exception)
            
            // T-101: Report error to debug manager
            debugManager?.updateStage(com.msidecoder.scanner.msi.MsiStage.ROI_EXTRACT, success = false, error = exception.message)
            
            callback(ScanResult.Error(exception, ScanSource.MSI))
        }
    }
    
    /**
     * Check if frame potentially contains MSI barcode patterns - STUB
     * 
     * @return Always false in Phase 0 stub
     */
    fun hasROI(@Suppress("UNUSED_PARAMETER") nv21Data: ByteArray, @Suppress("UNUSED_PARAMETER") width: Int, @Suppress("UNUSED_PARAMETER") height: Int): Boolean {
        // TODO Phase 1+: Implement ROI detection
        // - Edge detection
        // - Pattern recognition
        // - Orientation analysis
        return false
    }
    
    /**
     * Clean up scanner resources
     */
    fun close() {
        Log.d(TAG, "MSIScanner closed (STUB)")
        // TODO Phase 1+: Cleanup native resources if needed
    }
}