package com.msidecoder.scanner.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object YuvToNv21Converter {
    
    fun convert(imageProxy: ImageProxy): ByteArray {
        val planes = imageProxy.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        
        val width = imageProxy.width
        val height = imageProxy.height
        
        // Calculate actual NV21 size (without padding)
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)
        
        // Copy Y plane row by row to handle stride correctly
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        
        if (yPixelStride == 1 && yRowStride == width) {
            // Simple case: no padding, direct copy
            yBuffer.get(nv21, 0, ySize)
        } else {
            // Complex case: handle stride and padding row by row
            var outputPos = 0
            val rowData = ByteArray(width)
            
            for (row in 0 until height) {
                val rowStart = row * yRowStride
                if (rowStart < yBuffer.limit()) {
                    yBuffer.position(rowStart)
                    val bytesToRead = minOf(width, yBuffer.remaining())
                    yBuffer.get(rowData, 0, bytesToRead)
                    System.arraycopy(rowData, 0, nv21, outputPos, bytesToRead)
                }
                outputPos += width
            }
        }
        
        // Handle UV planes - simplified approach
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uvPixelStride = uPlane.pixelStride
        
        var uvOutputPos = ySize
        
        try {
            if (uvPixelStride == 2) {
                // Interleaved UV case - most common
                // Check if U plane contains interleaved UV data
                val uvInterleavedSize = minOf(uBuffer.remaining(), uvSize)
                val uvData = ByteArray(uvInterleavedSize)
                uBuffer.get(uvData)
                
                // Convert UV interleaved to VU interleaved (NV21 format)
                var i = 0
                while (i < uvInterleavedSize - 1 && uvOutputPos < nv21.size - 1) {
                    nv21[uvOutputPos++] = uvData[i + 1] // V
                    nv21[uvOutputPos++] = uvData[i]     // U
                    i += 2
                }
            } else {
                // Separate U and V planes
                val uvHeight = height / 2
                val uvWidth = width / 2
                val uData = ByteArray(minOf(uBuffer.remaining(), uvWidth * uvHeight))
                val vData = ByteArray(minOf(vBuffer.remaining(), uvWidth * uvHeight))
                
                uBuffer.get(uData)
                vBuffer.get(vData)
                
                // Interleave V and U for NV21
                for (i in 0 until minOf(uData.size, vData.size)) {
                    if (uvOutputPos < nv21.size - 1) {
                        nv21[uvOutputPos++] = vData[i] // V first
                        nv21[uvOutputPos++] = uData[i] // U second
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: fill remaining with neutral gray values
            while (uvOutputPos < nv21.size) {
                nv21[uvOutputPos++] = 128.toByte() // Neutral chroma
            }
        }

        return nv21
    }
    
    fun convertOptimized(imageProxy: ImageProxy): ByteArray {
        val planes = imageProxy.planes
        val yPlane = planes[0]
        val uPlane = planes[1] 
        val vPlane = planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // Handle UV planes based on pixel stride
        val pixelStride = uPlane.pixelStride
        val rowStride = uPlane.rowStride
        val rowPadding = rowStride - imageProxy.width / 2
        
        var nv21Index = ySize
        var uIndex = 0
        var vIndex = 0
        
        val uArray = ByteArray(uSize)
        val vArray = ByteArray(vSize)
        uBuffer.get(uArray)
        vBuffer.get(vArray)
        
        if (pixelStride == 1 && rowPadding == 0) {
            // Simple case: tightly packed, just interleave V and U
            for (i in 0 until uSize) {
                nv21[nv21Index++] = vArray[i] // V first for NV21
                nv21[nv21Index++] = uArray[i] // U second
            }
        } else {
            // Complex case: handle stride and padding
            val height = imageProxy.height / 2
            val width = imageProxy.width / 2
            
            for (row in 0 until height) {
                for (col in 0 until width) {
                    nv21[nv21Index++] = vArray[vIndex] // V
                    nv21[nv21Index++] = uArray[uIndex] // U
                    uIndex += pixelStride
                    vIndex += pixelStride
                }
                uIndex += rowPadding
                vIndex += rowPadding
            }
        }
        
        return nv21
    }
}