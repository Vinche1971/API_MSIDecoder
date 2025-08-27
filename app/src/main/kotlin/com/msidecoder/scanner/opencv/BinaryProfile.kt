package com.msidecoder.scanner.opencv

/**
 * T-103: Binary profile representation for MSI barcode decoding
 * 
 * Represents a 1D barcode as a sequence of bars (black) and spaces (white)
 */
data class BinaryProfile(
    /**
     * Binary pattern as Boolean array: true = bar (black), false = space (white)
     */
    val pattern: BooleanArray,
    
    /**
     * Quality score of the binarization (0.0 to 1.0)
     */
    val quality: Float,
    
    /**
     * Width/height ratio of the processed ROI
     */
    val aspectRatio: Float,
    
    /**
     * Number of bar/space transitions detected
     */
    val transitionCount: Int,
    
    /**
     * Average bar width in pixels (for validation)
     */
    val averageBarWidth: Float
) {
    
    /**
     * Get ASCII representation of the binary pattern for logging
     */
    fun toASCII(): String {
        return pattern.map { if (it) '█' else '·' }.joinToString("")
    }
    
    /**
     * Get compact ASCII representation (grouped bars)
     */
    fun toCompactASCII(): String {
        if (pattern.isEmpty()) return ""
        
        val result = StringBuilder()
        var current = pattern[0]
        var count = 1
        
        for (i in 1 until pattern.size) {
            if (pattern[i] == current) {
                count++
            } else {
                result.append(if (current) "$count" else "·$count")
                current = pattern[i]
                count = 1
            }
        }
        result.append(if (current) "$count" else "·$count")
        
        return result.toString()
    }
    
    /**
     * Get detailed analysis string for debug logs
     */
    fun toDebugString(): String {
        return "BinaryProfile(len=${pattern.size}, quality=${"%.2f".format(quality)}, " +
               "transitions=$transitionCount, avgBarWidth=${"%.1f".format(averageBarWidth)}, " +
               "ratio=${"%.2f".format(aspectRatio)})\n" +
               "ASCII: ${toASCII()}\n" +
               "Compact: ${toCompactASCII()}"
    }
    
    /**
     * Check if this binary profile is suitable for MSI decoding
     */
    fun isValidMSI(): Boolean {
        return quality > 0.5f &&
               transitionCount >= 8 &&
               pattern.size >= 20 &&
               aspectRatio > 2.0f
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as BinaryProfile
        
        return pattern.contentEquals(other.pattern) &&
               quality == other.quality &&
               aspectRatio == other.aspectRatio &&
               transitionCount == other.transitionCount &&
               averageBarWidth == other.averageBarWidth
    }
    
    override fun hashCode(): Int {
        var result = pattern.contentHashCode()
        result = 31 * result + quality.hashCode()
        result = 31 * result + aspectRatio.hashCode()
        result = 31 * result + transitionCount
        result = 31 * result + averageBarWidth.hashCode()
        return result
    }
}