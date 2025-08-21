package com.msidecoder.scanner.ui

import android.graphics.Point
import android.graphics.Rect
import android.util.Log

/**
 * T-008: MLKit Coordinate Transformation
 * Transforms MLKit coordinates from camera space to preview display space
 * 
 * Camera space: 640×480 landscape (rotated 90° from display)
 * Display space: Portrait mode (e.g., 1080×2201)
 */
class MLKitCoordinateTransformer {
    
    companion object {
        private const val TAG = "MLKitCoordTransformer"
    }
    
    /**
     * Transform MLKit bounding box from camera space to preview display space
     * 
     * @param cameraBoundingBox Original MLKit bounding box in camera coordinates
     * @param cameraWidth Camera frame width (typically 640)
     * @param cameraHeight Camera frame height (typically 480)  
     * @param previewWidth Preview view width on screen
     * @param previewHeight Preview view height on screen
     * @return Transformed bounding box for display overlay
     */
    fun transformBoundingBox(
        cameraBoundingBox: Rect,
        cameraWidth: Int,
        cameraHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): Rect {
        Log.d(TAG, "=== COORDINATE TRANSFORMATION ===")
        Log.d(TAG, "Input: $cameraBoundingBox in ${cameraWidth}×${cameraHeight}")
        Log.d(TAG, "Target: ${previewWidth}×${previewHeight}")
        
        // Calculate aspect ratios for scaling analysis
        val cameraAspectRatio = cameraWidth.toFloat() / cameraHeight.toFloat()
        val previewAspectRatio = previewWidth.toFloat() / previewHeight.toFloat()
        Log.d(TAG, "Camera aspect: $cameraAspectRatio, Preview aspect: $previewAspectRatio")
        
        // FILL_CENTER: Calculate effective visible area with crop
        val scale = maxOf(
            previewWidth.toFloat() / cameraWidth.toFloat(),
            previewHeight.toFloat() / cameraHeight.toFloat()
        )
        
        val scaledCameraWidth = cameraWidth * scale
        val scaledCameraHeight = cameraHeight * scale
        
        val cropOffsetX = (scaledCameraWidth - previewWidth) / 2.0f
        val cropOffsetY = (scaledCameraHeight - previewHeight) / 2.0f
        
        Log.d(TAG, "FILL_CENTER: scale=$scale")
        Log.d(TAG, "Scaled camera: ${scaledCameraWidth}×${scaledCameraHeight}")
        Log.d(TAG, "Crop offsets: X=$cropOffsetX, Y=$cropOffsetY")
        
        // Step 1: Normalize to [0,1] in camera space
        val normalizedLeft = cameraBoundingBox.left.toFloat() / cameraWidth
        val normalizedTop = cameraBoundingBox.top.toFloat() / cameraHeight
        val normalizedRight = cameraBoundingBox.right.toFloat() / cameraWidth
        val normalizedBottom = cameraBoundingBox.bottom.toFloat() / cameraHeight
        
        Log.d(TAG, "Normalized: L=$normalizedLeft T=$normalizedTop R=$normalizedRight B=$normalizedBottom")
        
        // Step 2: Apply 90° rotation (camera landscape → portrait display)
        // FIXE: QR bas → carré bas, QR droite → carré droite
        // Essai: Camera Y → Display Y, Camera X → Display X (pas de rotation?)
        val rotatedLeft = normalizedLeft
        val rotatedTop = normalizedTop  
        val rotatedRight = normalizedRight
        val rotatedBottom = normalizedBottom
        
        Log.d(TAG, "Rotated (no rotation): L=$rotatedLeft T=$rotatedTop R=$rotatedRight B=$rotatedBottom")
        
        // Step 3: Apply FILL_CENTER transformation with crop compensation
        val displayLeft = (rotatedLeft * cameraWidth * scale - cropOffsetX).toInt()
        val displayTop = (rotatedTop * cameraHeight * scale - cropOffsetY).toInt()  
        val displayRight = (rotatedRight * cameraWidth * scale - cropOffsetX).toInt()
        val displayBottom = (rotatedBottom * cameraHeight * scale - cropOffsetY).toInt()
        
        val transformedRect = Rect(displayLeft, displayTop, displayRight, displayBottom)
        Log.d(TAG, "Final display: $transformedRect")
        
        return transformedRect
    }
    
    /**
     * Transform MLKit corner points from camera space to preview display space
     * 
     * @param cameraCornerPoints Original MLKit corner points in camera coordinates
     * @param cameraWidth Camera frame width
     * @param cameraHeight Camera frame height
     * @param previewWidth Preview view width on screen
     * @param previewHeight Preview view height on screen
     * @return Transformed corner points for display overlay
     */
    fun transformCornerPoints(
        cameraCornerPoints: Array<Point>,
        cameraWidth: Int,
        cameraHeight: Int,
        previewWidth: Int,
        previewHeight: Int
    ): Array<Point> {
        return cameraCornerPoints.map { point ->
            // Normalize to [0,1]
            val normalizedX = point.x.toFloat() / cameraWidth
            val normalizedY = point.y.toFloat() / cameraHeight
            
            // Rotate 90° to match bounding box transform
            val rotatedX = normalizedY
            val rotatedY = 1.0f - normalizedX
            
            // Scale to display
            val displayX = (rotatedX * previewWidth).toInt()
            val displayY = (rotatedY * previewHeight).toInt()
            
            Point(displayX, displayY)
        }.toTypedArray()
    }
}