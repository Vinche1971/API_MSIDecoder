package com.msidecoder.scanner.opencv

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageProxy
import com.msidecoder.scanner.camera.YuvToNv21Converter
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * T-101: OpenCV NV21→Mat conversion utilities for MSI detection pipeline
 * 
 * Optimized for Phase 0 architecture integration without disrupting MLKit performance
 */
object OpenCVConverter {
    
    private const val TAG = "OpenCVConverter"
    
    /**
     * Convert ImageProxy (YUV_420_888) to OpenCV grayscale Mat directly
     * This is the correct method for CameraX ImageAnalysis
     * 
     * @param imageProxy ImageProxy from CameraX ImageAnalysis
     * @param applyRotation Apply rotation correction for portrait app
     * @return Grayscale Mat ready for OpenCV processing
     */
    fun imageProxyToGrayscaleMat(imageProxy: ImageProxy, applyRotation: Boolean = true): Mat? {
        return try {
            val startTime = System.currentTimeMillis()
            
            val image = imageProxy.image ?: return null
            
            // Validate format (CameraX uses YUV_420_888)
            if (image.format != ImageFormat.YUV_420_888) {
                Log.e(TAG, "Unsupported image format: ${image.format}, expected: ${ImageFormat.YUV_420_888}")
                return null
            }
            
            // Method 1: Direct Y-plane extraction (most efficient for grayscale)
            val planes = image.planes
            if (planes.size < 3) {
                Log.e(TAG, "Invalid plane count: ${planes.size}, expected: 3")
                return null
            }
            
            val yPlane = planes[0]
            val yBuffer = yPlane.buffer
            val ySize = yBuffer.remaining()
            
            // Handle row stride (padding) correctly
            val pixelStride = yPlane.pixelStride
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height
            
            val grayMat = if (pixelStride == 1 && rowStride == width) {
                // Simple case: no padding, direct copy
                val yData = ByteArray(ySize)
                yBuffer.get(yData)
                Mat(height, width, CvType.CV_8UC1).apply {
                    put(0, 0, yData)
                }
            } else {
                // Complex case: handle stride and padding
                Mat(height, width, CvType.CV_8UC1).apply {
                    val rowData = ByteArray(width)
                    for (row in 0 until height) {
                        yBuffer.position(row * rowStride)
                        yBuffer.get(rowData, 0, width)
                        put(row, 0, rowData)
                    }
                }
            }
            
            // Apply rotation for portrait app if needed
            val finalMat = if (applyRotation) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                Log.d(TAG, "ImageProxy rotation metadata: ${rotationDegrees}°")
                Log.d(TAG, "Original image: ${grayMat.cols()}x${grayMat.rows()}")
                
                // TEMPORARY: Force 90° rotation for testing
                Log.w(TAG, "FORCING 90° rotation for testing")
                val forcedRotation = 90
                
                when (forcedRotation) {
                    90 -> {
                        val rotatedMat = Mat()
                        Core.rotate(grayMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
                        Log.d(TAG, "Applied 90° clockwise: ${grayMat.cols()}x${grayMat.rows()} → ${rotatedMat.cols()}x${rotatedMat.rows()}")
                        grayMat.release()
                        rotatedMat
                    }
                    180 -> {
                        val rotatedMat = Mat()
                        Core.rotate(grayMat, rotatedMat, Core.ROTATE_180)
                        Log.d(TAG, "Applied 180°: ${grayMat.cols()}x${grayMat.rows()} → ${rotatedMat.cols()}x${rotatedMat.rows()}")
                        grayMat.release() 
                        rotatedMat
                    }
                    270 -> {
                        val rotatedMat = Mat()
                        Core.rotate(grayMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
                        Log.d(TAG, "Applied 270° counter-clockwise: ${grayMat.cols()}x${grayMat.rows()} → ${rotatedMat.cols()}x${rotatedMat.rows()}")
                        grayMat.release()
                        rotatedMat
                    }
                    else -> {
                        Log.d(TAG, "No rotation applied (${rotationDegrees}°): ${grayMat.cols()}x${grayMat.rows()}")
                        grayMat // 0° or unknown, keep as-is
                    }
                }
            } else {
                grayMat
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.v(TAG, "ImageProxy→GrayMat: ${finalMat.cols()}x${finalMat.rows()} in ${processingTime}ms (rotation: ${imageProxy.imageInfo.rotationDegrees}°)")
            
            finalMat
            
        } catch (exception: Exception) {
            Log.e(TAG, "ImageProxy→GrayMat conversion failed: ${exception.message}", exception)
            null
        }
    }
    
    /**
     * Convert ImageProxy to NV21 byte array (legacy method for compatibility)
     * 
     * @param imageProxy ImageProxy from CameraX ImageAnalysis
     * @return NV21 byte array or null if conversion fails
     */
    fun imageProxyToNv21(imageProxy: ImageProxy): ByteArray? {
        return try {
            YuvToNv21Converter.convert(imageProxy)
        } catch (exception: Exception) {
            Log.e(TAG, "ImageProxy→NV21 conversion failed: ${exception.message}", exception)
            null
        }
    }
    
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
            
            // Validate input data size (flexible - allow extra data from stride/padding)
            val yPlaneSize = width * height
            val minExpectedSize = yPlaneSize // At least Y plane
            val maxExpectedSize = yPlaneSize * 3 // Generous upper bound for stride/padding
            
            if (nv21Data.size < minExpectedSize) {
                Log.e(TAG, "NV21 data too small: ${nv21Data.size}, minimum: $minExpectedSize")
                return null
            }
            
            if (nv21Data.size > maxExpectedSize) {
                Log.w(TAG, "NV21 data larger than expected: ${nv21Data.size}, max expected: $maxExpectedSize (stride/padding)")
            }
            
            // Extract Y plane (grayscale) - first width*height bytes
            // NV21 format: Y plane is luminance data = grayscale
            val grayData = nv21Data.sliceArray(0 until yPlaneSize)
            
            // Create grayscale Mat directly from Y plane
            val grayMat = Mat(height, width, CvType.CV_8UC1)
            grayMat.put(0, 0, grayData)
            
            // TEMPORARY: Apply 90° rotation for portrait app
            val rotatedMat = Mat()
            Core.rotate(grayMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            grayMat.release()
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.v(TAG, "NV21→Gray conversion: ${width}x${height} → ${rotatedMat.cols()}x${rotatedMat.rows()} in ${processingTime}ms (ROTATED 90°)")
            
            rotatedMat
            
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