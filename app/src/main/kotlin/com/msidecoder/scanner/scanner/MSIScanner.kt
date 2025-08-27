package com.msidecoder.scanner.scanner

import android.content.Context
import android.util.Log
import com.msidecoder.scanner.opencv.OpenCVConverter
import com.msidecoder.scanner.opencv.OpenCVMSIDetector
import com.msidecoder.scanner.opencv.OpenCVMSIBinarizer
import com.msidecoder.scanner.opencv.VisualDebugger
import com.msidecoder.scanner.opencv.ROICandidate

/**
 * T-103: MSI barcode scanner with OpenCV ROI detection + binarization
 * 
 * Detects MSI barcodes using OpenCV gradient analysis, morphological operations,
 * and adaptive binarization with ASCII visualization.
 * Pipeline: NV21 → Mat → ROI Detection → Binarization → [Future: Decoding]
 */
class MSIScanner(context: Context? = null, enableDebugImages: Boolean = false) {
    
    companion object {
        private const val TAG = "MSIScanner"
        private const val MAX_PROCESSING_TIME_MS = 50L // T-102: Strict timeout for ROI detection
    }
    
    // Visual debugging (optional)
    private val visualDebugger = if (enableDebugImages && context != null) {
        VisualDebugger(context)
    } else null
    
    // OpenCV components for MSI detection
    private val openCVDetector = OpenCVMSIDetector(visualDebugger)
    private val openCVBinarizer = OpenCVMSIBinarizer(visualDebugger)
    
    init {
        val debugStatus = if (visualDebugger != null) "with visual debugging" else "standard mode"
        Log.d(TAG, "MSIScanner initialized with OpenCV ROI detector + binarizer ($debugStatus)")
    }
    
    /**
     * T-103: Scan ImageProxy frame for MSI barcodes using OpenCV ROI detection + binarization
     * 
     * @param imageProxy ImageProxy from CameraX ImageAnalysis
     * @param callback Result callback
     */
    fun scanFrame(
        imageProxy: androidx.camera.core.ImageProxy,
        callback: (ScanResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Step 1: Convert ImageProxy (YUV_420_888) directly to OpenCV grayscale Mat
            val grayMat = OpenCVConverter.imageProxyToGrayscaleMat(imageProxy)
            if (grayMat == null) {
                Log.e(TAG, "Failed to convert ImageProxy to Mat")
                callback(ScanResult.Error(Exception("ImageProxy conversion failed"), ScanSource.MSI))
                return
            }
            
            // Step 3: Detect ROI candidates using OpenCV (with debug images if enabled)
            val roiCandidates = openCVDetector.detectROICandidates(grayMat, imageProxy)
            
            // Step 4: Process ROI candidates
            val processingTime = System.currentTimeMillis() - startTime
            
            if (roiCandidates.isNotEmpty()) {
                val bestROI = roiCandidates.first() // Highest confidence
                Log.d(TAG, "MSI ROI detected in ${processingTime}ms: $bestROI")
                
                // T-103: Implement binarization of detected ROI
                val binaryProfile = openCVBinarizer.binarizeROI(grayMat, bestROI)
                
                if (binaryProfile != null) {
                    Log.d(TAG, "MSI binarization successful:")
                    Log.d(TAG, binaryProfile.toDebugString())
                    
                    // TODO T-104: Implement MSI pattern decoding
                    Log.d(TAG, "MSI binary pattern extracted but decoding not yet implemented (pending T-104)")
                    callback(ScanResult.NoResult)
                } else {
                    Log.v(TAG, "MSI binarization failed - ROI quality insufficient")
                    callback(ScanResult.NoResult)
                }
            } else {
                Log.v(TAG, "No MSI ROI detected in ${processingTime}ms")
                callback(ScanResult.NoResult)
            }
            
            // Cleanup OpenCV Mat
            grayMat.release()
            
            // Timeout protection
            if (processingTime > MAX_PROCESSING_TIME_MS) {
                Log.w(TAG, "MSI processing time exceeded limit: ${processingTime}ms > ${MAX_PROCESSING_TIME_MS}ms")
            }
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "MSI scan failed in ${processingTime}ms", exception)
            callback(ScanResult.Error(exception, ScanSource.MSI))
        }
    }
    
    /**
     * Legacy scan method for backward compatibility with ScannerArbitrator
     */
    fun scanFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        // This method is deprecated - direct ImageProxy method should be used instead
        Log.w(TAG, "Using legacy NV21 scan method - consider using ImageProxy method")
        
        try {
            val grayMat = OpenCVConverter.nv21ToGrayMat(nv21Data, width, height)
            if (grayMat == null) {
                callback(ScanResult.Error(Exception("NV21 conversion failed"), ScanSource.MSI))
                return
            }
            
            val roiCandidates = openCVDetector.detectROICandidates(grayMat)
            
            if (roiCandidates.isNotEmpty()) {
                val bestROI = roiCandidates.first()
                val binaryProfile = openCVBinarizer.binarizeROI(grayMat, bestROI)
                
                if (binaryProfile != null) {
                    Log.d(TAG, "MSI binarization successful (legacy method)")
                    callback(ScanResult.NoResult) // TODO: implement decoding
                } else {
                    callback(ScanResult.NoResult)
                }
            } else {
                callback(ScanResult.NoResult)
            }
            
            grayMat.release()
            
        } catch (exception: Exception) {
            Log.e(TAG, "Legacy MSI scan failed", exception)
            callback(ScanResult.Error(exception, ScanSource.MSI))
        }
    }
    
    /**
     * T-102: Check if frame potentially contains MSI barcode patterns
     * 
     * @return True if ROI candidates detected, false otherwise
     */
    fun hasROI(imageProxy: androidx.camera.core.ImageProxy): Boolean {
        return try {
            val grayMat = OpenCVConverter.imageProxyToGrayscaleMat(imageProxy)
            if (grayMat == null) {
                Log.w(TAG, "Failed to convert ImageProxy for ROI check")
                return false
            }
            
            val roiCandidates = openCVDetector.detectROICandidates(grayMat)
            val hasValidROI = roiCandidates.any { it.isValidBarcode() }
            
            grayMat.release()
            
            Log.v(TAG, "ROI check: ${roiCandidates.size} candidates, valid: $hasValidROI")
            hasValidROI
            
        } catch (exception: Exception) {
            Log.e(TAG, "ROI check failed: ${exception.message}")
            false
        }
    }
    
    /**
     * Clean up scanner resources
     */
    fun close() {
        openCVDetector.release()
        Log.d(TAG, "MSIScanner closed - OpenCV resources released")
    }
}