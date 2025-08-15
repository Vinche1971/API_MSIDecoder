package com.msidecoder.scanner.debug

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import com.msidecoder.scanner.scanner.ScannerArbitrator
import com.msidecoder.scanner.state.CameraControlsManager
import com.msidecoder.scanner.state.PreferencesRepository
import com.msidecoder.scanner.state.ZoomLevel
import com.msidecoder.scanner.utils.MetricsCollector
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for capturing and saving debug snapshots according to T-007 specifications
 */
class SnapshotManager(
    private val context: Context,
    private val metricsCollector: MetricsCollector,
    private val cameraControlsManager: CameraControlsManager,
    private val preferencesRepository: PreferencesRepository
) {
    
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private var lastSnapshotFile: File? = null
    
    /**
     * Capture a complete snapshot of current system state
     */
    fun captureSnapshot(scannerArbitrator: ScannerArbitrator?): SnapshotData {
        val timestamp = System.currentTimeMillis()
        val snapshot = metricsCollector.getSnapshot()
        val cameraState = cameraControlsManager.getCurrentState()
        val lastScanResult = preferencesRepository.getLastScanResult()
        
        // Get scanner metrics
        val scannerMetrics = scannerArbitrator?.getMetrics() ?: ScannerArbitrator.ScanMetrics(0, 0, 0, 0)
        
        return SnapshotData(
            ts = timestamp,
            res = snapshot.resolution,
            fps = snapshot.fps,
            procMs = snapshot.avgProcessingTimeMs,
            queue = snapshot.queueSize,
            rotationDeg = snapshot.rotation,
            torch = if (cameraState.torchEnabled) "ON" else "OFF",
            zoom = SnapshotData.ZoomData(
                ratio = cameraState.zoomLevel.ratio,
                type = getZoomType(cameraState.zoomLevel)
            ),
            ml = SnapshotData.MLKitData(
                latMs = if (scannerMetrics.mlkitTimeMs > 0) scannerMetrics.mlkitTimeMs.toDouble() else null,
                hits = scannerMetrics.mlkitHits
            ),
            msi = SnapshotData.MSIData(
                latMs = if (scannerMetrics.msiTimeMs > 0) scannerMetrics.msiTimeMs.toDouble() else null,
                status = "stub" // Phase 0: always stub
            ),
            lastPub = lastScanResult?.let { result ->
                SnapshotData.LastPublicationData(
                    text = result.data,
                    src = metricsCollector.lastScanSource.takeIf { it != "none" } ?: "none",
                    ts = result.timestamp
                )
            }
        )
    }
    
    /**
     * Save snapshot to file and show feedback
     */
    fun saveSnapshotWithFeedback(scannerArbitrator: ScannerArbitrator?) {
        try {
            val snapshotData = captureSnapshot(scannerArbitrator)
            val file = saveSnapshotToFile(snapshotData)
            
            // Show feedback
            showSuccessFeedback(file)
            vibrateIfSupported()
            
            lastSnapshotFile = file
            
        } catch (exception: Exception) {
            showErrorFeedback(exception)
        }
    }
    
    /**
     * Save snapshot data to JSON file
     */
    private fun saveSnapshotToFile(snapshotData: SnapshotData): File {
        val snapshotsDir = File(context.filesDir, "snapshots")
        if (!snapshotsDir.exists()) {
            snapshotsDir.mkdirs()
        }
        
        val timestamp = dateFormat.format(Date(snapshotData.ts))
        val filename = "snap_${timestamp}.json"
        val file = File(snapshotsDir, filename)
        
        FileWriter(file).use { writer ->
            writer.write(snapshotData.toPrettyJson())
        }
        
        return file
    }
    
    /**
     * Get zoom type string for snapshot
     */
    private fun getZoomType(zoomLevel: ZoomLevel): String {
        // For Phase 0, all zoom is digital
        return "numerique"
    }
    
    /**
     * Show success feedback toast
     */
    private fun showSuccessFeedback(file: File) {
        val shortPath = "snapshots/${file.name}"
        Toast.makeText(context, "Snapshot enregistré: $shortPath", Toast.LENGTH_LONG).show()
    }
    
    /**
     * Show error feedback toast
     */
    private fun showErrorFeedback(exception: Exception) {
        Toast.makeText(context, "Erreur snapshot: ${exception.message}", Toast.LENGTH_LONG).show()
    }
    
    /**
     * Provide light vibration feedback if supported
     */
    private fun vibrateIfSupported() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let { vib ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(100)
                }
            }
        } catch (exception: Exception) {
            // Ignore vibration errors - it's just feedback
        }
    }
    
    /**
     * Get total count of saved snapshots
     */
    fun getSnapshotCount(): Int {
        val snapshotsDir = File(context.filesDir, "snapshots")
        return if (snapshotsDir.exists()) {
            snapshotsDir.listFiles { _, name -> name.endsWith(".json") }?.size ?: 0
        } else {
            0
        }
    }
    
    /**
     * Get the last saved snapshot file
     */
    fun getLastSnapshotFile(): File? = lastSnapshotFile
    
    /**
     * Clear all saved snapshots
     */
    fun clearAllSnapshots(): Int {
        val snapshotsDir = File(context.filesDir, "snapshots")
        if (!snapshotsDir.exists()) return 0
        
        val files = snapshotsDir.listFiles { _, name -> name.endsWith(".json") }
        var deletedCount = 0
        
        files?.forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }
        
        return deletedCount
    }
}