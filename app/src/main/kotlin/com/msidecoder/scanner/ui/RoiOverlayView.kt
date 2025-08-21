package com.msidecoder.scanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * T-008: ROI Overlay View for diagnosing and displaying barcode detection zones
 * 
 * Phase 1: Basic diagnostic overlay for MLKit coordinate transformation
 */
class RoiOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "RoiOverlayView"
    }
    
    // ROI candidates to display
    private var roiRects = mutableListOf<Rect>()
    private var frameWidth = 0
    private var frameHeight = 0
    
    // Paint for drawing ROI rectangles
    private val roiPaint = Paint().apply {
        color = Color.parseColor("#FF2196F3") // Blue for MLKit
        strokeWidth = 4.0f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    // Paint for debugging info text
    private val debugTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 12.0f * resources.displayMetrics.density
        isAntiAlias = true
        setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK)
    }
    
    
    /**
     * Update ROI rectangles to display
     * @param rects List of ROI rectangles in screen coordinates
     * @param cameraWidth Camera frame width for debugging
     * @param cameraHeight Camera frame height for debugging
     */
    fun updateRoi(rects: List<Rect>, cameraWidth: Int = 0, cameraHeight: Int = 0) {
        Log.d(TAG, "updateRoi called: ${rects.size} rectangles")
        
        roiRects.clear()
        roiRects.addAll(rects)
        frameWidth = cameraWidth
        frameHeight = cameraHeight
        
        rects.forEachIndexed { index, rect ->
            Log.d(TAG, "ROI[$index]: $rect")
        }
        
        // Trigger redraw on UI thread
        post {
            invalidate()
        }
    }
    
    /**
     * Clear all ROI overlays
     */
    fun clearRoi() {
        Log.d(TAG, "clearRoi called")
        roiRects.clear()
        post {
            invalidate()
        }
    }
    
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (roiRects.isEmpty()) {
            return
        }
        
        Log.d(TAG, "onDraw: drawing ${roiRects.size} ROI rectangles")
        Log.d(TAG, "onDraw: view size = ${width}×${height}")
        
        // Draw each ROI rectangle
        roiRects.forEachIndexed { index, rect ->
            // Draw rectangle
            canvas.drawRect(rect, roiPaint)
            
            // Draw debug info
            val debugText = "ROI$index: ${rect.left},${rect.top} ${rect.width()}×${rect.height()}"
            canvas.drawText(
                debugText,
                rect.left.toFloat(),
                (rect.top - 10).toFloat().coerceAtLeast(debugTextPaint.textSize),
                debugTextPaint
            )
            
            Log.d(TAG, "Drew ROI[$index]: $rect")
        }
        
        // Draw frame info if available
        if (frameWidth > 0 && frameHeight > 0) {
            val frameInfo = "Camera: ${frameWidth}×${frameHeight} | View: ${width}×${height}"
            canvas.drawText(
                frameInfo,
                20f,
                height - 40f,
                debugTextPaint
            )
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(TAG, "onSizeChanged: ${oldw}×${oldh} → ${w}×${h}")
    }
}