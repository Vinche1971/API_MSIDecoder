package com.msidecoder.scanner.opencv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Environment
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Visual debugger for OpenCV MSI detection pipeline
 * Saves intermediate images to help diagnose what's happening
 */
class VisualDebugger(private val context: Context) {
    
    companion object {
        private const val TAG = "VisualDebugger"
        private const val DEBUG_DIR = "MSI_Debug"
        private var debugCounter = 0
    }
    
    private val dateFormat = SimpleDateFormat("HHmmss-SSS", Locale.getDefault())
    
    /**
     * Save grayscale Mat as image file
     */
    fun saveGrayscaleImage(mat: Mat, filename: String) {
        try {
            if (mat.empty()) {
                Log.w(TAG, "Cannot save empty mat: $filename")
                return
            }
            
            val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bitmap)
            
            saveBitmap(bitmap, filename)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save grayscale image $filename: ${e.message}")
        }
    }
    
    /**
     * Save original frame with ROI rectangles overlaid (using ImageProxy for correct format)
     */
    fun saveFrameWithROIs(imageProxy: androidx.camera.core.ImageProxy?, 
                         rois: List<ROICandidate>, filename: String) {
        try {
            if (imageProxy?.image == null) {
                Log.w(TAG, "Cannot save frame with ROIs: ImageProxy or Image is null")
                return
            }
            
            // Use the corrected OpenCV conversion to get clean grayscale Mat
            val grayMat = com.msidecoder.scanner.opencv.OpenCVConverter.imageProxyToGrayscaleMat(imageProxy)
            if (grayMat == null) {
                Log.w(TAG, "Failed to convert ImageProxy to Mat for debug")
                return
            }
            
            // Convert Mat to Bitmap properly
            val bitmap = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(grayMat, bitmap)
            grayMat.release()
            
            val canvas = Canvas(bitmap)
            
            // Draw ROI rectangles
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }
            
            rois.forEachIndexed { index, roi ->
                // Color based on confidence
                paint.color = when {
                    roi.confidence >= 0.8f -> Color.GREEN    // High confidence
                    roi.confidence >= 0.5f -> Color.YELLOW   // Medium confidence  
                    else -> Color.RED                         // Low confidence
                }
                
                canvas.drawRect(roi.boundingRect, paint)
                
                // Draw confidence text
                paint.textSize = 24f
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                val text = "${index + 1}: ${String.format("%.2f", roi.confidence)}"
                canvas.drawText(text, 
                    roi.boundingRect.left.toFloat(), 
                    roi.boundingRect.top.toFloat() - 8f, paint)
                paint.style = Paint.Style.STROKE
            }
            
            saveBitmap(bitmap, filename)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save frame with ROIs $filename: ${e.message}")
        }
    }
    
    /**
     * Save individual ROI as cropped image
     */
    fun saveROICrop(originalMat: Mat, roi: ROICandidate, roiIndex: Int, baseFilename: String) {
        try {
            val opencvRect = org.opencv.core.Rect(
                roi.boundingRect.left,
                roi.boundingRect.top,
                roi.boundingRect.width(),
                roi.boundingRect.height()
            )
            
            // Safety check for bounds
            if (opencvRect.x >= 0 && opencvRect.y >= 0 && 
                opencvRect.x + opencvRect.width <= originalMat.cols() && 
                opencvRect.y + opencvRect.height <= originalMat.rows()) {
                
                val roiMat = Mat(originalMat, opencvRect)
                val filename = "${baseFilename}_ROI_${roiIndex + 1}_conf_${String.format("%.2f", roi.confidence)}"
                
                saveGrayscaleImage(roiMat, filename)
                roiMat.release()
            } else {
                Log.w(TAG, "ROI bounds invalid: $opencvRect vs ${originalMat.cols()}x${originalMat.rows()}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ROI crop: ${e.message}")
        }
    }
    
    /**
     * Save binarized barcode attempt
     */
    fun saveBinarizedBarcode(binaryMat: Mat, filename: String) {
        saveGrayscaleImage(binaryMat, "${filename}_binary")
    }
    
    /**
     * Create debug session with timestamp
     */
    fun createDebugSession(): String {
        debugCounter++
        return "${dateFormat.format(Date())}_${debugCounter}"
    }
    
    private fun saveBitmap(bitmap: Bitmap, filename: String) {
        try {
            val debugDir = getDebugDirectory()
            if (debugDir == null) {
                Log.e(TAG, "Cannot create debug directory")
                return
            }
            
            val file = File(debugDir, "$filename.png")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            
            Log.d(TAG, "Saved debug image: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap $filename: ${e.message}")
        }
    }
    
    private fun getDebugDirectory(): File? {
        return try {
            // Try external storage first (Pictures/MSI_Debug)
            val externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val debugDir = File(externalDir, DEBUG_DIR)
            
            if (!debugDir.exists() && !debugDir.mkdirs()) {
                // Fallback to internal storage
                val internalDir = File(context.filesDir, DEBUG_DIR)
                if (!internalDir.exists() && !internalDir.mkdirs()) {
                    Log.e(TAG, "Cannot create internal debug directory")
                    return null
                }
                Log.w(TAG, "Using internal storage for debug: ${internalDir.absolutePath}")
                internalDir
            } else {
                Log.d(TAG, "Using external storage for debug: ${debugDir.absolutePath}")
                debugDir
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating debug directory: ${e.message}")
            null
        }
    }
}