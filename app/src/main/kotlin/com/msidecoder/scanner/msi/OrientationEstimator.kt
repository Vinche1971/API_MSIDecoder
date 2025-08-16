package com.msidecoder.scanner.msi

import android.util.Log
import kotlin.math.*

/**
 * T-102: Orientation Estimation using Structure Tensor
 * 
 * Estimates the angle of barcode inclination in ROI regions using:
 * - Structure tensor computation (Gx², Gy², GxGy)
 * - Angle calculation: ½ atan2(2·GxGy, Gx²−Gy²)
 * - Robust averaging over ROI region
 */
class OrientationEstimator {
    
    companion object {
        private const val TAG = "OrientationEstimator"
        
        // Downsampling parameters for performance
        private const val DOWNSAMPLE_FACTOR = 2    // Reduce resolution by 2x for speed
        private const val MIN_DOWNSAMPLE_SIZE = 32  // Minimum size after downsampling
        
        // Structure tensor parameters
        private const val GRADIENT_EPSILON = 1e-6f // Avoid division by zero
        private const val MEDIAN_WINDOW_SIZE = 5    // Window for robust angle averaging
    }
    
    /**
     * Estimate orientation angle for a given ROI
     * 
     * @param nv21Data Original frame data
     * @param frameWidth Original frame width
     * @param frameHeight Original frame height
     * @param roiCandidate ROI region to analyze
     * @return Estimated angle in degrees [-90, +90] where 0° = horizontal
     */
    fun estimateOrientation(
        nv21Data: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        roiCandidate: RoiCandidate
    ): Float {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Estimating orientation for ROI: ${roiCandidate.x},${roiCandidate.y} ${roiCandidate.width}x${roiCandidate.height}")
            
            // Step 1: Extract ROI region from frame
            val roiIntensities = extractRoiRegion(nv21Data, frameWidth, frameHeight, roiCandidate)
            
            // Step 2: Downsample for performance if ROI is large enough
            val (downsampledIntensities, downsampledWidth, downsampledHeight) = 
                downsampleRoi(roiIntensities, roiCandidate.width, roiCandidate.height)
            
            // Step 3: Compute gradients (Sobel operators)
            val (gradX, gradY) = computeGradients(downsampledIntensities, downsampledWidth, downsampledHeight)
            
            // Step 4: Compute structure tensor components
            val (gxx, gyy, gxy) = computeStructureTensor(gradX, gradY, downsampledWidth, downsampledHeight)
            
            // Step 5: Calculate orientation angle
            val angleRadians = 0.5f * atan2(2 * gxy, gxx - gyy)
            val angleDegrees = Math.toDegrees(angleRadians.toDouble()).toFloat()
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Orientation estimated: ${String.format("%.1f", angleDegrees)}° in ${processingTime}ms")
            
            return angleDegrees
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "Orientation estimation failed in ${processingTime}ms", exception)
            return 0.0f // Default to horizontal
        }
    }
    
    /**
     * Extract ROI region from full frame
     */
    private fun extractRoiRegion(
        nv21Data: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        roi: RoiCandidate
    ): FloatArray {
        val roiIntensities = FloatArray(roi.width * roi.height)
        var roiIndex = 0
        
        for (y in roi.y until min(roi.y + roi.height, frameHeight)) {
            for (x in roi.x until min(roi.x + roi.width, frameWidth)) {
                val frameIndex = y * frameWidth + x
                if (frameIndex < nv21Data.size) {
                    // Convert NV21 luminance to normalized intensity [0..1]
                    roiIntensities[roiIndex] = (nv21Data[frameIndex].toInt() and 0xFF) / 255.0f
                }
                roiIndex++
            }
        }
        
        return roiIntensities
    }
    
    /**
     * Downsample ROI for performance optimization
     */
    private fun downsampleRoi(
        roiIntensities: FloatArray,
        originalWidth: Int,
        originalHeight: Int
    ): Triple<FloatArray, Int, Int> {
        
        // Check if downsampling is beneficial
        val newWidth = max(MIN_DOWNSAMPLE_SIZE, originalWidth / DOWNSAMPLE_FACTOR)
        val newHeight = max(MIN_DOWNSAMPLE_SIZE, originalHeight / DOWNSAMPLE_FACTOR)
        
        if (newWidth >= originalWidth || newHeight >= originalHeight) {
            // No downsampling needed
            return Triple(roiIntensities, originalWidth, originalHeight)
        }
        
        val downsampledIntensities = FloatArray(newWidth * newHeight)
        val scaleX = originalWidth.toFloat() / newWidth
        val scaleY = originalHeight.toFloat() / newHeight
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                // Bilinear interpolation for smooth downsampling
                val srcX = x * scaleX
                val srcY = y * scaleY
                
                val x0 = srcX.toInt().coerceIn(0, originalWidth - 1)
                val y0 = srcY.toInt().coerceIn(0, originalHeight - 1)
                val x1 = min(x0 + 1, originalWidth - 1)
                val y1 = min(y0 + 1, originalHeight - 1)
                
                val fx = srcX - x0
                val fy = srcY - y0
                
                val p00 = roiIntensities[y0 * originalWidth + x0]
                val p10 = roiIntensities[y0 * originalWidth + x1]
                val p01 = roiIntensities[y1 * originalWidth + x0]
                val p11 = roiIntensities[y1 * originalWidth + x1]
                
                val interpolated = p00 * (1 - fx) * (1 - fy) +
                                 p10 * fx * (1 - fy) +
                                 p01 * (1 - fx) * fy +
                                 p11 * fx * fy
                
                downsampledIntensities[y * newWidth + x] = interpolated
            }
        }
        
        Log.d(TAG, "Downsampled ROI from ${originalWidth}x${originalHeight} to ${newWidth}x${newHeight}")
        return Triple(downsampledIntensities, newWidth, newHeight)
    }
    
    /**
     * Compute image gradients using Sobel operators
     */
    private fun computeGradients(
        intensities: FloatArray,
        width: Int,
        height: Int
    ): Pair<FloatArray, FloatArray> {
        
        val gradX = FloatArray(width * height)
        val gradY = FloatArray(width * height)
        
        // Sobel X kernel: [[-1, 0, 1], [-2, 0, 2], [-1, 0, 1]]
        // Sobel Y kernel: [[-1, -2, -1], [0, 0, 0], [1, 2, 1]]
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // Sobel X (horizontal gradient)
                val gx = -intensities[(y-1) * width + (x-1)] +  intensities[(y-1) * width + (x+1)] +
                        -2 * intensities[y * width + (x-1)] + 2 * intensities[y * width + (x+1)] +
                        -intensities[(y+1) * width + (x-1)] +  intensities[(y+1) * width + (x+1)]
                
                // Sobel Y (vertical gradient)
                val gy = -intensities[(y-1) * width + (x-1)] - 2 * intensities[(y-1) * width + x] - intensities[(y-1) * width + (x+1)] +
                         intensities[(y+1) * width + (x-1)] + 2 * intensities[(y+1) * width + x] + intensities[(y+1) * width + (x+1)]
                
                gradX[y * width + x] = gx
                gradY[y * width + x] = gy
            }
        }
        
        return Pair(gradX, gradY)
    }
    
    /**
     * Compute structure tensor components: Gxx, Gyy, Gxy
     * These represent the local structure of the image
     */
    private fun computeStructureTensor(
        gradX: FloatArray,
        gradY: FloatArray,
        width: Int,
        height: Int
    ): Triple<Float, Float, Float> {
        
        var sumGxx = 0.0f
        var sumGyy = 0.0f
        var sumGxy = 0.0f
        var count = 0
        
        // Accumulate structure tensor components over the ROI
        for (i in gradX.indices) {
            val gx = gradX[i]
            val gy = gradY[i]
            
            sumGxx += gx * gx
            sumGyy += gy * gy
            sumGxy += gx * gy
            count++
        }
        
        if (count == 0) {
            return Triple(0.0f, 0.0f, 0.0f)
        }
        
        // Average structure tensor components
        val avgGxx = sumGxx / count
        val avgGyy = sumGyy / count
        val avgGxy = sumGxy / count
        
        Log.d(TAG, "Structure tensor: Gxx=${String.format("%.4f", avgGxx)}, Gyy=${String.format("%.4f", avgGyy)}, Gxy=${String.format("%.4f", avgGxy)}")
        
        return Triple(avgGxx, avgGyy, avgGxy)
    }
    
    /**
     * Estimate orientation using Structure Tensor for multiple ROI candidates
     * Returns median angle for robustness
     */
    fun estimateRobustOrientation(
        nv21Data: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        roiCandidates: List<RoiCandidate>
    ): Float {
        
        if (roiCandidates.isEmpty()) {
            return 0.0f
        }
        
        val angles = roiCandidates.map { roi ->
            estimateOrientation(nv21Data, frameWidth, frameHeight, roi)
        }
        
        // Return median angle for robustness
        val sortedAngles = angles.sorted()
        val median = if (sortedAngles.size % 2 == 0) {
            (sortedAngles[sortedAngles.size / 2 - 1] + sortedAngles[sortedAngles.size / 2]) / 2.0f
        } else {
            sortedAngles[sortedAngles.size / 2]
        }
        
        Log.d(TAG, "Robust orientation from ${roiCandidates.size} ROIs: ${String.format("%.1f", median)}° (angles: ${angles.map { String.format("%.1f", it) }})")
        
        return median
    }
}