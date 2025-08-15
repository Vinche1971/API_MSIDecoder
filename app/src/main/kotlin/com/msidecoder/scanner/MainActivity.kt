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
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.msidecoder.scanner.camera.YuvToNv21Converter
import com.msidecoder.scanner.databinding.ActivityMainBinding
import com.msidecoder.scanner.state.CameraControlsManager
import com.msidecoder.scanner.state.PreferencesRepository
import com.msidecoder.scanner.state.ScannerState
import com.msidecoder.scanner.state.ScannerStateManager
import com.msidecoder.scanner.utils.MetricsCollector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    
    private val metricsCollector = MetricsCollector()
    private val scannerStateManager = ScannerStateManager()
    private val cameraControlsManager = CameraControlsManager()
    private lateinit var preferencesRepository: PreferencesRepository
    private val overlayHandler = Handler(Looper.getMainLooper())
    private val overlayUpdateRunnable = object : Runnable {
        override fun run() {
            binding.metricsOverlay.updateMetrics(metricsCollector.getSnapshot())
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                showCameraErrorDialog()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            return
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        // Create ImageAnalysis but don't bind it initially (IDLE state)
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                Log.d(TAG, "Setting ImageAnalysis analyzer")
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()
            Log.d(TAG, "Binding Preview only (Scanner IDLE)")
            // Only bind preview initially - ImageAnalysis will be bound when scanner starts
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )
            
            // Capture camera control references
            cameraControl = camera.cameraControl
            cameraInfo = camera.cameraInfo
            
            Log.d(TAG, "Preview bound successfully")
            Log.d(TAG, "Camera capabilities: Torch=${cameraInfo?.hasFlashUnit()}, MaxZoom=${cameraInfo?.zoomState?.value?.maxZoomRatio}")
            
            // Apply saved zoom after camera is ready
            applySavedZoomAfterCameraReady()
            
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            showCameraErrorDialog()
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        Log.d(TAG, "processFrame called")
        metricsCollector.onFrameStart()
        val startTime = System.currentTimeMillis()
        
        try {
            val width = imageProxy.width
            val height = imageProxy.height
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            
            // Convert YUV to NV21 (use simple version to avoid crash)
            val nv21Data = YuvToNv21Converter.convert(imageProxy)
            
            // TODO: Pass nv21Data to ML Kit and MSI pipeline
            
            val processingTime = System.currentTimeMillis() - startTime
            metricsCollector.onFrameProcessed(processingTime, width, height, rotationDegrees)
            
        } finally {
            imageProxy.close()
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
        Log.d(TAG, "Starting scanner")
        val provider = cameraProvider ?: return
        val analysis = imageAnalysis ?: return
        
        try {
            // Bind ImageAnalysis to start processing
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                analysis
            )
            metricsCollector.reset() // Reset metrics when starting
            Log.d(TAG, "Scanner started - ImageAnalysis bound")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to start scanner", exc)
        }
    }
    
    private fun stopScanner() {
        Log.d(TAG, "Stopping scanner")
        val provider = cameraProvider ?: return
        val analysis = imageAnalysis ?: return
        
        try {
            // Unbind only ImageAnalysis, keep Preview
            provider.unbind(analysis)
            Log.d(TAG, "Scanner stopped - ImageAnalysis unbound")
        } catch (exc: Exception) {
            Log.e(TAG, "Failed to stop scanner", exc)
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
            repeat(savedControlsState.zoomLevel.ordinal) {
                val maxZoom = 3.0f // Use default until camera is ready
                Log.d(TAG, "Cycling zoom: step ${it + 1}/${savedControlsState.zoomLevel.ordinal}")
                cameraControlsManager.cycleZoom(maxZoom)
                Log.d(TAG, "After cycle step ${it + 1}: ${cameraControlsManager.getCurrentState().zoomLevel}")
            }
        } else {
            Log.d(TAG, "Zoom already at ZOOM_1X, no cycling needed")
        }
        
        Log.d(TAG, "Final CameraControlsManager state after restore: ${cameraControlsManager.getCurrentState()}")
        Log.d(TAG, "=== RESTORE COMPLETE ===")
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
        Log.d(TAG, "=== ZOOM APPLY COMPLETE ===")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== ON RESUME ===")
        Log.d(TAG, "CameraControlsManager state on resume: ${cameraControlsManager.getCurrentState()}")
        // Force apply current zoom state when resuming 
        if (cameraControl != null) {
            val currentZoom = cameraControlsManager.getCurrentState().zoomLevel.ratio
            Log.d(TAG, "Applying zoom on resume: $currentZoom")
            cameraControl?.setZoomRatio(currentZoom)
        }
        Log.d(TAG, "=== ON RESUME COMPLETE ===")
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayHandler.removeCallbacks(overlayUpdateRunnable)
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MSIScanner"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}