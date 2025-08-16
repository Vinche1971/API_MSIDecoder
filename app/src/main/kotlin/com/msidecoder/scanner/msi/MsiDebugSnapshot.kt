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
 * MSI Debug Manager - collects and manages debug snapshots
 */
class MsiDebugManager {
    
    private var currentSnapshot: MsiDebugSnapshot? = null
    private var frameCounter = 0L
    
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
     * Get current snapshot for export
     */
    fun getCurrentSnapshot(): MsiDebugSnapshot? = currentSnapshot
    
    /**
     * Get compact overlay status string
     */
    fun getOverlayStatus(): String {
        val snapshot = currentSnapshot ?: return "MSI: —"
        
        return when {
            !snapshot.success -> "MSI: ERROR"
            snapshot.pipelineStage == MsiStage.INIT -> "MSI: INIT"
            snapshot.signalStats != null -> {
                val stats = snapshot.signalStats
                "MSI: ${stats.length}px med=${stats.mean.toInt()}"
            }
            else -> "MSI: ${snapshot.pipelineStage.name}"
        }
    }
}