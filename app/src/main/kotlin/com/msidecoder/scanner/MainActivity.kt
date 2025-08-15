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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.msidecoder.scanner.camera.YuvToNv21Converter
import com.msidecoder.scanner.databinding.ActivityMainBinding
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
    
    private val metricsCollector = MetricsCollector()
    private val scannerStateManager = ScannerStateManager()
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
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Setup start/stop button
        setupStartStopButton()
        
        // Setup scanner state listener
        setupScannerStateListener()
        
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
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview
            )
            Log.d(TAG, "Preview bound successfully")
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