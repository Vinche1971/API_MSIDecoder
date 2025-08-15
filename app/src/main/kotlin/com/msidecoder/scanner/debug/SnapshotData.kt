package com.msidecoder.scanner.debug

import org.json.JSONObject

/**
 * Data class representing a complete debug snapshot according to T-007 specifications
 */
data class SnapshotData(
    val ts: Long,                                    // timestamp (epoch ms)
    val res: String,                                 // "WIDTHxHEIGHT" (ex: "1280x720")
    val fps: Double,                                 // double (EMA)
    val procMs: Double,                              // double (latence pipeline)
    val queue: Int,                                  // int (frames in-flight)
    val rotationDeg: Int,                            // int (0/90/180/270)
    val torch: String,                               // "ON" | "OFF"
    val zoom: ZoomData,                              // { "ratio": float, "type": "optique|numerique" }
    val ml: MLKitData,                               // { "latMs": double|null, "hits": int }
    val msi: MSIData,                                // { "latMs": double|null, "status": "stub|ok|timeout|error" }
    val lastPub: LastPublicationData?                // { "text": string|null, "src": "MLKit|MSI|null", "ts": long }
) {
    
    data class ZoomData(
        val ratio: Float,
        val type: String                             // "optique" | "numerique"
    )
    
    data class MLKitData(
        val latMs: Double?,                          // null if no recent activity
        val hits: Int
    )
    
    data class MSIData(
        val latMs: Double?,                          // null if no recent activity
        val status: String                           // "stub" | "ok" | "timeout" | "error"
    )
    
    data class LastPublicationData(
        val text: String?,                           // null if no recent publication
        val src: String,                             // "MLKit" | "MSI" | "none"
        val ts: Long                                 // timestamp of last publication
    )
    
    /**
     * Convert this snapshot to JSON string
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("ts", ts)
            put("res", res)
            put("fps", fps)
            put("procMs", procMs)
            put("queue", queue)
            put("rotationDeg", rotationDeg)
            put("torch", torch)
            
            // Zoom data
            put("zoom", JSONObject().apply {
                put("ratio", zoom.ratio.toDouble())
                put("type", zoom.type)
            })
            
            // ML Kit data
            put("ml", JSONObject().apply {
                put("latMs", ml.latMs)
                put("hits", ml.hits)
            })
            
            // MSI data
            put("msi", JSONObject().apply {
                put("latMs", msi.latMs)
                put("status", msi.status)
            })
            
            // Last publication data
            lastPub?.let { lastPub ->
                put("lastPub", JSONObject().apply {
                    put("text", lastPub.text)
                    put("src", lastPub.src)
                    put("ts", lastPub.ts)
                })
            } ?: put("lastPub", JSONObject.NULL)
            
        }.toString()
    }
    
    /**
     * Convert this snapshot to pretty-printed JSON string
     */
    fun toPrettyJson(): String {
        return JSONObject().apply {
            put("ts", ts)
            put("res", res)
            put("fps", fps)
            put("procMs", procMs)
            put("queue", queue)
            put("rotationDeg", rotationDeg)
            put("torch", torch)
            
            // Zoom data
            put("zoom", JSONObject().apply {
                put("ratio", zoom.ratio.toDouble())
                put("type", zoom.type)
            })
            
            // ML Kit data
            put("ml", JSONObject().apply {
                put("latMs", ml.latMs)
                put("hits", ml.hits)
            })
            
            // MSI data
            put("msi", JSONObject().apply {
                put("latMs", msi.latMs)
                put("status", msi.status)
            })
            
            // Last publication data
            lastPub?.let { lastPub ->
                put("lastPub", JSONObject().apply {
                    put("text", lastPub.text)
                    put("src", lastPub.src)
                    put("ts", lastPub.ts)
                })
            } ?: put("lastPub", JSONObject.NULL)
            
        }.toString(2) // Pretty print with 2 spaces indent
    }
}