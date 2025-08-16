package com.msidecoder.scanner.msi

import android.util.Log
import kotlin.math.*

/**
 * T-103: Perspective Rectifier for MSI ROI normalization
 * 
 * Transforms detected ROI candidates into normalized, perspective-corrected images
 * with vertical bars aligned to ±2° tolerance.
 */
class PerspectiveRectifier {
    
    companion object {
        private const val TAG = "PerspectiveRectifier"
        
        // T-103: Target rectified dimensions
        private const val RECTIFIED_WIDTH = 1024
        private const val RECTIFIED_HEIGHT = 256
        
        // T-103: Corner detection parameters
        private const val CORNER_SEARCH_RADIUS = 10
        private const val MIN_CORNER_QUALITY = 0.1f
    }
    
    /**
     * Rectify ROI candidate using perspective transformation
     * 
     * @param nv21Data Source frame data
     * @param frameWidth Frame width
     * @param frameHeight Frame height  
     * @param roiCandidate ROI to rectify with orientation angle
     * @return Rectified ROI data or null if rectification failed
     */
    fun rectifyRoi(
        nv21Data: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        roiCandidate: RoiCandidate
    ): RectifiedRoi? {
        
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "T-103: Starting rectification for ROI ${roiCandidate.x},${roiCandidate.y} ${roiCandidate.width}x${roiCandidate.height}, angle=${roiCandidate.orientationDegrees}°")
            
            // Step 1: Extract ROI region from NV21
            val roiData = extractRoiRegion(nv21Data, frameWidth, frameHeight, roiCandidate)
            if (roiData == null) {
                Log.w(TAG, "T-103: Failed to extract ROI region")
                return null
            }
            
            // Step 2: Detect corner points for perspective transformation
            val corners = detectCorners(roiData, roiCandidate.width, roiCandidate.height)
            if (corners == null) {
                Log.w(TAG, "T-103: Failed to detect corner points")
                return null
            }
            
            // Step 3: Apply perspective transformation
            val transformedData = applyPerspectiveTransform(roiData, roiCandidate.width, roiCandidate.height, corners)
            if (transformedData == null) {
                Log.w(TAG, "T-103: Failed to apply perspective transformation")
                return null
            }
            
            // Step 4: Apply rotation inverse to correct orientation
            val rectifiedData = applyRotationCorrection(transformedData, roiCandidate.orientationDegrees)
            
            // Step 5: Clamp and normalize intensities
            val normalizedData = clampAndNormalize(rectifiedData)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "T-103: Rectification completed in ${processingTime}ms → ${RECTIFIED_WIDTH}x${RECTIFIED_HEIGHT}")
            
            return RectifiedRoi(
                data = normalizedData,
                width = RECTIFIED_WIDTH,
                height = RECTIFIED_HEIGHT,
                originalAngle = roiCandidate.orientationDegrees,
                corners = corners,
                processingTimeMs = processingTime
            )
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "T-103: Rectification failed in ${processingTime}ms", exception)
            return null
        }
    }
    
    /**
     * Extract ROI region from NV21 frame data
     */
    private fun extractRoiRegion(
        nv21Data: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        roiCandidate: RoiCandidate
    ): ByteArray? {
        
        try {
            val roiWidth = roiCandidate.width
            val roiHeight = roiCandidate.height
            val startX = roiCandidate.x
            val startY = roiCandidate.y
            
            // Bounds checking
            if (startX < 0 || startY < 0 || 
                startX + roiWidth > frameWidth || 
                startY + roiHeight > frameHeight) {
                Log.w(TAG, "T-103: ROI bounds exceed frame dimensions")
                return null
            }
            
            val roiData = ByteArray(roiWidth * roiHeight)
            
            // Extract luminance (Y channel) only
            for (y in 0 until roiHeight) {
                for (x in 0 until roiWidth) {
                    val frameIndex = (startY + y) * frameWidth + (startX + x)
                    val roiIndex = y * roiWidth + x
                    roiData[roiIndex] = nv21Data[frameIndex]
                }
            }
            
            return roiData
            
        } catch (exception: Exception) {
            Log.e(TAG, "T-103: Failed to extract ROI region", exception)
            return null
        }
    }
    
    /**
     * Detect corner points for perspective transformation
     * Using simplified corner detection based on intensity gradients
     */
    private fun detectCorners(roiData: ByteArray, width: Int, height: Int): Array<CornerPoint>? {
        
        try {
            // T-103: Simplified corner detection - use ROI bounds as initial estimate
            // In future phases, this can be enhanced with proper corner detection algorithms
            
            val topLeft = CornerPoint(0f, 0f)
            val topRight = CornerPoint(width.toFloat(), 0f)
            val bottomLeft = CornerPoint(0f, height.toFloat())
            val bottomRight = CornerPoint(width.toFloat(), height.toFloat())
            
            // TODO T-103 enhancement: Implement proper corner detection using Harris corner detector
            // or similar algorithm for more accurate perspective correction
            
            Log.d(TAG, "T-103: Corner detection completed - using ROI bounds as initial estimate")
            
            return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
            
        } catch (exception: Exception) {
            Log.e(TAG, "T-103: Corner detection failed", exception)
            return null
        }
    }
    
    /**
     * Apply perspective transformation using detected corners
     */
    private fun applyPerspectiveTransform(
        roiData: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        corners: Array<CornerPoint>
    ): ByteArray? {
        
        try {
            // T-103: Simplified perspective transform - for now, use bilinear resize
            // In future phases, implement full perspective transformation matrix
            
            val transformedData = ByteArray(RECTIFIED_WIDTH * RECTIFIED_HEIGHT)
            
            // Simple bilinear interpolation resize
            val scaleX = srcWidth.toFloat() / RECTIFIED_WIDTH
            val scaleY = srcHeight.toFloat() / RECTIFIED_HEIGHT
            
            for (y in 0 until RECTIFIED_HEIGHT) {
                for (x in 0 until RECTIFIED_WIDTH) {
                    val srcX = (x * scaleX).toInt().coerceIn(0, srcWidth - 1)
                    val srcY = (y * scaleY).toInt().coerceIn(0, srcHeight - 1)
                    
                    val srcIndex = srcY * srcWidth + srcX
                    val dstIndex = y * RECTIFIED_WIDTH + x
                    
                    transformedData[dstIndex] = roiData[srcIndex]
                }
            }
            
            Log.d(TAG, "T-103: Perspective transform applied → ${RECTIFIED_WIDTH}x${RECTIFIED_HEIGHT}")
            
            return transformedData
            
        } catch (exception: Exception) {
            Log.e(TAG, "T-103: Perspective transform failed", exception)
            return null
        }
    }
    
    /**
     * Apply rotation correction based on estimated orientation angle
     */
    private fun applyRotationCorrection(data: ByteArray, orientationDegrees: Float): ByteArray {
        
        // T-103: Apply inverse rotation to make bars vertical
        val correctionAngle = -orientationDegrees // Inverse rotation
        
        if (abs(correctionAngle) < 2.0f) {
            // Already sufficiently vertical, no rotation needed
            Log.d(TAG, "T-103: Skipping rotation correction - angle ${orientationDegrees}° within tolerance")
            return data
        }
        
        try {
            val correctedData = ByteArray(data.size)
            val centerX = RECTIFIED_WIDTH / 2f
            val centerY = RECTIFIED_HEIGHT / 2f
            val angleRad = Math.toRadians(correctionAngle.toDouble())
            val cosAngle = cos(angleRad).toFloat()
            val sinAngle = sin(angleRad).toFloat()
            
            // Apply rotation matrix
            for (y in 0 until RECTIFIED_HEIGHT) {
                for (x in 0 until RECTIFIED_WIDTH) {
                    // Translate to center
                    val dx = x - centerX
                    val dy = y - centerY
                    
                    // Apply rotation
                    val rotatedX = dx * cosAngle - dy * sinAngle + centerX
                    val rotatedY = dx * sinAngle + dy * cosAngle + centerY
                    
                    // Bounds check and interpolate
                    val srcX = rotatedX.toInt()
                    val srcY = rotatedY.toInt()
                    
                    val dstIndex = y * RECTIFIED_WIDTH + x
                    
                    if (srcX >= 0 && srcX < RECTIFIED_WIDTH && srcY >= 0 && srcY < RECTIFIED_HEIGHT) {
                        val srcIndex = srcY * RECTIFIED_WIDTH + srcX
                        correctedData[dstIndex] = data[srcIndex]
                    } else {
                        // Fill with average intensity for out-of-bounds
                        correctedData[dstIndex] = 128.toByte()
                    }
                }
            }
            
            Log.d(TAG, "T-103: Rotation correction applied: ${correctionAngle}° → vertical bars")
            
            return correctedData
            
        } catch (exception: Exception) {
            Log.e(TAG, "T-103: Rotation correction failed", exception)
            return data // Return original data if rotation fails
        }
    }
    
    /**
     * Clamp and normalize intensity values
     */
    private fun clampAndNormalize(data: ByteArray): ByteArray {
        
        try {
            // Find intensity range
            var minIntensity = 255
            var maxIntensity = 0
            
            for (byte in data) {
                val intensity = byte.toInt() and 0xFF
                minIntensity = minOf(minIntensity, intensity)
                maxIntensity = maxOf(maxIntensity, intensity)
            }
            
            val range = maxIntensity - minIntensity
            if (range <= 0) {
                Log.w(TAG, "T-103: No intensity variation - returning original data")
                return data
            }
            
            // Normalize to full range [0, 255]
            val normalizedData = ByteArray(data.size)
            for (i in data.indices) {
                val intensity = data[i].toInt() and 0xFF
                val normalized = ((intensity - minIntensity) * 255 / range).coerceIn(0, 255)
                normalizedData[i] = normalized.toByte()
            }
            
            Log.d(TAG, "T-103: Intensity normalization: [$minIntensity, $maxIntensity] → [0, 255]")
            
            return normalizedData
            
        } catch (exception: Exception) {
            Log.e(TAG, "T-103: Normalization failed", exception)
            return data // Return original data if normalization fails
        }
    }
}

/**
 * Corner point for perspective transformation
 */
data class CornerPoint(
    val x: Float,
    val y: Float
)

/**
 * Rectified ROI result with transformation metadata
 */
data class RectifiedRoi(
    val data: ByteArray,                    // Rectified image data
    val width: Int,                         // Rectified width (1024)
    val height: Int,                        // Rectified height (256)
    val originalAngle: Float,               // Original orientation angle
    val corners: Array<CornerPoint>,        // Detected corner points
    val processingTimeMs: Long              // Processing time
) {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as RectifiedRoi
        
        if (!data.contentEquals(other.data)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (originalAngle != other.originalAngle) return false
        if (!corners.contentEquals(other.corners)) return false
        if (processingTimeMs != other.processingTimeMs) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + originalAngle.hashCode()
        result = 31 * result + corners.contentHashCode()
        result = 31 * result + processingTimeMs.hashCode()
        return result
    }
}