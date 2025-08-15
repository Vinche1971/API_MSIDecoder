package com.msidecoder.scanner.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object YuvToNv21Converter {
    
    fun convert(imageProxy: ImageProxy): ByteArray {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y plane - copy directly
        yBuffer.get(nv21, 0, ySize)

        // UV interleaved for NV21 format
        val uvPixelStride = imageProxy.planes[1].pixelStride
        if (uvPixelStride == 1) {
            // Packed UV
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)
        } else {
            // Interleaved UV - need to repack to VU order for NV21
            val uvBuffer = ByteArray(uSize + vSize)
            uBuffer.get(uvBuffer, 0, uSize)
            vBuffer.get(uvBuffer, uSize, vSize)
            
            var uvPos = ySize
            var i = 0
            while (i < uvBuffer.size - 1) {
                nv21[uvPos++] = uvBuffer[i + 1] // V
                nv21[uvPos++] = uvBuffer[i]     // U
                i += 2
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