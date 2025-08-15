package com.msidecoder.scanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.msidecoder.scanner.utils.MetricsCollector

class MetricsOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0) // Semi-transparent black
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val titlePaint = Paint().apply {
        color = Color.CYAN
        textSize = 40f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var metricsSnapshot: MetricsCollector.Snapshot? = null
    private val padding = 24f
    private val lineHeight = 50f

    fun updateMetrics(snapshot: MetricsCollector.Snapshot) {
        metricsSnapshot = snapshot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val snapshot = metricsSnapshot ?: return
        
        val metrics = mutableListOf<String>().apply {
            add("MSI Scanner Metrics")
            add("")
            add("FPS: ${String.format("%.1f", snapshot.fps)}")
            add("Proc: ${String.format("%.1f", snapshot.avgProcessingTimeMs)} ms")
            add("Queue: ${snapshot.queueSize}")
            add("Res: ${snapshot.resolution}")
            add("")
            add("ML: ${snapshot.mlkitTimeMs} ms, hits: ${snapshot.mlkitHits}")
            add("MSI: ${if (snapshot.msiTimeMs > 0) "${snapshot.msiTimeMs} ms" else "—"}")
            add("SRC: ${snapshot.lastScanSource}")
        }

        // Calculate background size
        val maxTextWidth = metrics.maxOfOrNull { 
            if (it.isEmpty()) 0f else {
                val paint = if (it == metrics.first()) titlePaint else textPaint
                paint.measureText(it)
            }
        } ?: 0f
        
        val backgroundWidth = maxTextWidth + padding * 2
        val backgroundHeight = metrics.size * lineHeight + padding * 2
        
        // Draw background
        val backgroundRect = RectF(
            padding,
            padding,
            padding + backgroundWidth,
            padding + backgroundHeight
        )
        canvas.drawRoundRect(backgroundRect, 12f, 12f, backgroundPaint)
        
        // Draw text
        var yPosition = padding * 2 + lineHeight
        metrics.forEachIndexed { index, text ->
            if (text.isNotEmpty()) {
                val paint = if (index == 0) titlePaint else textPaint
                canvas.drawText(text, padding * 2, yPosition, paint)
            }
            yPosition += lineHeight
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always take full size of parent
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}