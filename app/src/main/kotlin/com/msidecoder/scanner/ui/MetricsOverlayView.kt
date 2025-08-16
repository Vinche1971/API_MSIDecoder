package com.msidecoder.scanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.msidecoder.scanner.utils.MetricsCollector
import com.msidecoder.scanner.msi.MsiDebugSnapshot

class MetricsOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        color = Color.argb(190, 0, 0, 0) // Semi-transparent black (plus opaque)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f  // Plus large pour overlay unifié
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val titlePaint = Paint().apply {
        color = Color.CYAN
        textSize = 36f  // Titre principal plus grand
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    private val msiTitlePaint = Paint().apply {
        color = Color.argb(255, 255, 165, 0) // Orange vif pour MSI
        textSize = 34f
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    private val msiTextPaint = Paint().apply {
        color = Color.argb(255, 255, 215, 0) // Jaune doré pour texte MSI
        textSize = 30f
        isAntiAlias = true
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private var metricsSnapshot: MetricsCollector.Snapshot? = null
    private var msiSnapshot: MsiDebugSnapshot? = null
    private val padding = 32f
    private val lineHeight = 42f
    private val sectionSpacing = 20f  // Espacement entre sections

    fun updateMetrics(snapshot: MetricsCollector.Snapshot) {
        metricsSnapshot = snapshot
        invalidate()
    }
    
    /**
     * T-101: Update MSI debug snapshot for detailed ROI display
     */
    fun updateMsiSnapshot(snapshot: MsiDebugSnapshot?) {
        msiSnapshot = snapshot
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val snapshot = metricsSnapshot ?: return
        
        // Section 1: Métriques générales
        val generalMetrics = mutableListOf<String>().apply {
            add("MSI Scanner")
            add("FPS: ${String.format("%.1f", snapshot.fps)}  Proc: ${String.format("%.1f", snapshot.avgProcessingTimeMs)}ms  Queue: ${snapshot.queueSize}")
            add("ML: ${snapshot.mlkitTimeMs}ms hits: ${snapshot.mlkitHits}  SRC: ${snapshot.lastScanSource}")
        }
        
        // Section 2: MSI ROI Detection
        val msiMetrics = buildMsiSection()
        
        // Calculer la largeur totale (80% de l'écran)
        val screenWidth = width.toFloat()
        val overlayWidth = (screenWidth * 0.8f).coerceAtLeast(400f)
        val startX = (screenWidth - overlayWidth) / 2f
        
        // Calculer hauteur totale
        val generalHeight = generalMetrics.size * lineHeight
        val msiHeight = msiMetrics.size * lineHeight
        val totalHeight = generalHeight + sectionSpacing + msiHeight + padding * 2
        
        // Dessiner background unifié
        val backgroundRect = RectF(
            startX,
            padding,
            startX + overlayWidth,
            padding + totalHeight
        )
        canvas.drawRoundRect(backgroundRect, 16f, 16f, backgroundPaint)
        
        // Dessiner métriques générales
        var yPosition = padding * 2 + lineHeight
        generalMetrics.forEachIndexed { index, text ->
            val paint = if (index == 0) titlePaint else textPaint
            val textX = startX + padding
            canvas.drawText(text, textX, yPosition, paint)
            yPosition += lineHeight
        }
        
        // Espacement entre sections
        yPosition += sectionSpacing
        
        // Dessiner section MSI
        msiMetrics.forEachIndexed { index, text ->
            val paint = if (index == 0) msiTitlePaint else msiTextPaint
            val textX = startX + padding
            canvas.drawText(text, textX, yPosition, paint)
            yPosition += lineHeight
        }
    }
    
    /**
     * T-101: Construire la section MSI avec détails ROI
     * T-102: Extended with orientation angle display
     */
    private fun buildMsiSection(): List<String> {
        val msi = msiSnapshot
        
        return if (msi?.roiStats != null) {
            val roi = msi.roiStats
            mutableListOf<String>().apply {
                add("🎯 MSI ROI DETECTION")
                add("Candidats: ${roi.candidatesFound} trouvés")
                if (roi.bestCandidate != null) {
                    val candidate = roi.bestCandidate
                    add("Meilleur: Score ${String.format("%.2f", roi.bestScore)} → (${candidate.x},${candidate.y}) ${candidate.width}×${candidate.height}px")
                    // T-102: Display orientation angle
                    add("Orientation: ${String.format("%.1f", roi.estimatedAngle)}° (Structure Tensor)")
                } else {
                    add("Meilleur: Aucun candidat valide")
                    add("Orientation: N/A")
                }
                add("Temps: ${roi.processingTimeMs}ms  Status: ${if (roi.candidatesFound > 0) "✅ DETECTED" else "❌ NO ROI"}")
            }
        } else {
            mutableListOf<String>().apply {
                add("🎯 MSI ROI DETECTION")
                add("Status: En attente...")
                add("Recherche de régions d'intérêt...")
                add("Orientation: —")
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always take full size of parent
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
    
    /**
     * T-100: Legacy method - now uses updateMsiSnapshot
     */
    @Deprecated("Use updateMsiSnapshot instead")
    fun updateMsiDebugStatus(status: String) {
        // Compatibility - ne fait plus rien, utiliser updateMsiSnapshot
    }
}