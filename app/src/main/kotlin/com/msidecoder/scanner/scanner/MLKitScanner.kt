package com.msidecoder.scanner.scanner

import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * ML Kit barcode scanner with whitelist format support
 */
class MLKitScanner {
    
    companion object {
        private const val TAG = "MLKitScanner"
    }
    
    private val scanner: BarcodeScanner
    
    init {
        // Configure ML Kit with whitelist formats only
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE
            )
            .build()
            
        scanner = BarcodeScanning.getClient(options)
        Log.d(TAG, "MLKitScanner initialized with whitelist formats")
    }
    
    /**
     * Scan NV21 frame data for barcodes
     * @param nv21Data Frame data in NV21 format
     * @param width Frame width
     * @param height Frame height  
     * @param rotationDegrees Frame rotation (0, 90, 180, 270)
     * @param callback Result callback
     */
    fun scanFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val inputImage = InputImage.fromByteArray(
                nv21Data,
                width,
                height,
                rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21
            )
            
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val processingTime = System.currentTimeMillis() - startTime
                    
                    if (barcodes.isNotEmpty()) {
                        // Take first detected barcode
                        val barcode = barcodes.first()
                        val mappedFormat = mapBarcodeFormat(barcode.format)
                        
                        // Check if format is in whitelist (supported formats only)
                        if (mappedFormat.startsWith("UNKNOWN_")) {
                            Log.d(TAG, "ML Kit detected unsupported format: $mappedFormat -> fallback to MSI")
                            callback(ScanResult.NoResult)
                            return@addOnSuccessListener
                        }
                        
                        val result = ScanResult.Success(
                            data = barcode.rawValue ?: "",
                            format = mappedFormat,
                            source = ScanSource.ML_KIT,
                            processingTimeMs = processingTime,
                            boundingBox = barcode.boundingBox,  // T-008: MLKit coordinates
                            cornerPoints = barcode.cornerPoints  // T-008: Corner points
                        )
                        
                        Log.d(TAG, "ML Kit detected: ${result.format} in ${processingTime}ms")
                        callback(result)
                    } else {
                        Log.d(TAG, "ML Kit: no barcodes detected in ${processingTime}ms")
                        callback(ScanResult.NoResult)
                    }
                }
                .addOnFailureListener { exception ->
                    val processingTime = System.currentTimeMillis() - startTime
                    Log.e(TAG, "ML Kit scan failed in ${processingTime}ms", exception)
                    callback(ScanResult.Error(exception, ScanSource.ML_KIT))
                }
                
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "ML Kit InputImage creation failed", exception)
            callback(ScanResult.Error(exception, ScanSource.ML_KIT))
        }
    }
    
    /**
     * Map ML Kit barcode format to our format constants
     */
    private fun mapBarcodeFormat(mlkitFormat: Int): String {
        return when (mlkitFormat) {
            Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            else -> "UNKNOWN_${mlkitFormat}"
        }
    }
    
    /**
     * Clean up scanner resources
     */
    fun close() {
        scanner.close()
        Log.d(TAG, "MLKitScanner closed")
    }
}