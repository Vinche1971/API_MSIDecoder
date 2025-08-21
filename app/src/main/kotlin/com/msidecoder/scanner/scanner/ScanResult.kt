package com.msidecoder.scanner.scanner

/**
 * Sealed class representing scan results from different scanners
 */
sealed class ScanResult {
    data class Success(
        val data: String,
        val format: String,
        val source: ScanSource,
        val processingTimeMs: Long,
        val boundingBox: android.graphics.Rect? = null,  // T-008: Coordinates for ROI overlay
        val cornerPoints: Array<android.graphics.Point>? = null  // T-008: Corner points if available
    ) : ScanResult()
    
    object NoResult : ScanResult()
    
    data class Error(
        val exception: Exception,
        val source: ScanSource
    ) : ScanResult()
}

/**
 * Source of the scan result
 */
enum class ScanSource {
    ML_KIT,
    MSI
}

/**
 * Barcode format constants
 */
object BarcodeFormat {
    const val DATA_MATRIX = "DATA_MATRIX"
    const val EAN_13 = "EAN_13"
    const val EAN_8 = "EAN_8"
    const val CODE_128 = "CODE_128"
    const val QR_CODE = "QR_CODE"
    const val MSI = "MSI"
}