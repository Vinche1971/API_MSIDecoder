package com.msidecoder.scanner.opencv

import android.util.Log
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * T-101: OpenCV NV21→Mat conversion utilities for MSI detection pipeline
 * 
 * Optimized for Phase 0 architecture integration without disrupting MLKit performance
 */
object OpenCVConverter {
    
    private const val TAG = "OpenCVConverter"
    
    /**
     * Convert NV21 frame data to OpenCV Mat (grayscale)
     * 
     * @param nv21Data Raw NV21 bytes from CameraX ImageAnalysis
     * @param width Frame width in pixels
     * @param height Frame height in pixels
     * @return Grayscale Mat ready for OpenCV processing
     */
    fun nv21ToGrayMat(nv21Data: ByteArray, width: Int, height: Int): Mat? {
        return try {
            val startTime = System.currentTimeMillis()
            
            // Create Mat from NV21 data (YUV420sp format)
            // NV21 format: Y plane (width*height) + UV interleaved (width*height/2)
            val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21Data)
            
            // Convert YUV (NV21) to grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(yuvMat, grayMat, Imgproc.COLOR_YUV2GRAY_NV21)
            
            // Release intermediate Mat
            yuvMat.release()
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.v(TAG, "NV21→Gray conversion: ${width}x${height} in ${processingTime}ms")
            
            grayMat
            
        } catch (exception: Exception) {
            Log.e(TAG, "NV21→Mat conversion failed: ${exception.message}", exception)
            null
        }
    }
    
    /**
     * Convert NV21 frame data to OpenCV Mat (RGB)
     * 
     * @param nv21Data Raw NV21 bytes from CameraX ImageAnalysis  
     * @param width Frame width in pixels
     * @param height Frame height in pixels
     * @return RGB Mat for color processing (if needed in future)
     */
    fun nv21ToRgbMat(nv21Data: ByteArray, width: Int, height: Int): Mat? {
        return try {
            val startTime = System.currentTimeMillis()
            
            // Create Mat from NV21 data
            val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21Data)
            
            // Convert YUV (NV21) to RGB
            val rgbMat = Mat()
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
            
            // Release intermediate Mat
            yuvMat.release()
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.v(TAG, "NV21→RGB conversion: ${width}x${height} in ${processingTime}ms")
            
            rgbMat
            
        } catch (exception: Exception) {
            Log.e(TAG, "NV21→RGB conversion failed: ${exception.message}", exception)
            null
        }
    }
    
    /**
     * Validate Mat dimensions and properties
     * 
     * @param mat OpenCV Mat to validate
     * @param expectedWidth Expected width (optional)
     * @param expectedHeight Expected height (optional)
     * @return true if Mat is valid and dimensions match
     */
    fun validateMat(mat: Mat, expectedWidth: Int = -1, expectedHeight: Int = -1): Boolean {
        return try {
            if (mat.empty()) {
                Log.w(TAG, "Mat validation failed: Mat is empty")
                return false
            }
            
            if (expectedWidth > 0 && mat.cols() != expectedWidth) {
                Log.w(TAG, "Mat validation failed: Width mismatch (expected: $expectedWidth, actual: ${mat.cols()})")
                return false
            }
            
            if (expectedHeight > 0 && mat.rows() != expectedHeight) {
                Log.w(TAG, "Mat validation failed: Height mismatch (expected: $expectedHeight, actual: ${mat.rows()})")
                return false
            }
            
            Log.v(TAG, "Mat validation SUCCESS: ${mat.cols()}x${mat.rows()}, type: ${mat.type()}, channels: ${mat.channels()}")
            true
            
        } catch (exception: Exception) {
            Log.e(TAG, "Mat validation failed with exception: ${exception.message}", exception)
            false
        }
    }
}