package com.msidecoder.scanner.opencv

import android.graphics.Rect
import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.math.*

/**
 * T-102: OpenCV MSI Barcode ROI Detector
 * 
 * Detects regions of interest (ROI) that potentially contain 1D barcodes (MSI format)
 * using gradient analysis and morphological operations.
 * 
 * Based on OpenCV/02-Detection-ROI-1D.md specification
 */
class OpenCVMSIDetector(private val visualDebugger: VisualDebugger? = null) {
    
    companion object {
        private const val TAG = "OpenCVMSIDetector"
        
        // Gradient analysis parameters (strict OpenCV specifications)
        private const val MIN_GRADIENT_RATIO = 3.0          // X/Y gradient ratio for horizontal codes
        private const val GRADIENT_THRESHOLD = 30.0         // Minimum gradient magnitude
        private const val MIN_ASPECT_RATIO = 2.5            // Width/Height ratio for 1D barcodes (OpenCV spec)
        private const val MAX_ASPECT_RATIO = 15.0           // Maximum reasonable aspect ratio
        
        // Morphological operations parameters
        private const val MORPH_KERNEL_WIDTH = 21           // Horizontal kernel width (connect bars)
        private const val MORPH_KERNEL_HEIGHT = 7           // Vertical kernel height (preserve height)
        
        // ROI filtering parameters (adjusted for high resolution ~1080×1920)
        private const val MIN_ROI_WIDTH = 100               // Minimum barcode width (high-res optimized)
        private const val MIN_ROI_HEIGHT = 30               // Minimum barcode height (high-res optimized)  
        private const val MIN_AREA = 3000                   // Minimum ROI area (high-res optimized)
        private const val MAX_ROI_COUNT = 3                 // Maximum ROI candidates (avoid overload)
        
        // Validation parameters (OpenCV quality thresholds)
        private const val MIN_DENSITY_RATIO = 0.3           // 30% minimum contour density
        private const val MIN_CONVEXITY = 0.7               // 70% minimum convexity
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.6   // High confidence threshold (lowered for more detections)
        
        // Confidence scoring weights (OpenCV documented formula)
        private const val WEIGHT_ASPECT_RATIO = 0.4f        // Aspect ratio importance
        private const val WEIGHT_GRADIENT_DENSITY = 0.3f    // Gradient density importance  
        private const val WEIGHT_COMPACTNESS = 0.2f         // Contour regularity importance
        private const val WEIGHT_POSITION = 0.1f            // Position in image importance
    }
    
    // Reusable Mat objects for performance (avoid allocation per frame)
    private val gradX = Mat()
    private val gradY = Mat()
    private val magnitude = Mat()
    private val direction = Mat()
    private val morphKernel = Imgproc.getStructuringElement(
        Imgproc.MORPH_RECT, 
        Size(MORPH_KERNEL_WIDTH.toDouble(), MORPH_KERNEL_HEIGHT.toDouble())
    )
    private val hierarchy = Mat()
    
    /**
     * Detect ROI candidates containing potential 1D barcodes
     * 
     * @param grayMat Input grayscale image (CV_8UC1)
     * @param originalImageProxy Original ImageProxy for debug visualization (optional)
     * @return List of ROI candidates sorted by confidence (descending)
     */
    fun detectROICandidates(grayMat: Mat, originalImageProxy: androidx.camera.core.ImageProxy? = null): List<ROICandidate> {
        val startTime = System.currentTimeMillis()
        val debugSession = visualDebugger?.createDebugSession()
        
        try {
            Log.v(TAG, "Detecting ROI candidates in ${grayMat.cols()}x${grayMat.rows()} image")
            
            // Debug: Save original grayscale image
            visualDebugger?.saveGrayscaleImage(grayMat, "${debugSession}_01_original")
            
            // Step 1: Gradient Analysis (Sobel X/Y)
            val gradientRegions = analyzeGradients(grayMat)
            if (gradientRegions.empty()) {
                Log.v(TAG, "No gradient regions found")
                return emptyList()
            }
            
            // Debug: Save gradient analysis result
            visualDebugger?.saveGrayscaleImage(gradientRegions, "${debugSession}_02_gradients")
            
            // Step 2: Morphological Operations
            val morphProcessed = applyMorphology(gradientRegions)
            
            // Debug: Save morphological processing result
            visualDebugger?.saveGrayscaleImage(morphProcessed, "${debugSession}_03_morphology")
            
            // Step 3: Contour Detection
            val contours = detectContours(morphProcessed)
            Log.v(TAG, "Found ${contours.size} contours")
            
            // Step 4: Geometric Filtering & ROI Extraction
            val roiCandidates = extractAndFilterROIs(contours, grayMat)
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Detected ${roiCandidates.size} ROI candidates in ${processingTime}ms")
            
            // Debug: Save original frame with ROI overlays if ImageProxy provided
            if (originalImageProxy != null && roiCandidates.isNotEmpty()) {
                visualDebugger?.saveFrameWithROIs(
                    originalImageProxy, 
                    roiCandidates, 
                    "${debugSession}_04_frame_with_rois"
                )
                
                // Debug: Save individual ROI crops
                roiCandidates.forEachIndexed { index, roi ->
                    visualDebugger?.saveROICrop(grayMat, roi, index, "${debugSession}_05_roi")
                }
            }
            
            // Apply OpenCV high confidence filtering (>=0.8 for high confidence)
            val highConfidenceROIs = roiCandidates.filter { it.confidence >= HIGH_CONFIDENCE_THRESHOLD }
            
            Log.d(TAG, "High confidence ROIs: ${highConfidenceROIs.size}/${roiCandidates.size} (threshold: $HIGH_CONFIDENCE_THRESHOLD)")
            
            // If no high confidence, accept medium confidence (>=0.5) but log warning
            val finalROIs = if (highConfidenceROIs.isNotEmpty()) {
                highConfidenceROIs
            } else {
                val mediumConfidenceROIs = roiCandidates.filter { it.confidence >= 0.5f }
                if (mediumConfidenceROIs.isNotEmpty()) {
                    Log.w(TAG, "No high confidence ROIs, using medium confidence (>=0.5)")
                    mediumConfidenceROIs
                } else {
                    emptyList()
                }
            }
            
            return finalROIs.take(MAX_ROI_COUNT) // Limit candidates for performance
            
        } catch (exception: Exception) {
            Log.e(TAG, "ROI detection failed: ${exception.message}", exception)
            return emptyList()
        }
    }
    
    /**
     * Step 1: Analyze gradients to find barcode-like patterns
     */
    private fun analyzeGradients(grayMat: Mat): Mat {
        // Calculate Sobel gradients in X and Y directions
        Imgproc.Sobel(grayMat, gradX, CvType.CV_32F, 1, 0, 3)
        Imgproc.Sobel(grayMat, gradY, CvType.CV_32F, 0, 1, 3)
        
        // Calculate gradient magnitude and direction
        Core.cartToPolar(gradX, gradY, magnitude, direction)
        
        // Create binary mask for strong horizontal gradients
        val gradientMask = Mat()
        val ratioMask = Mat()
        val threshMask = Mat()
        
        try {
            // Calculate gradient ratio (|gradX| / |gradY|) for horizontal pattern detection
            val absGradX = Mat()
            val absGradY = Mat()
            Core.absdiff(gradX, Scalar.all(0.0), absGradX)
            Core.absdiff(gradY, Scalar.all(0.0), absGradY)
            
            // Avoid division by zero
            Core.add(absGradY, Scalar.all(1.0), absGradY)
            Core.divide(absGradX, absGradY, ratioMask)
            
            // Apply thresholds: strong magnitude AND high X/Y ratio
            Imgproc.threshold(magnitude, threshMask, GRADIENT_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.threshold(ratioMask, ratioMask, MIN_GRADIENT_RATIO, 255.0, Imgproc.THRESH_BINARY)
            
            // Combine masks
            Core.bitwise_and(threshMask, ratioMask, gradientMask)
            
            // Convert to 8-bit for morphological operations
            gradientMask.convertTo(gradientMask, CvType.CV_8UC1)
            
            // Cleanup temp matrices
            absGradX.release()
            absGradY.release()
            ratioMask.release()
            threshMask.release()
            
            return gradientMask
            
        } catch (exception: Exception) {
            Log.e(TAG, "Gradient analysis failed: ${exception.message}", exception)
            gradientMask.release()
            ratioMask.release()
            threshMask.release()
            return Mat() // Return empty Mat on error
        }
    }
    
    /**
     * Step 2: Apply morphological operations to connect barcode bars
     */
    private fun applyMorphology(gradientMask: Mat): Mat {
        val morphResult = Mat()
        
        // Closing operation: connects nearby bars horizontally while preserving height
        Imgproc.morphologyEx(gradientMask, morphResult, Imgproc.MORPH_CLOSE, morphKernel)
        
        // Optional: Additional opening to remove noise
        Imgproc.morphologyEx(morphResult, morphResult, Imgproc.MORPH_OPEN, morphKernel)
        
        return morphResult
    }
    
    /**
     * Step 3: Detect contours in the processed image
     */
    private fun detectContours(morphProcessed: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()
        
        // Find external contours only (RETR_EXTERNAL)
        Imgproc.findContours(
            morphProcessed, 
            contours, 
            hierarchy, 
            Imgproc.RETR_EXTERNAL, 
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        return contours
    }
    
    /**
     * Step 4: Extract ROI candidates and apply geometric filtering
     */
    private fun extractAndFilterROIs(contours: List<MatOfPoint>, originalMat: Mat): List<ROICandidate> {
        val roiCandidates = mutableListOf<ROICandidate>()
        
        for (contour in contours) {
            try {
                // Get bounding rectangle
                val boundingRect = Imgproc.boundingRect(contour)
                
                // Apply geometric filters
                val aspectRatio = boundingRect.width.toFloat() / boundingRect.height.toFloat()
                
                if (!isValidBarcodeGeometry(boundingRect, aspectRatio, contour, originalMat)) {
                    continue
                }
                
                // Calculate confidence score using OpenCV documented formula
                val confidence = calculateOpenCVConfidence(boundingRect, aspectRatio, contour, originalMat)
                
                // Create ROI candidate
                val roi = ROICandidate(
                    boundingRect = Rect(boundingRect.x, boundingRect.y, 
                                      boundingRect.x + boundingRect.width, 
                                      boundingRect.y + boundingRect.height),
                    confidence = confidence,
                    aspectRatio = aspectRatio,
                    gradientMagnitude = calculateAverageGradient(boundingRect),
                    rotationAngle = 0 // Horizontal orientation for now
                )
                
                roiCandidates.add(roi)
                
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to process contour: ${exception.message}")
                continue
            }
        }
        
        // Sort by confidence (descending) and return
        return roiCandidates.sortedByDescending { it.confidence }
    }
    
    /**
     * Validate barcode geometry constraints
     */
    /**
     * Strict validation according to OpenCV barcode detection specifications
     * Implements all documented validation criteria from 02-Detection-ROI-1D.md
     */
    private fun isValidBarcodeGeometry(
        rect: org.opencv.core.Rect, 
        aspectRatio: Float, 
        contour: MatOfPoint,
        originalMat: Mat
    ): Boolean {
        try {
            // Validation 1: Basic size constraints
            if (rect.width < MIN_ROI_WIDTH || rect.height < MIN_ROI_HEIGHT) {
                Log.v(TAG, "REJECT size: ${rect.width}x${rect.height} < ${MIN_ROI_WIDTH}x${MIN_ROI_HEIGHT}")
                return false
            }
            
            // Validation 2: Aspect ratio for 1D barcodes
            if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
                Log.v(TAG, "REJECT aspect ratio: $aspectRatio not in [$MIN_ASPECT_RATIO, $MAX_ASPECT_RATIO]")
                return false
            }
            
            // Validation 3: Minimum area (OpenCV spec)
            val area = rect.width * rect.height
            if (area < MIN_AREA) {
                Log.v(TAG, "REJECT area: $area < $MIN_AREA")
                return false
            }
            
            // Validation 4: Contour density (30% minimum - OpenCV spec)
            val contourArea = Imgproc.contourArea(contour)
            val rectArea = rect.width * rect.height
            val densityRatio = contourArea / rectArea
            if (densityRatio < MIN_DENSITY_RATIO) {
                Log.v(TAG, "REJECT density: ${String.format("%.2f", densityRatio)} < $MIN_DENSITY_RATIO")
                return false
            }
            
            // Validation 5: Convexity (70% minimum - OpenCV spec)
            val hull = MatOfInt()
            Imgproc.convexHull(contour, hull)
            val hullPoints = hull.toArray().map { contour.toArray()[it] }.toTypedArray()
            val hullArea = Imgproc.contourArea(MatOfPoint(*hullPoints))
            val convexity = contourArea / hullArea
            hull.release()
            
            if (convexity < MIN_CONVEXITY) {
                Log.v(TAG, "REJECT convexity: ${String.format("%.2f", convexity)} < $MIN_CONVEXITY")
                return false
            }
            
            // Validation 6: Gradient intensity in ROI (must have significant transitions)
            val roi = Mat(magnitude, rect)
            val meanGradient = Core.mean(roi).`val`[0]
            roi.release()
            
            if (meanGradient < GRADIENT_THRESHOLD) {
                Log.v(TAG, "REJECT gradient: ${String.format("%.1f", meanGradient)} < $GRADIENT_THRESHOLD")
                return false
            }
            
            Log.v(TAG, "ACCEPT ROI: ${rect.width}x${rect.height}, ratio=${String.format("%.1f", aspectRatio)}, area=$area, density=${String.format("%.2f", densityRatio)}, convexity=${String.format("%.2f", convexity)}, grad=${String.format("%.1f", meanGradient)}")
            return true
            
        } catch (exception: Exception) {
            Log.w(TAG, "Error in barcode geometry validation", exception)
            return false
        }
    }
    
    /**
     * Calculate confidence score using OpenCV documented formula
     * Based on 4-factor weighted formula from 02-Detection-ROI-1D.md
     */
    private fun calculateOpenCVConfidence(
        rect: org.opencv.core.Rect, 
        aspectRatio: Float, 
        contour: MatOfPoint,
        originalMat: Mat
    ): Float {
        try {
            // Factor 1: Aspect ratio score (closer to barcode ratios = better)
            val aspectScore = when {
                aspectRatio >= 8.0f -> 1.0f      // Excellent (long codes)
                aspectRatio >= 5.0f -> 0.8f      // Good
                aspectRatio >= 3.0f -> 0.6f      // Acceptable  
                else -> 0.3f                     // Low
            }
            
            // Factor 2: Gradient density in ROI
            val roi = Mat(magnitude, rect)
            val nonZeroCount = Core.countNonZero(roi)
            val totalPixels = rect.width * rect.height
            val densityScore = (nonZeroCount.toFloat() / totalPixels).coerceIn(0.0f, 1.0f)
            roi.release()
            
            // Factor 3: Contour regularity (compactness)
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val area = Imgproc.contourArea(contour)
            val compactness = (4 * Math.PI * area) / (perimeter * perimeter)
            val compactnessScore = compactness.toFloat().coerceIn(0.0f, 1.0f)
            
            // Factor 4: Position in image (center often better)
            val imageCenter = Point(originalMat.width() / 2.0, originalMat.height() / 2.0)
            val rectCenter = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)
            val distance = Math.sqrt(
                Math.pow(rectCenter.x - imageCenter.x, 2.0) + 
                Math.pow(rectCenter.y - imageCenter.y, 2.0)
            )
            val maxDistance = Math.sqrt(
                Math.pow(originalMat.width() / 2.0, 2.0) + 
                Math.pow(originalMat.height() / 2.0, 2.0)
            )
            val positionScore = (1.0 - (distance / maxDistance)).toFloat()
            
            // OpenCV documented weighted combination
            val confidence = (aspectScore * WEIGHT_ASPECT_RATIO) + 
                           (densityScore * WEIGHT_GRADIENT_DENSITY) + 
                           (compactnessScore * WEIGHT_COMPACTNESS) + 
                           (positionScore * WEIGHT_POSITION)
            
            val finalConfidence = confidence.coerceIn(0.0f, 1.0f)
            
            Log.v(TAG, "CONFIDENCE: ${String.format("%.2f", finalConfidence)} = aspect(${String.format("%.2f", aspectScore)}) + density(${String.format("%.2f", densityScore)}) + compact(${String.format("%.2f", compactnessScore)}) + pos(${String.format("%.2f", positionScore)})")
                           
            return finalConfidence
            
        } catch (exception: Exception) {
            Log.w(TAG, "Error calculating OpenCV confidence", exception)
            return 0.5f  // Neutral value on error
        }
    }
    
    /**
     * Calculate average gradient magnitude in the ROI
     */
    private fun calculateAverageGradient(rect: org.opencv.core.Rect): Double {
        return try {
            // Extract ROI from magnitude matrix
            val roi = Mat(magnitude, rect)
            val mean = Core.mean(roi)
            roi.release()
            mean.`val`[0]
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to calculate gradient for ROI: ${exception.message}")
            0.0
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        gradX.release()
        gradY.release()
        magnitude.release()
        direction.release()
        morphKernel.release()
        hierarchy.release()
        Log.d(TAG, "OpenCVMSIDetector resources released")
    }
}