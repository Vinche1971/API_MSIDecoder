package com.msidecoder.scanner.opencv

import android.graphics.Rect

/**
 * T-102: ROI Candidate for barcode detection
 * 
 * Represents a region of interest that potentially contains a 1D barcode (MSI)
 */
data class ROICandidate(
    /**
     * Bounding rectangle of the detected region
     */
    val boundingRect: Rect,
    
    /**
     * Confidence score (0.0 to 1.0) indicating likelihood of containing a barcode
     */
    val confidence: Float,
    
    /**
     * Aspect ratio (width/height) of the detected region
     */
    val aspectRatio: Float,
    
    /**
     * Average gradient magnitude in the region
     */
    val gradientMagnitude: Double,
    
    /**
     * Rotation angle in degrees (0, 90, 180, 270) - for future multi-orientation support
     */
    val rotationAngle: Int = 0
) {
    /**
     * Get the center point of the ROI
     */
    val centerX: Int get() = boundingRect.centerX()
    val centerY: Int get() = boundingRect.centerY()
    
    /**
     * Get dimensions
     */
    val width: Int get() = boundingRect.width()
    val height: Int get() = boundingRect.height()
    
    /**
     * Check if this ROI is valid for barcode processing
     */
    fun isValidBarcode(): Boolean {
        return confidence > 0.3f && 
               aspectRatio > 2.0f && 
               width > 50 && 
               height > 10
    }
    
    override fun toString(): String {
        return "ROI(rect=$boundingRect, conf=${"%.2f".format(confidence)}, ratio=${"%.1f".format(aspectRatio)}, grad=${"%.1f".format(gradientMagnitude)})"
    }
}