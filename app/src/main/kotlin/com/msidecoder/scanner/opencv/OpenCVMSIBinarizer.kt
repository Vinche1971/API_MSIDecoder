package com.msidecoder.scanner.opencv

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * OpenCV documented binarization result structure
 * From 03-Pipeline-Binarisation.md specification
 */
data class BinarizationResult(
    val binaryMat: Mat,
    val method: String,
    val threshold: Double,
    val confidence: Float
) {
    fun copy(confidence: Float): BinarizationResult {
        return BinarizationResult(binaryMat, method, threshold, confidence)
    }
}

/**
 * T-103: OpenCV MSI Barcode Binarizer
 * 
 * Converts detected ROI regions into high-quality binary profiles suitable for MSI decoding.
 * Implements adaptive thresholding, perspective correction, and quality validation.
 * 
 * Based on OpenCV/03-Pipeline-Binarisation.md specification
 */
class OpenCVMSIBinarizer(private val visualDebugger: VisualDebugger? = null) {
    
    companion object {
        private const val TAG = "OpenCVMSIBinarizer"
        
        // ROI extraction parameters (optimized for high resolution ~1080×1920)
        private const val ROI_MARGIN_PERCENT = 0.15f           // 15% margin around detected ROI
        private const val MIN_EXTRACT_WIDTH = 80               // Minimum extraction width (high-res optimized)
        private const val MIN_EXTRACT_HEIGHT = 25              // Minimum extraction height (high-res optimized)
        
        // Normalization parameters
        private const val TARGET_HEIGHT_HORIZONTAL = 60        // Target height for horizontal codes
        private const val MAX_NORMALIZED_SIZE = 800            // Maximum size after normalization
        
        // Binarization parameters
        private const val ADAPTIVE_BLOCK_SIZE = 15             // Adaptive threshold block size
        private const val ADAPTIVE_C = -2.0                   // Adaptive threshold constant
        private const val OTSU_THRESHOLD = 0.0                // OTSU automatic threshold
        
        // Quality validation
        private const val MIN_BINARY_CONTRAST = 50.0          // Minimum contrast for quality
        private const val MIN_TRANSITION_COUNT = 8            // Minimum bar/space transitions
        private const val MIN_QUALITY_SCORE = 0.3f            // Minimum acceptable quality
    }
    
    /**
     * Binarize ROI candidate into binary profile suitable for MSI decoding
     * 
     * @param grayMat Original grayscale image
     * @param roiCandidate ROI to binarize
     * @param debugSession Debug session ID for visual debugging (optional)
     * @return BinaryProfile with ASCII visualization, null if binarization failed
     */
    fun binarizeROI(grayMat: Mat, roiCandidate: ROICandidate, debugSession: String? = null): BinaryProfile? {
        val startTime = System.currentTimeMillis()
        val session = debugSession ?: visualDebugger?.createDebugSession() ?: "unknown"
        
        try {
            Log.v(TAG, "Binarizing ROI: $roiCandidate")
            
            // Step 1: Extract ROI with margins
            val extractedROI = extractROIWithMargin(grayMat, roiCandidate)
            if (extractedROI.empty()) {
                Log.w(TAG, "Failed to extract ROI")
                return null
            }
            
            // Debug: Save extracted ROI
            visualDebugger?.saveGrayscaleImage(extractedROI, "${session}_06_extracted_roi")
            
            // Step 2: Preprocess ROI (normalization, denoising)
            val preprocessedROI = preprocessROI(extractedROI)
            
            // Debug: Save preprocessed ROI
            visualDebugger?.saveGrayscaleImage(preprocessedROI, "${session}_07_preprocessed")
            
            // Step 3: Multi-method binarization
            val binarizedROI = performBinarization(preprocessedROI)
            if (binarizedROI.empty()) {
                Log.w(TAG, "Binarization failed")
                cleanup(extractedROI, preprocessedROI, binarizedROI)
                return null
            }
            
            // Debug: Save binarization result
            visualDebugger?.saveBinarizedBarcode(binarizedROI, "${session}_08_binarized")
            
            // Step 4: Extract binary profile (scan line analysis)
            val binaryProfile = extractBinaryProfile(binarizedROI, roiCandidate.aspectRatio)
            
            // Step 5: Quality validation
            if (binaryProfile == null || !binaryProfile.isValidMSI()) {
                Log.v(TAG, "Binary profile quality insufficient: ${binaryProfile?.quality ?: "null"}")
                cleanup(extractedROI, preprocessedROI, binarizedROI)
                return null
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "ROI binarized successfully in ${processingTime}ms")
            Log.d(TAG, binaryProfile.toDebugString())
            
            // Debug: Log binary pattern visualization
            Log.d(TAG, "BINARY PATTERN: ${binaryProfile.toASCII()}")
            
            // Cleanup matrices
            cleanup(extractedROI, preprocessedROI, binarizedROI)
            
            return binaryProfile
            
        } catch (exception: Exception) {
            Log.e(TAG, "ROI binarization failed: ${exception.message}", exception)
            return null
        }
    }
    
    /**
     * Step 1: Extract ROI with margins for better processing
     */
    private fun extractROIWithMargin(grayMat: Mat, roiCandidate: ROICandidate): Mat {
        val roi = roiCandidate.boundingRect
        
        // Calculate margins
        val marginX = (roi.width() * ROI_MARGIN_PERCENT).toInt()
        val marginY = (roi.height() * ROI_MARGIN_PERCENT).toInt()
        
        // Apply margins with bounds checking
        val expandedLeft = max(0, roi.left - marginX)
        val expandedTop = max(0, roi.top - marginY)
        val expandedRight = min(grayMat.cols(), roi.right + marginX)
        val expandedBottom = min(grayMat.rows(), roi.bottom + marginY)
        
        val expandedRect = org.opencv.core.Rect(
            expandedLeft, expandedTop,
            expandedRight - expandedLeft, expandedBottom - expandedTop
        )
        
        // Validate minimum size
        if (expandedRect.width < MIN_EXTRACT_WIDTH || expandedRect.height < MIN_EXTRACT_HEIGHT) {
            Log.w(TAG, "Expanded ROI too small: ${expandedRect.width}x${expandedRect.height}")
            return Mat()
        }
        
        return Mat(grayMat, expandedRect)
    }
    
    /**
     * Step 2: Preprocess ROI (normalization, denoising)
     */
    private fun preprocessROI(roiMat: Mat): Mat {
        val preprocessed = Mat()
        
        // Normalize height for consistent processing
        val currentHeight = roiMat.rows()
        val targetHeight = TARGET_HEIGHT_HORIZONTAL
        
        if (currentHeight != targetHeight && currentHeight > 10) {
            val scale = targetHeight.toDouble() / currentHeight.toDouble()
            val newWidth = (roiMat.cols() * scale).toInt()
            
            if (newWidth > 0 && newWidth <= MAX_NORMALIZED_SIZE) {
                Imgproc.resize(roiMat, preprocessed, Size(newWidth.toDouble(), targetHeight.toDouble()))
            } else {
                roiMat.copyTo(preprocessed)
            }
        } else {
            roiMat.copyTo(preprocessed)
        }
        
        // Light denoising with Gaussian blur
        val denoised = Mat()
        Imgproc.GaussianBlur(preprocessed, denoised, Size(3.0, 3.0), 0.0)
        preprocessed.release()
        
        return denoised
    }
    
    /**
     * Step 3: OpenCV documented 4-method adaptive binarization
     * EXACTLY as specified in 03-Pipeline-Binarisation.md
     */
    private fun performBinarization(preprocessedROI: Mat): Mat {
        val binarizationResult = performAdaptiveBinarization(preprocessedROI)
        Log.v(TAG, "Selected method: ${binarizationResult.method} (confidence: ${"%.3f".format(binarizationResult.confidence)})")
        return binarizationResult.binaryMat
    }
    
    /**
     * OpenCV documented multi-method binarization with quality evaluation
     */
    private fun performAdaptiveBinarization(sourceMat: Mat): BinarizationResult {
        val methods = listOf<() -> BinarizationResult>(
            { otsuThresholding(sourceMat) },
            { adaptiveGaussianThresholding(sourceMat) },
            { adaptiveMeanThresholding(sourceMat) },
            { triangleThresholding(sourceMat) }
        )
        
        var bestResult: BinarizationResult? = null
        var bestScore = 0.0
        
        for ((index, method) in methods.withIndex()) {
            try {
                val result = method()
                val score = evaluateBinarizationQuality(result.binaryMat)
                
                Log.v(TAG, "Method $index (${result.method}): score=${String.format("%.3f", score)}")
                
                if (score > bestScore) {
                    bestResult?.binaryMat?.release() // Release previous best
                    bestResult = result.copy(confidence = score.toFloat())
                    bestScore = score
                } else {
                    result.binaryMat.release() // Release non-retained result
                }
                
            } catch (exception: Exception) {
                Log.w(TAG, "Binarization method $index failed", exception)
            }
        }
        
        return bestResult ?: BinarizationResult(
            binaryMat = Mat.zeros(sourceMat.size(), CvType.CV_8UC1),
            method = "fallback",
            threshold = 127.0,
            confidence = 0.0f
        )
    }
    
    // OpenCV documented binarization methods (EXACT implementation)
    
    private fun otsuThresholding(sourceMat: Mat): BinarizationResult {
        val binary = Mat()
        val threshold = Imgproc.threshold(sourceMat, binary, 0.0, 255.0, 
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        
        return BinarizationResult(binary, "Otsu", threshold, 0.0f)
    }

    private fun adaptiveGaussianThresholding(sourceMat: Mat): BinarizationResult {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            sourceMat, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            15,   // Block size (OpenCV spec)
            5.0   // C constant (OpenCV spec)
        )
        
        return BinarizationResult(binary, "AdaptiveGaussian", -1.0, 0.0f)
    }

    private fun adaptiveMeanThresholding(sourceMat: Mat): BinarizationResult {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            sourceMat, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY,
            11,   // Block size (OpenCV spec)  
            3.0   // C constant (OpenCV spec)
        )
        
        return BinarizationResult(binary, "AdaptiveMean", -1.0, 0.0f)
    }
    
    private fun triangleThresholding(sourceMat: Mat): BinarizationResult {
        val binary = Mat()
        val threshold = Imgproc.threshold(sourceMat, binary, 0.0, 255.0, 
            Imgproc.THRESH_BINARY + Imgproc.THRESH_TRIANGLE)
        
        return BinarizationResult(binary, "Triangle", threshold, 0.0f)
    }
    
    /**
     * OpenCV documented quality evaluation (EXACT from 03-Pipeline-Binarisation.md)
     */
    private fun evaluateBinarizationQuality(binaryMat: Mat): Double {
        var qualityScore = 0.0
        
        try {
            // Facteur 1: Contraste binaire (séparation noir/blanc)
            val mean = Core.mean(binaryMat).`val`[0]
            val contrastScore = when {
                mean < 50 || mean > 205 -> 1.0      // Bon contraste (très noir ou très blanc)
                mean < 80 || mean > 175 -> 0.7      // Contraste moyen
                else -> 0.3                          // Faible contraste
            }
            
            // Facteur 2: Nombre de transitions (barres/espaces)
            val transitionCount = countBinaryTransitions(binaryMat)
            val transitionScore = when {
                transitionCount >= MIN_TRANSITION_COUNT -> 1.0
                transitionCount >= MIN_TRANSITION_COUNT / 2 -> 0.6
                else -> 0.2
            }
            
            // Facteur 3: Régularité espacements (codes-barres ont pattern régulier)
            val regularityScore = evaluateSpacingRegularity(binaryMat)
            
            // Facteur 4: Ratio pixels noirs/blancs (codes MSI ~40-60% noir)
            val blackPixels = binaryMat.total() - Core.countNonZero(binaryMat)
            val blackRatio = blackPixels.toDouble() / binaryMat.total()
            val ratioScore = when {
                blackRatio in 0.3..0.7 -> 1.0      // Ratio optimal
                blackRatio in 0.2..0.8 -> 0.7      // Acceptable
                else -> 0.3                          // Non optimal
            }
            
            // Combinaison pondérée (OpenCV spec)
            qualityScore = (contrastScore * 0.3) + 
                          (transitionScore * 0.4) + 
                          (regularityScore * 0.2) + 
                          (ratioScore * 0.1)
            
        } catch (exception: Exception) {
            Log.w(TAG, "Quality evaluation failed", exception)
            qualityScore = 0.1
        }
        
        return qualityScore.coerceIn(0.0, 1.0)
    }
    
    /**
     * Count transitions along middle scan line (OpenCV spec)
     */
    private fun countBinaryTransitions(binaryMat: Mat): Int {
        try {
            // Scan along the middle line horizontally (most robust for 1D codes)
            val midRow = binaryMat.height() / 2
            val rowData = ByteArray(binaryMat.width())
            binaryMat.get(midRow, 0, rowData)
            
            return countTransitionsInArray(rowData)
            
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to count transitions", exception)
            return 0
        }
    }
    
    /**
     * Count transitions in scan line data
     */
    private fun countTransitionsInArray(data: ByteArray): Int {
        if (data.size < 2) return 0
        
        var transitions = 0
        var currentState = data[0] > 127  // true = blanc, false = noir
        
        for (i in 1 until data.size) {
            val newState = data[i] > 127
            if (newState != currentState) {
                transitions++
                currentState = newState
            }
        }
        
        return transitions
    }
    
    /**
     * Evaluate spacing regularity (simplified for now)
     */
    private fun evaluateSpacingRegularity(binaryMat: Mat): Double {
        // Simplified: check if we have reasonable transitions pattern
        val transitions = countBinaryTransitions(binaryMat)
        return if (transitions >= 8 && transitions <= 50) 0.8 else 0.4
    }
    
    /**
     * Step 4: Extract binary profile from binarized image
     */
    private fun extractBinaryProfile(binaryMat: Mat, aspectRatio: Float): BinaryProfile? {
        try {
            // Scan along the middle line horizontally (most robust for 1D codes)
            val scanLine = binaryMat.rows() / 2
            val width = binaryMat.cols()
            
            if (width < 20) {
                Log.w(TAG, "Binary matrix too narrow for scan line: $width")
                return null
            }
            
            // Extract pixel values along scan line
            val scanData = ByteArray(width)
            binaryMat.get(scanLine, 0, scanData)
            
            // Convert to boolean array (true = black bar, false = white space)
            val pattern = BooleanArray(width)
            for (i in scanData.indices) {
                pattern[i] = (scanData[i].toInt() and 0xFF) < 128 // Threshold at 128
            }
            
            // Calculate metrics
            val transitionCount = countTransitions(pattern)
            val averageBarWidth = calculateAverageBarWidth(pattern)
            val quality = calculateQuality(pattern, transitionCount, averageBarWidth)
            
            return BinaryProfile(
                pattern = pattern,
                quality = quality,
                aspectRatio = aspectRatio,
                transitionCount = transitionCount,
                averageBarWidth = averageBarWidth
            )
            
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to extract binary profile: ${exception.message}", exception)
            return null
        }
    }
    
    /**
     * Calculate contrast metric for binarization method selection
     */
    private fun calculateContrast(binaryMat: Mat): Double {
        val mean = Core.mean(binaryMat)
        val stdDev = MatOfDouble()
        val meanMat = MatOfDouble()
        
        Core.meanStdDev(binaryMat, meanMat, stdDev)
        val contrast = stdDev.get(0, 0)[0]
        
        stdDev.release()
        meanMat.release()
        
        return contrast
    }
    
    /**
     * Count bar/space transitions in binary pattern
     */
    private fun countTransitions(pattern: BooleanArray): Int {
        if (pattern.size < 2) return 0
        
        var transitions = 0
        var previous = pattern[0]
        
        for (i in 1 until pattern.size) {
            if (pattern[i] != previous) {
                transitions++
                previous = pattern[i]
            }
        }
        
        return transitions
    }
    
    /**
     * Calculate average width of bars in the pattern
     */
    private fun calculateAverageBarWidth(pattern: BooleanArray): Float {
        if (pattern.isEmpty()) return 0f
        
        var barWidthSum = 0
        var barCount = 0
        var currentWidth = 0
        var inBar = false
        
        for (pixel in pattern) {
            if (pixel) { // In bar
                if (!inBar) {
                    inBar = true
                    currentWidth = 1
                } else {
                    currentWidth++
                }
            } else { // In space
                if (inBar) {
                    barWidthSum += currentWidth
                    barCount++
                    inBar = false
                }
            }
        }
        
        // Handle case where pattern ends in a bar
        if (inBar) {
            barWidthSum += currentWidth
            barCount++
        }
        
        return if (barCount > 0) barWidthSum.toFloat() / barCount else 0f
    }
    
    /**
     * Calculate overall quality score for binary profile
     */
    private fun calculateQuality(pattern: BooleanArray, transitions: Int, avgBarWidth: Float): Float {
        if (pattern.isEmpty() || transitions < MIN_TRANSITION_COUNT) return 0f
        
        // Quality factors
        val transitionScore = min(transitions.toFloat() / 20f, 1f) // More transitions = better
        val consistencyScore = if (avgBarWidth > 1f) min(10f / avgBarWidth, 1f) else 0f // Consistent bar widths
        val lengthScore = min(pattern.size.toFloat() / 100f, 1f) // Reasonable length
        
        return (transitionScore * 0.5f + consistencyScore * 0.3f + lengthScore * 0.2f).coerceIn(0f, 1f)
    }
    
    /**
     * Cleanup OpenCV matrices to prevent memory leaks
     */
    private fun cleanup(vararg mats: Mat) {
        for (mat in mats) {
            if (!mat.empty()) {
                mat.release()
            }
        }
    }
}