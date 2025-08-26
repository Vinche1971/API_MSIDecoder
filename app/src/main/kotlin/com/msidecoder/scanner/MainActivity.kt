package com.msidecoder.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.msidecoder.scanner.databinding.ActivityMainBinding
import com.msidecoder.scanner.debug.SnapshotManager
import com.msidecoder.scanner.scanner.MLKitScanner
import com.msidecoder.scanner.scanner.MSIScanner
import com.msidecoder.scanner.scanner.ScannerArbitrator
import com.msidecoder.scanner.scanner.ScanResult
import com.msidecoder.scanner.scanner.ScanSource
import com.msidecoder.scanner.state.CameraControlsManager
import com.msidecoder.scanner.state.PreferencesRepository
import com.msidecoder.scanner.opencv.OpenCVConverter
import org.opencv.android.OpenCVLoader
import com.msidecoder.scanner.state.ScannerState
import com.msidecoder.scanner.state.ScannerStateManager
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.msidecoder.scanner.utils.MetricsCollector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraController: LifecycleCameraController? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    
    // T-008: MLKit native components 
    private lateinit var barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner
    private var isScannerActive = false // Control processing state
    
    private val metricsCollector = MetricsCollector()
    private val scannerStateManager = ScannerStateManager()
    private val cameraControlsManager = CameraControlsManager()
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var snapshotManager: SnapshotManager
    
    
    // Scanner components
    private lateinit var scannerArbitrator: ScannerArbitrator
    private var lastScanTime = 0L
    private val debounceIntervalMs = 750L // 750ms debounce for scan results
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val overlayUpdateRunnable = object : Runnable {
        override fun run() {
            // Clear old scan source (1 second timeout)
            metricsCollector.clearScanSourceIfOld(1000L)
            
            // Get scanner metrics
            val scannerMetrics = if (::scannerArbitrator.isInitialized) {
                scannerArbitrator.getMetrics()
            } else {
                ScannerArbitrator.ScanMetrics(0, 0, 0, 0)
            }
            
            // Update overlay with combined metrics
            val snapshot = metricsCollector.getSnapshot(
                mlkitTimeMs = scannerMetrics.mlkitTimeMs,
                msiTimeMs = scannerMetrics.msiTimeMs,
                mlkitHits = scannerMetrics.mlkitHits,
                msiHits = scannerMetrics.msiHits
            )
            binding.metricsOverlay.updateMetrics(snapshot)
            overlayHandler.postDelayed(this, 100) // 10Hz refresh
        }
    }
    
    // Debounce for button clicks
    private var lastClickTime = 0L
    private val debounceInterval = 200L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        preferencesRepository = PreferencesRepository(this)
        
        // T-008: Initialize native MLKit barcode scanner
        barcodeScanner = BarcodeScanning.getClient()
        
        // T-101: Initialize OpenCV (early initialization)
        initializeOpenCV()
        
        // T-007: Initialize snapshot manager
        snapshotManager = SnapshotManager(
            context = this,
            metricsCollector = metricsCollector,
            cameraControlsManager = cameraControlsManager,
            preferencesRepository = preferencesRepository
        )
        
        // Initialize scanner components
        initializeScanners()
        
        // Restore saved states
        restoreStates()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Setup buttons
        setupStartStopButton()
        setupTorchButton()
        setupZoomButton()
        setupSnapshotButton()
        
        // Setup state listeners
        setupScannerStateListener()
        setupCameraControlsListener()
        
        // Setup persistence listeners
        setupPersistenceListeners()
        
        // Start overlay updates
        overlayHandler.post(overlayUpdateRunnable)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        Log.d(TAG, "Starting camera with MlKitAnalyzer")
        
        // T-008: Use LifecycleCameraController for native MLKit integration
        cameraController = LifecycleCameraController(this)
        
        // Configure camera controller
        cameraController?.apply {
            // Set camera selector
            setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
            
            // T-008: Configure MlKitAnalyzer BEFORE bindToLifecycle (critical for COORDINATE_SYSTEM_VIEW_REFERENCED)
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(this@MainActivity),
                MlKitAnalyzer(
                    listOf(barcodeScanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(this@MainActivity)
                ) { result: MlKitAnalyzer.Result? ->
                    handleMlKitNativeResult(result)
                }
            )
            
            // Bind to lifecycle AFTER analyzer configuration
            bindToLifecycle(this@MainActivity)
            
            // Set surface provider
            binding.previewView.controller = this
        }
        
        // Capture camera control references after controller is set
        cameraController?.let { controller ->
            cameraControl = controller.cameraControl
            cameraInfo = controller.cameraInfo
            
            Log.d(TAG, "Camera capabilities: Torch=${cameraInfo?.hasFlashUnit()}, MaxZoom=${cameraInfo?.zoomState?.value?.maxZoomRatio}")
            
            // Apply saved zoom after camera is ready
            applySavedZoomAfterCameraReady()
        }
        
        Log.d(TAG, "Camera started successfully with MlKitAnalyzer")
    }


    
    private fun handleScanResult(result: ScanResult) {
        when (result) {
            is ScanResult.Success -> {
                // T-008: Native coordinates already handled in handleMlKitNativeResult
                
                // T-006: Check anti-republication before debounce
                val lastResult = preferencesRepository.getLastScanResult()
                if (lastResult != null && lastResult.matches(result.data) && lastResult.isRecent(800L)) {
                    Log.d(TAG, "SCAN IGNORED: Same result '${result.data}' within 800ms (anti-republication)")
                    return
                }
                
                // Standard debounce to prevent multiple detections
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastScanTime < debounceIntervalMs) {
                    return
                }
                lastScanTime = currentTime
                
                Log.d(TAG, "SCAN SUCCESS: ${result.format} = '${result.data}' (${result.source}, ${result.processingTimeMs}ms)")
                
                // T-006: Save scan result immediately for anti-republication
                preferencesRepository.setLastScanResult(result.data, currentTime)
                Log.d(TAG, "Last scan result saved: '${result.data}' at $currentTime")
                
                // Update metrics with scan source
                metricsCollector.updateScanSource(result.source.name)
                
                // TODO: Add beep + haptic feedback
            }
            
            is ScanResult.Error -> {
                Log.w(TAG, "Scan error from ${result.source}", result.exception)
            }
            
            is ScanResult.NoResult -> {
                // Normal case - no barcode detected
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.camera_permission_required)
            .setMessage(R.string.camera_permission_denied_message)
            .setPositiveButton(R.string.close) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showCameraErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Error")
            .setMessage("Failed to initialize camera. Please restart the app.")
            .setPositiveButton(R.string.close) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupStartStopButton() {
        binding.fabStartStop.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < debounceInterval) {
                return@setOnClickListener // Ignore rapid clicks
            }
            lastClickTime = currentTime
            
            scannerStateManager.toggle()
        }
    }
    
    private fun setupScannerStateListener() {
        scannerStateManager.addStateChangeListener { state ->
            when (state) {
                ScannerState.IDLE -> {
                    stopScanner()
                    updateButtonForIdleState()
                    // Auto-disable torch when stopping scanner
                    if (cameraControlsManager.getCurrentState().torchEnabled) {
                        cameraControlsManager.setTorch(false)
                        // Reset user intended state when scanner stops (intentional)
                        preferencesRepository.setUserIntendedTorchState(false)
                        Log.d(TAG, "Reset user intended torch state: OFF (scanner stopped)")
                    }
                }
                ScannerState.ACTIVE -> {
                    startScanner()
                    updateButtonForActiveState()
                }
            }
        }
    }
    
    private fun startScanner() {
        Log.d(TAG, "Starting scanner with MlKitAnalyzer")
        
        try {
            // T-008: MlKitAnalyzer is already configured, just activate processing
            isScannerActive = true
            
            metricsCollector.reset() // Reset metrics when starting
            Log.d(TAG, "Scanner started - Processing activated")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to start scanner", exc)
        }
    }
    
    private fun stopScanner() {
        Log.d(TAG, "Stopping scanner")
        
        try {
            // T-008: Deactivate processing but keep analyzer running
            isScannerActive = false
            
            // Clear ROI overlay when stopping
            binding.roiOverlay.clearRoi()
            
            Log.d(TAG, "Scanner stopped - Processing deactivated")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to stop scanner", exc)
        }
    }
    
    // T-008: Handle MLKit native results with COORDINATE_SYSTEM_VIEW_REFERENCED
    private fun handleMlKitNativeResult(result: MlKitAnalyzer.Result?) {
        // Early return if scanner is not active
        if (!isScannerActive) {
            return
        }
        
        metricsCollector.onFrameStart()
        val startTime = System.currentTimeMillis()
        
        try {
            result?.getValue(barcodeScanner)?.let { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val barcode = barcodes.first() // Take first detected barcode
                    Log.d(TAG, "=== T-008 NATIVE MLKIT SUCCESS ===")
                    Log.d(TAG, "Detected: ${barcode.displayValue}")
                    Log.d(TAG, "Format: ${barcode.format}")
                    Log.d(TAG, "BoundingBox NATIVE (PreviewView space): ${barcode.boundingBox}")
                    Log.d(TAG, "PreviewView Size: ${binding.previewView.width}×${binding.previewView.height}")
                    
                    // Create ScanResult compatible with existing pipeline
                    val scanResult = ScanResult.Success(
                        data = barcode.displayValue ?: "",
                        format = mapBarcodeFormat(barcode.format),
                        source = ScanSource.ML_KIT,
                        processingTimeMs = System.currentTimeMillis() - startTime,
                        boundingBox = barcode.boundingBox // Already in PreviewView coordinates!
                    )
                    
                    // Display ROI with native coordinates (no transformation needed!)
                    barcode.boundingBox?.let { nativeBoundingBox ->
                        binding.roiOverlay.updateRoi(
                            rects = listOf(nativeBoundingBox),
                            cameraWidth = 0, // Not needed with native coordinates
                            cameraHeight = 0  // Not needed with native coordinates
                        )
                    }
                    
                    // Handle through existing pipeline
                    handleScanResult(scanResult)
                } else {
                    // No barcode detected - clear overlay
                    binding.roiOverlay.clearRoi()
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            metricsCollector.onFrameProcessed(processingTime, 0, 0, 0) // Frame dimensions not relevant for native
            
        } catch (exc: Exception) {
            Log.e(TAG, "Error handling MLKit native result", exc)
        }
    }
    
    // Helper function to map MLKit barcode formats to our internal format
    private fun mapBarcodeFormat(mlkitFormat: Int): String {
        return when (mlkitFormat) {
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            else -> "UNKNOWN_$mlkitFormat"
        }
    }
    
    private fun updateButtonForIdleState() {
        binding.fabStartStop.icon = ContextCompat.getDrawable(this, R.drawable.ic_play)
        binding.fabStartStop.text = getString(R.string.start_scanner)
        binding.fabStartStop.contentDescription = getString(R.string.start_scanner)
    }
    
    private fun updateButtonForActiveState() {
        binding.fabStartStop.icon = ContextCompat.getDrawable(this, R.drawable.ic_stop)
        binding.fabStartStop.text = getString(R.string.stop_scanner)
        binding.fabStartStop.contentDescription = getString(R.string.stop_scanner)
    }
    
    private fun setupTorchButton() {
        binding.fabTorch.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < debounceInterval) {
                return@setOnClickListener
            }
            lastClickTime = currentTime
            
            // Save user intended torch state before toggling
            val newTorchState = !cameraControlsManager.getCurrentState().torchEnabled
            preferencesRepository.setUserIntendedTorchState(newTorchState)
            Log.d(TAG, "User intended torch state saved: $newTorchState")
            
            cameraControlsManager.toggleTorch()
        }
    }
    
    private fun setupZoomButton() {
        // Set initial zoom button color
        binding.fabZoom.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_tender)
        
        binding.fabZoom.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < debounceInterval) {
                return@setOnClickListener
            }
            lastClickTime = currentTime
            
            Log.d(TAG, "=== ZOOM BUTTON CLICKED ===")
            Log.d(TAG, "Before click - CameraControlsManager: ${cameraControlsManager.getCurrentState().zoomLevel}")
            Log.d(TAG, "Before click - Camera zoom: ${cameraInfo?.zoomState?.value?.zoomRatio}")
            
            val maxZoom = cameraInfo?.zoomState?.value?.maxZoomRatio ?: 3.0f
            cameraControlsManager.cycleZoom(maxZoom)
            
            Log.d(TAG, "After click - CameraControlsManager: ${cameraControlsManager.getCurrentState().zoomLevel}")
            Log.d(TAG, "=== ZOOM BUTTON CLICK COMPLETE ===")
        }
    }
    
    private fun setupSnapshotButton() {
        // Set snapshot button color (distinct from others)
        binding.fabSnapshot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_500)
        
        binding.fabSnapshot.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < debounceInterval) {
                return@setOnClickListener
            }
            lastClickTime = currentTime
            
            Log.d(TAG, "=== SNAPSHOT BUTTON CLICKED ===")
            
            // T-007: Capture snapshot instantly with feedback
            snapshotManager.saveSnapshotWithFeedback(
                if (::scannerArbitrator.isInitialized) scannerArbitrator else null
            )
            
            Log.d(TAG, "Total snapshots saved: ${snapshotManager.getSnapshotCount()}")
            Log.d(TAG, "=== SNAPSHOT CAPTURE COMPLETE ===")
        }
    }
    
    private fun setupCameraControlsListener() {
        cameraControlsManager.addStateChangeListener { controlsState ->
            // Update torch
            cameraControl?.enableTorch(controlsState.torchEnabled)
            updateTorchButton(controlsState.torchEnabled)
            
            // Update zoom
            cameraControl?.setZoomRatio(controlsState.zoomLevel.ratio)
            updateZoomButton(controlsState.zoomLevel)
            
            Log.d(TAG, "Camera controls updated: Torch=${controlsState.torchEnabled}, Zoom=${controlsState.zoomLevel.displayText}")
        }
    }
    
    private fun updateTorchButton(enabled: Boolean) {
        if (enabled) {
            // Torch ON: invert colors (white background, blue text)
            binding.fabTorch.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.white)
            binding.fabTorch.setTextColor(ContextCompat.getColor(this, R.color.blue_tender))
            binding.fabTorch.contentDescription = getString(R.string.torch_on)
        } else {
            // Torch OFF: normal colors (blue background, white text)  
            binding.fabTorch.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_tender)
            binding.fabTorch.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.fabTorch.contentDescription = getString(R.string.torch_off)
        }
    }
    
    private fun updateZoomButton(zoomLevel: com.msidecoder.scanner.state.ZoomLevel) {
        val (text, descriptionRes) = when (zoomLevel) {
            com.msidecoder.scanner.state.ZoomLevel.ZOOM_1X -> "1" to R.string.zoom_1x
            com.msidecoder.scanner.state.ZoomLevel.ZOOM_2X -> "2" to R.string.zoom_2x
            com.msidecoder.scanner.state.ZoomLevel.ZOOM_3X -> "3" to R.string.zoom_3x
        }
        binding.fabZoom.text = text
        binding.fabZoom.contentDescription = getString(descriptionRes)
    }
    
    private fun restoreStates() {
        Log.d(TAG, "=== RESTORING SAVED STATES ===")
        
        // Restore camera controls state
        val savedControlsState = preferencesRepository.getCameraControlsState()
        Log.d(TAG, "Loaded from SharedPrefs: Torch=${savedControlsState.torchEnabled}, Zoom=${savedControlsState.zoomLevel} (ratio=${savedControlsState.zoomLevel.ratio})")
        
        // Apply the saved camera controls state (this will trigger UI updates via listeners)
        cameraControlsManager.setTorch(savedControlsState.torchEnabled)
        Log.d(TAG, "After setTorch - CameraControlsManager state: ${cameraControlsManager.getCurrentState()}")
        
        // Set zoom state directly without applying to camera yet (camera not ready)
        if (savedControlsState.zoomLevel != com.msidecoder.scanner.state.ZoomLevel.ZOOM_1X) {
            Log.d(TAG, "Need to restore zoom from ZOOM_1X to ${savedControlsState.zoomLevel} (${savedControlsState.zoomLevel.ordinal} cycles)")
            // Manually cycle to the saved zoom level
            try {
                repeat(savedControlsState.zoomLevel.ordinal) {
                    val maxZoom = 3.0f // Use default until camera is ready
                    Log.d(TAG, "Cycling zoom: step ${it + 1}/${savedControlsState.zoomLevel.ordinal}")
                    cameraControlsManager.cycleZoom(maxZoom)
                    Log.d(TAG, "After cycle step ${it + 1}: ${cameraControlsManager.getCurrentState().zoomLevel}")
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to restore zoom state - falling back to 1X", exception)
                // Fallback: don't change zoom state if restoration fails
                // The controls will stay at default 1X which is safe
            }
        } else {
            Log.d(TAG, "Zoom already at ZOOM_1X, no cycling needed")
        }
        
        Log.d(TAG, "Final CameraControlsManager state after restore: ${cameraControlsManager.getCurrentState()}")
        
        // T-006: Restore user intended torch state
        val userIntendedTorch = preferencesRepository.getUserIntendedTorchState()
        if (userIntendedTorch && !cameraControlsManager.getCurrentState().torchEnabled) {
            Log.d(TAG, "Restoring user intended torch state: ON")
            cameraControlsManager.setTorch(true)
        }
        
        // T-006: Force UI update after restore
        val finalState = cameraControlsManager.getCurrentState()
        updateTorchButton(finalState.torchEnabled)
        updateZoomButton(finalState.zoomLevel)
        
        // T-006: Restore scanner state
        restoreScannerState()
        
        Log.d(TAG, "=== RESTORE COMPLETE ===")
    }
    
    private fun restoreScannerState() {
        val savedScannerState = preferencesRepository.getScannerState()
        val alwaysStartStopped = preferencesRepository.getAlwaysStartStopped()
        
        Log.d(TAG, "Scanner state from prefs: $savedScannerState, alwaysStartStopped: $alwaysStartStopped")
        
        if (alwaysStartStopped) {
            Log.d(TAG, "Always start stopped enabled - forcing IDLE state")
            scannerStateManager.setState(ScannerState.IDLE)
            return
        }
        
        when (savedScannerState) {
            ScannerState.ACTIVE -> {
                Log.d(TAG, "Scanner was ACTIVE - will auto-start after camera ready")
                // Don't start immediately - wait for camera to be ready
                // Will be handled in applySavedZoomAfterCameraReady()
            }
            ScannerState.IDLE -> {
                Log.d(TAG, "Scanner was IDLE - keeping stopped")
                scannerStateManager.setState(ScannerState.IDLE)
            }
        }
    }
    
    private fun setupPersistenceListeners() {
        // Save scanner state changes
        scannerStateManager.addStateChangeListener { state ->
            preferencesRepository.setScannerState(state)
        }
        
        // Save camera controls state changes  
        cameraControlsManager.addStateChangeListener { controlsState ->
            preferencesRepository.setCameraControlsState(controlsState)
        }
    }
    
    private fun applySavedZoomAfterCameraReady() {
        // This should be called after camera is bound and cameraControl is available
        val currentState = cameraControlsManager.getCurrentState()
        Log.d(TAG, "=== APPLYING ZOOM TO CAMERA ===")
        Log.d(TAG, "CameraControlsManager current state: ${currentState.zoomLevel} (ratio=${currentState.zoomLevel.ratio})")
        Log.d(TAG, "Camera maxZoom: ${cameraInfo?.zoomState?.value?.maxZoomRatio}")
        Log.d(TAG, "Camera current zoom before apply: ${cameraInfo?.zoomState?.value?.zoomRatio}")
        
        // Always apply the current zoom state to camera (even if it's 1.0f, to be sure)
        cameraControl?.setZoomRatio(currentState.zoomLevel.ratio)
        Log.d(TAG, "Camera setZoomRatio called with: ${currentState.zoomLevel.ratio}")
        
        // T-006: Apply torch state to camera (after camera is ready)
        Log.d(TAG, "Applying torch state to camera: ${currentState.torchEnabled}")
        cameraControl?.enableTorch(currentState.torchEnabled)
        
        // T-006: Auto-start scanner if it was previously ACTIVE
        autoStartScannerIfNeeded()
        
        Log.d(TAG, "=== ZOOM & TORCH APPLY COMPLETE ===")
    }
    
    private fun autoStartScannerIfNeeded() {
        val savedScannerState = preferencesRepository.getScannerState()
        val alwaysStartStopped = preferencesRepository.getAlwaysStartStopped()
        
        Log.d(TAG, "=== AUTO-START CHECK ===")
        Log.d(TAG, "Saved scanner state: $savedScannerState")
        Log.d(TAG, "Always start stopped: $alwaysStartStopped")
        Log.d(TAG, "Current scanner state: ${scannerStateManager.getCurrentState()}")
        
        if (alwaysStartStopped) {
            Log.d(TAG, "Always start stopped enabled - no auto-start")
            return
        }
        
        // T-006: Check prerequisites before auto-starting
        if (!canAutoStartScanner()) {
            Log.w(TAG, "Cannot auto-start scanner - prerequisites not met")
            // Force IDLE state and save it
            scannerStateManager.setState(ScannerState.IDLE)
            return
        }
        
        if (savedScannerState == ScannerState.ACTIVE && scannerStateManager.getCurrentState() == ScannerState.IDLE) {
            Log.d(TAG, "Auto-starting scanner (was previously ACTIVE)")
            try {
                scannerStateManager.setState(ScannerState.ACTIVE)
                Log.d(TAG, "Scanner auto-started successfully")
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to auto-start scanner", exception)
                // Fallback to IDLE on any error
                scannerStateManager.setState(ScannerState.IDLE)
            }
        } else {
            Log.d(TAG, "No auto-start needed")
        }
        Log.d(TAG, "=== AUTO-START CHECK COMPLETE ===")
    }
    
    private fun canAutoStartScanner(): Boolean {
        // Check camera permissions
        if (!allPermissionsGranted()) {
            Log.w(TAG, "Camera permission not granted")
            return false
        }
        
        // Check camera availability
        if (cameraController == null) {
            Log.w(TAG, "Camera controller not available")
            return false
        }
        
        if (cameraControl == null) {
            Log.w(TAG, "Camera control not available")
            return false
        }
        
        // Check scanner arbitrator
        if (!::scannerArbitrator.isInitialized) {
            Log.w(TAG, "Scanner arbitrator not initialized")
            return false
        }
        
        Log.d(TAG, "All prerequisites met for auto-start")
        return true
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== ON RESUME ===")
        Log.d(TAG, "CameraControlsManager state on resume: ${cameraControlsManager.getCurrentState()}")
        
        // Force apply current states when resuming 
        if (cameraControl != null) {
            val currentState = cameraControlsManager.getCurrentState()
            
            // Restore zoom
            Log.d(TAG, "Applying zoom on resume: ${currentState.zoomLevel.ratio}")
            cameraControl?.setZoomRatio(currentState.zoomLevel.ratio)
            
            // T-006: Restore torch state (may have been turned off in onPause)
            Log.d(TAG, "Applying torch on resume: ${currentState.torchEnabled}")
            cameraControl?.enableTorch(currentState.torchEnabled)
        }
        
        Log.d(TAG, "=== ON RESUME COMPLETE ===")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== ON PAUSE ===")
        
        // T-006: Auto-OFF torch when going to background (system only)
        val currentTorchState = cameraControlsManager.getCurrentState().torchEnabled
        if (currentTorchState) {
            Log.d(TAG, "Auto-OFF torch due to onPause() - keeping user intention")
            // Turn off torch directly on camera without updating CameraControlsManager
            // This preserves user intended state while turning off LED
            cameraControl?.enableTorch(false)
        }
        
        Log.d(TAG, "=== ON PAUSE COMPLETE ===")
    }

    private fun initializeScanners() {
        Log.d(TAG, "Initializing scanner components")
        
        val mlkitScanner = MLKitScanner()
        val msiScanner = MSIScanner() 
        
        scannerArbitrator = ScannerArbitrator(
            mlkitScanner = mlkitScanner,
            msiScanner = msiScanner,
            executor = cameraExecutor
        )
        
        Log.d(TAG, "Scanner components initialized")
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayHandler.removeCallbacks(overlayUpdateRunnable)
        if (::scannerArbitrator.isInitialized) {
            scannerArbitrator.close()
        }
        cameraExecutor.shutdown()
    }
    
    /**
     * T-101: Initialize OpenCV early in onCreate() using modern API
     */
    private fun initializeOpenCV() {
        Log.d(TAG, "=== T-101: Initializing OpenCV ===")
        
        try {
            // OpenCV 4.12.0: Use direct initialization (simpler than callback approach)
            val success = OpenCVLoader.initLocal()
            if (success) {
                Log.d(TAG, "✅ OpenCV loaded successfully - version: ${OpenCVLoader.OPENCV_VERSION}")
                // T-101: Quick test - NV21→Mat conversion functionality
                testOpenCVBaseline()
            } else {
                Log.e(TAG, "❌ OpenCV initialization failed")
            }
        } catch (exception: Exception) {
            Log.e(TAG, "❌ OpenCV initialization exception: ${exception.message}", exception)
        }
    }
    
    /**
     * T-101: OpenCV baseline test - NV21→Mat conversion validation
     */
    private fun testOpenCVBaseline() {
        Log.d(TAG, "=== OpenCV Baseline Test ===")
        
        try {
            // Create mock NV21 data (640x480 typical CameraX size)  
            val width = 640
            val height = 480
            val nv21Size = width * height * 3 / 2 // NV21 format size
            val mockNv21Data = ByteArray(nv21Size) { (it % 256).toByte() }
            
            // Test conversion
            val startTime = System.currentTimeMillis()
            val grayMat = OpenCVConverter.nv21ToGrayMat(mockNv21Data, width, height)
            val conversionTime = System.currentTimeMillis() - startTime
            
            if (grayMat != null) {
                val isValid = OpenCVConverter.validateMat(grayMat, width, height)
                Log.d(TAG, "✅ OpenCV baseline test SUCCESS: conversion=${conversionTime}ms, valid=$isValid")
                
                // Clean up
                grayMat.release()
            } else {
                Log.e(TAG, "❌ OpenCV baseline test FAILED: conversion returned null")
            }
            
        } catch (exception: Exception) {
            Log.e(TAG, "❌ OpenCV baseline test EXCEPTION: ${exception.message}", exception)
        }
        
        Log.d(TAG, "=== OpenCV Baseline Test Complete ===")
    }
    

    companion object {
        private const val TAG = "MSIScanner"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}