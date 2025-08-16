package com.msidecoder.scanner.msi

/**
 * T-100: Debug snapshot system for MSI pipeline monitoring
 * Records detailed metrics for each processing stage
 */
data class MsiDebugSnapshot(
    val frameId: String,                    // Unique frame identifier
    val timestamp: Long,                    // Epoch milliseconds
    val pipelineStage: MsiStage,           // Current processing stage
    val success: Boolean,                   // Stage completion success
    val errorMessage: String? = null,       // Error details if failed
    val parameters: MsiParameters,          // Active configuration parameters
    val signalStats: SignalStats? = null,  // Signal analysis (if applicable)
    val roiStats: RoiStats? = null,        // T-101: ROI detection statistics
    val runs: List<Int>? = null,           // Extracted runs (if applicable)
    val moduleWidth: Double? = null        // Detected module width (if applicable)
) {
    
    /**
     * Convert to compact JSON for snapshot integration
     */
    fun toCompactMap(): Map<String, Any?> {
        return mapOf(
            "frameId" to frameId,
            "stage" to pipelineStage.name.lowercase(),
            "success" to success,
            "error" to errorMessage,
            "params" to parameters.toMap(),
            "signal" to signalStats?.toMap(),
            "roi" to roiStats?.toMap(),
            "runs" to runs,
            "module" to moduleWidth
        ).filterValues { it != null }
    }
}

/**
 * MSI processing pipeline stages
 */
enum class MsiStage {
    INIT,           // Initial setup
    ROI_EXTRACT,    // T-101: ROI extraction
    NORMALIZE,      // T-102: Signal normalization
    BINARIZE,       // T-103: Binary conversion
    RUNS_EXTRACT,   // T-104: Runs extraction
    RUNS_NORMALIZE, // T-105: Runs normalization
    COMPLETE        // Pipeline completed
}

/**
 * MSI processing parameters
 */
data class MsiParameters(
    val roiWidth: Int = 1280,              // ROI width in pixels
    val roiBandHeight: Int = 10,           // ROI band height (±N pixels from median)
    val binThreshold: Double = 0.5,        // Binarization threshold
    val filterSize: Int = 3,               // Moving average filter size
    val moduleDetectionMethod: String = "median" // Module detection method
) {
    
    fun toMap(): Map<String, Any> {
        return mapOf(
            "roiWidth" to roiWidth,
            "bandHeight" to roiBandHeight,
            "binThreshold" to binThreshold,
            "filterSize" to filterSize,
            "moduleMethod" to moduleDetectionMethod
        )
    }
}

/**
 * Signal analysis statistics
 */
data class SignalStats(
    val length: Int,                       // Signal length in pixels
    val mean: Double,                      // Average intensity
    val variance: Double,                  // Intensity variance
    val min: Int,                         // Minimum intensity
    val max: Int,                         // Maximum intensity
    val dynamicRange: Int = max - min     // Dynamic range
) {
    
    fun toMap(): Map<String, Any> {
        return mapOf(
            "length" to length,
            "mean" to "%.1f".format(mean),
            "variance" to "%.1f".format(variance),
            "range" to "$min-$max",
            "dynamic" to dynamicRange
        )
    }
}

/**
 * T-101: ROI Detection statistics
 * T-102: Extended with orientation information
 */
data class RoiStats(
    val candidatesFound: Int,              // Number of ROI candidates detected
    val bestScore: Float,                  // Score of best candidate
    val bestCandidate: RoiCandidate?,      // Best ROI candidate details
    val processingTimeMs: Long,            // ROI detection processing time
    val gradientThreshold: Float,          // Gradient threshold used
    val morphoKernelSize: Int,             // Morphological kernel size
    val estimatedAngle: Float = 0.0f       // T-102: Estimated orientation angle (degrees)
) {
    
    fun toMap(): Map<String, Any> {
        return mapOf(
            "candidates" to candidatesFound,
            "bestScore" to "%.2f".format(bestScore),
            "bestROI" to (bestCandidate?.let { 
                "${it.x},${it.y} ${it.width}x${it.height}" 
            } ?: "none"),
            "procTimeMs" to processingTimeMs,
            "gradThresh" to gradientThreshold,
            "morphKernel" to morphoKernelSize,
            "angle" to "%.1f°".format(estimatedAngle)  // T-102: Include orientation angle
        )
    }
}

/**
 * MSI Debug Manager - collects and manages debug snapshots
 */
class MsiDebugManager {
    
    private var currentSnapshot: MsiDebugSnapshot? = null
    private var lastRoiSnapshot: MsiDebugSnapshot? = null  // T-101: Persist last ROI detection
    private var lastRoiTimestamp = 0L  // T-101: Track when last ROI was detected
    private var frameCounter = 0L
    
    companion object {
        private const val ROI_PERSIST_DURATION_MS = 2500L  // T-101: Keep ROI visible for 2.5s
    }
    
    /**
     * Start new frame processing
     */
    fun startFrame(): String {
        frameCounter++
        val frameId = "msi_${frameCounter}"
        
        currentSnapshot = MsiDebugSnapshot(
            frameId = frameId,
            timestamp = System.currentTimeMillis(),
            pipelineStage = MsiStage.INIT,
            success = true,
            parameters = MsiParameters() // Default parameters
        )
        
        return frameId
    }
    
    /**
     * Update processing stage
     */
    fun updateStage(stage: MsiStage, success: Boolean = true, error: String? = null) {
        currentSnapshot = currentSnapshot?.copy(
            pipelineStage = stage,
            success = success,
            errorMessage = error,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Add signal analysis
     */
    fun addSignalStats(stats: SignalStats) {
        currentSnapshot = currentSnapshot?.copy(signalStats = stats)
    }
    
    /**
     * T-101: Add ROI detection statistics
     */
    fun addRoiStats(stats: RoiStats) {
        currentSnapshot = currentSnapshot?.copy(roiStats = stats)
        
        // T-101: Si des candidats ROI sont trouvés, sauvegarder pour persistance
        if (stats.candidatesFound > 0) {
            lastRoiSnapshot = currentSnapshot
            lastRoiTimestamp = System.currentTimeMillis()
            android.util.Log.d("MsiDebugManager", "T-101: ROI détecté - sauvegarde pour persistance (${stats.candidatesFound} candidats)")
        }
    }
    
    /**
     * Add extracted runs
     */
    fun addRuns(runs: List<Int>) {
        currentSnapshot = currentSnapshot?.copy(runs = runs)
    }
    
    /**
     * Add module width detection
     */
    fun addModuleWidth(width: Double) {
        currentSnapshot = currentSnapshot?.copy(moduleWidth = width)
    }
    
    /**
     * Get current snapshot for export - T-101: with ROI persistence
     */
    fun getCurrentSnapshot(): MsiDebugSnapshot? {
        val current = currentSnapshot
        val now = System.currentTimeMillis()
        
        // T-101: Si pas de ROI dans snapshot courant, mais on a une détection récente, l'utiliser
        return if (current?.roiStats == null && 
                   lastRoiSnapshot?.roiStats != null && 
                   (now - lastRoiTimestamp) < ROI_PERSIST_DURATION_MS) {
            
            android.util.Log.d("MsiDebugManager", "T-101: Utilisation ROI persistante (${now - lastRoiTimestamp}ms ago)")
            // Retourner snapshot courant avec ROI persistante
            current?.copy(roiStats = lastRoiSnapshot?.roiStats) ?: lastRoiSnapshot
        } else {
            current
        }
    }
    
    /**
     * Get compact overlay status string
     */
    fun getOverlayStatus(): String {
        val snapshot = currentSnapshot ?: return "MSI: —"
        
        return when {
            !snapshot.success -> "MSI: ERROR"
            snapshot.pipelineStage == MsiStage.INIT -> "MSI: INIT"
            snapshot.roiStats != null -> {
                val roi = snapshot.roiStats
                "MSI: ${roi.candidatesFound} ROI max=${roi.bestScore.let { "%.2f".format(it) }}"
            }
            snapshot.signalStats != null -> {
                val stats = snapshot.signalStats
                "MSI: ${stats.length}px med=${stats.mean.toInt()}"
            }
            else -> "MSI: ${snapshot.pipelineStage.name}"
        }
    }
}