package com.msidecoder.scanner.msi

import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * MSI ROI Detection using anisotropic gradient energy
 * 
 * Detects candidate regions containing MSI barcodes through:
 * - Intensity normalization [0..1]
 * - Local contrast enhancement  
 * - Horizontal gradient (Sobel X) for vertical bars detection
 * - Morphological closing 1×k to group bars
 * - Bounding box detection with MSI-specific criteria
 */
class MsiRoiDetector {
    
    companion object {
        private const val TAG = "MsiRoiDetector"
        
        // ROI Detection Parameters
        private const val MIN_ROI_WIDTH = 60      // Minimum ROI width in pixels
        private const val MIN_ROI_HEIGHT = 20     // Minimum ROI height in pixels
        private const val MIN_ASPECT_RATIO = 3.0f // Width/Height >= 3 for MSI
        private const val MAX_ROI_CANDIDATES = 3   // Return top 3 candidates max
        
        // Gradient Parameters
        private const val GRADIENT_THRESHOLD = 0.3f // Minimum gradient magnitude
        private const val MORPHO_KERNEL_SIZE = 15    // Morphological closing kernel (1×k)
        
        // Quiet Zone Parameters
        private const val QUIET_ZONE_RATIO = 0.1f   // Quiet zones should be ~10% of ROI width
    }
    
    /**
     * Detect ROI candidates in NV21 frame
     * 
     * @param nv21Data Frame data in NV21 format (luminance Y in first width*height bytes)
     * @param width Frame width
     * @param height Frame height
     * @param rotationDegrees Frame rotation (0, 90, 180, 270)
     * @return List of ROI candidates sorted by score (best first)
     */
    fun detectROI(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): List<RoiCandidate> {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Starting ROI detection: ${width}x${height}, rotation=$rotationDegrees")
            
            // Step 1: Extract and normalize luminance
            val normalizedIntensities = normalizeIntensities(nv21Data, width, height)
            
            // Step 2: Enhance local contrast (placeholder for now)
            val enhancedIntensities = enhanceContrast(normalizedIntensities, width, height)
            
            // Step 3: Compute horizontal gradient (Sobel X)
            val gradientMap = computeHorizontalGradient(enhancedIntensities, width, height)
            
            // Step 4: Morphological closing to group bars
            val closedGradient = morphologicalClosing(gradientMap, width, height, MORPHO_KERNEL_SIZE)
            
            // Step 5: Detect bounding boxes
            val candidates = detectBoundingBoxes(closedGradient, width, height)
            
            // Step 6: Filter and score candidates
            val filteredCandidates = filterAndScoreCandidates(candidates, gradientMap, width, height)
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "ROI detection completed in ${processingTime}ms, found ${filteredCandidates.size} candidates")
            
            return filteredCandidates.take(MAX_ROI_CANDIDATES)
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "ROI detection failed in ${processingTime}ms", exception)
            return emptyList()
        }
    }
    
    /**
     * Normalize pixel intensities from [0..255] to [0..1]
     */
    private fun normalizeIntensities(nv21Data: ByteArray, width: Int, height: Int): FloatArray {
        val normalized = FloatArray(width * height)
        
        for (i in 0 until width * height) {
            // NV21 format: Y (luminance) is in first width*height bytes
            // Convert unsigned byte [0..255] to float [0..1]
            normalized[i] = (nv21Data[i].toInt() and 0xFF) / 255.0f
        }
        
        return normalized
    }
    
    /**
     * Enhance local contrast (placeholder implementation)
     * TODO: Implement CLAHE light or local normalization
     */
    private fun enhanceContrast(intensities: FloatArray, @Suppress("UNUSED_PARAMETER") width: Int, @Suppress("UNUSED_PARAMETER") height: Int): FloatArray {
        // For now, just return the input (no enhancement)
        // Future: implement CLAHE or local histogram equalization
        return intensities.copyOf()
    }
    
    /**
     * Compute horizontal gradient using Sobel X kernel
     * Sobel X: [[-1, 0, 1], [-2, 0, 2], [-1, 0, 1]]
     */
    private fun computeHorizontalGradient(intensities: FloatArray, width: Int, height: Int): FloatArray {
        val gradient = FloatArray(width * height)
        
        // Sobel X kernel weights
        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        
        // Apply Sobel X convolution (skip borders)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0.0f
                
                // Convolve with 3x3 Sobel X kernel
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelIndex = (y + ky) * width + (x + kx)
                        gx += intensities[pixelIndex] * sobelX[ky + 1][kx + 1]
                    }
                }
                
                // Store gradient magnitude (absolute value)
                gradient[y * width + x] = abs(gx)
            }
        }
        
        return gradient
    }
    
    /**
     * Morphological closing with horizontal kernel 1×k
     * Dilate then erode to close gaps between bars
     */
    private fun morphologicalClosing(gradient: FloatArray, width: Int, height: Int, kernelSize: Int): FloatArray {
        // Dilate horizontally
        val dilated = dilateHorizontal(gradient, width, height, kernelSize)
        
        // Erode horizontally  
        val closed = erodeHorizontal(dilated, width, height, kernelSize)
        
        return closed
    }
    
    /**
     * Horizontal dilation with 1×k kernel
     */
    private fun dilateHorizontal(image: FloatArray, width: Int, height: Int, kernelSize: Int): FloatArray {
        val result = FloatArray(width * height)
        val halfKernel = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxVal = 0.0f
                
                // Find maximum in horizontal neighborhood
                for (kx in -halfKernel..halfKernel) {
                    val neighborX = max(0, min(width - 1, x + kx))
                    maxVal = max(maxVal, image[y * width + neighborX])
                }
                
                result[y * width + x] = maxVal
            }
        }
        
        return result
    }
    
    /**
     * Horizontal erosion with 1×k kernel
     */
    private fun erodeHorizontal(image: FloatArray, width: Int, height: Int, kernelSize: Int): FloatArray {
        val result = FloatArray(width * height)
        val halfKernel = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minVal = Float.MAX_VALUE
                
                // Find minimum in horizontal neighborhood
                for (kx in -halfKernel..halfKernel) {
                    val neighborX = max(0, min(width - 1, x + kx))
                    minVal = min(minVal, image[y * width + neighborX])
                }
                
                result[y * width + x] = minVal
            }
        }
        
        return result
    }
    
    /**
     * Detect potential bounding boxes from gradient map
     */
    private fun detectBoundingBoxes(gradient: FloatArray, width: Int, height: Int): List<BoundingBox> {
        val boxes = mutableListOf<BoundingBox>()
        val visited = BooleanArray(width * height)
        
        // Threshold gradient to find strong edges
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                
                if (!visited[index] && gradient[index] > GRADIENT_THRESHOLD) {
                    // Flood fill to find connected component
                    val boundingBox = floodFillBoundingBox(gradient, visited, x, y, width, height)
                    
                    if (boundingBox != null) {
                        boxes.add(boundingBox)
                    }
                }
            }
        }
        
        return boxes
    }
    
    /**
     * Flood fill to find bounding box of connected high-gradient region
     */
    private fun floodFillBoundingBox(
        gradient: FloatArray,
        visited: BooleanArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int
    ): BoundingBox? {
        val stack = mutableListOf<Pair<Int, Int>>()
        stack.add(Pair(startX, startY))
        
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        var pixelCount = 0
        
        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeAt(stack.size - 1)
            val index = y * width + x
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[index] || gradient[index] <= GRADIENT_THRESHOLD) {
                continue
            }
            
            visited[index] = true
            pixelCount++
            
            // Update bounding box
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
            
            // Add neighbors to stack
            stack.add(Pair(x + 1, y))
            stack.add(Pair(x - 1, y))
            stack.add(Pair(x, y + 1))
            stack.add(Pair(x, y - 1))
        }
        
        val boxWidth = maxX - minX + 1
        val boxHeight = maxY - minY + 1
        
        // Filter by minimum size
        if (boxWidth >= MIN_ROI_WIDTH && boxHeight >= MIN_ROI_HEIGHT) {
            return BoundingBox(minX, minY, boxWidth, boxHeight, pixelCount)
        }
        
        return null
    }
    
    /**
     * Filter and score candidates based on MSI criteria
     */
    private fun filterAndScoreCandidates(
        boxes: List<BoundingBox>,
        gradientMap: FloatArray,
        width: Int,
        height: Int
    ): List<RoiCandidate> {
        val candidates = mutableListOf<RoiCandidate>()
        
        for (box in boxes) {
            val aspectRatio = box.width.toFloat() / box.height.toFloat()
            
            // Filter by aspect ratio (MSI codes are wide)
            if (aspectRatio >= MIN_ASPECT_RATIO) {
                // Calculate gradient variance within the box
                val gradientVariance = calculateGradientVariance(gradientMap, box, width)
                
                // Check for quiet zones (simplified)
                val hasQuietZones = checkQuietZones(gradientMap, box, width, height)
                
                // Calculate score (simple formula for now)
                val score = (aspectRatio / 10.0f) + gradientVariance + (if (hasQuietZones) 0.3f else 0.0f)
                
                candidates.add(
                    RoiCandidate(
                        x = box.x,
                        y = box.y,
                        width = box.width,
                        height = box.height,
                        score = score,
                        gradientVariance = gradientVariance
                    )
                )
            }
        }
        
        // Sort by score (best first)
        return candidates.sortedByDescending { it.score }
    }
    
    /**
     * Calculate gradient variance within bounding box
     */
    private fun calculateGradientVariance(gradientMap: FloatArray, box: BoundingBox, width: Int): Float {
        var sum = 0.0f
        var sumSquares = 0.0f
        var count = 0
        
        for (y in box.y until box.y + box.height) {
            for (x in box.x until box.x + box.width) {
                val gradient = gradientMap[y * width + x]
                sum += gradient
                sumSquares += gradient * gradient
                count++
            }
        }
        
        if (count == 0) return 0.0f
        
        val mean = sum / count
        val variance = (sumSquares / count) - (mean * mean)
        return variance
    }
    
    /**
     * Check for quiet zones (simplified implementation)
     */
    private fun checkQuietZones(gradientMap: FloatArray, box: BoundingBox, width: Int, @Suppress("UNUSED_PARAMETER") height: Int): Boolean {
        val quietWidth = (box.width * QUIET_ZONE_RATIO).toInt()
        
        // Check left quiet zone
        val leftQuietZone = checkQuietZoneRegion(
            gradientMap, 
            max(0, box.x - quietWidth), 
            box.y, 
            quietWidth, 
            box.height, 
            width
        )
        
        // Check right quiet zone  
        val rightQuietZone = checkQuietZoneRegion(
            gradientMap,
            min(width - quietWidth, box.x + box.width),
            box.y,
            quietWidth,
            box.height,
            width
        )
        
        return leftQuietZone && rightQuietZone
    }
    
    /**
     * Check if a region has low gradient (quiet zone)
     */
    private fun checkQuietZoneRegion(
        gradientMap: FloatArray,
        x: Int,
        y: Int,
        regionWidth: Int,
        regionHeight: Int,
        frameWidth: Int
    ): Boolean {
        var gradientSum = 0.0f
        var count = 0
        
        for (dy in 0 until regionHeight) {
            for (dx in 0 until regionWidth) {
                val px = x + dx
                val py = y + dy
                
                if (px >= 0 && px < frameWidth && py >= 0) {
                    gradientSum += gradientMap[py * frameWidth + px]
                    count++
                }
            }
        }
        
        if (count == 0) return false
        
        val avgGradient = gradientSum / count
        return avgGradient < GRADIENT_THRESHOLD * 0.5f // Quiet zones should have low gradient
    }
    
    /**
     * Internal data class for bounding box detection
     */
    private data class BoundingBox(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val pixelCount: Int
    )
}

/**
 * ROI Candidate result
 */
data class RoiCandidate(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val score: Float,
    val gradientVariance: Float
)