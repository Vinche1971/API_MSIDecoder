# 04 - Intégration OpenCV avec CameraX

## 🎯 Objectif

Intégrer harmonieusement OpenCV dans le pipeline CameraX existant pour **partager la même source d'images** entre MLKit et OpenCV, sans duplication de flux ni impact performance sur l'infrastructure Phase 0 validée.

## 🔄 Architecture d'Intégration

### Source Unique CameraX Partagée
```
CameraX ImageAnalysis.Analyzer
    ↓
ImageProxy (YUV_420_888)
    ↓
YuvToNv21Converter.convert() ← POINT COMMUN
    ↓
ByteArray NV21 (640×480)
    ↓
ScannerArbitrator.processFrame()
    ↓
┌─────────────────┬─────────────────┐
│   MLKit Path    │   OpenCV Path   │
│  (Prioritaire)  │   (Fallback)    │
│                 │                 │
│ MLKit API       │ NV21→Mat        │
│ ↓               │ ↓               │
│ ML Processing   │ OpenCV Pipeline │
│ ↓               │ ↓               │ 
│ ScanResult      │ ScanResult      │
└─────────────────┴─────────────────┘
    ↓
Phase 0 Result Processing (Inchangé)
```

## 🛠️ Implémentation Technique

### 1. Extension YuvToNv21Converter (Inchangée)
```kotlin
// Phase 0 converter préservé à l'identique
class YuvToNv21Converter {
    fun convert(image: ImageProxy): ByteArray {
        // Implémentation Phase 0 exacte - AUCUN CHANGEMENT
        val yPlane = image.planes[0]
        val uPlane = image.planes[1] 
        val vPlane = image.planes[2]
        
        // Conversion YUV → NV21 existante
        return convertYuvToNv21(yPlane, uPlane, vPlane, image.width, image.height)
    }
}
```

### 2. OpenCV Mat Converter (Nouveau)
```kotlin
/**
 * Utilitaire conversion NV21 ↔ OpenCV Mat
 * Optimisé pour performance et compatibilité Android
 */
object OpenCVMatConverter {
    
    private const val TAG = "OpenCVMatConverter"
    
    /**
     * Conversion NV21 → OpenCV Mat grayscale
     * Utilise uniquement le plan Y pour performance optimale
     */
    fun nv21ToGrayMat(nv21Data: ByteArray, width: Int, height: Int): Mat {
        // NV21 format: [Y plane: width*height] [UV interleaved: width*height/2]
        // Pour grayscale, on utilise seulement le plan Y
        
        val yPlaneSize = width * height
        if (nv21Data.size < yPlaneSize) {
            throw IllegalArgumentException("Invalid NV21 data size: ${nv21Data.size}, expected >= $yPlaneSize")
        }
        
        // Extraction plan Y uniquement (grayscale)
        val yPlane = nv21Data.sliceArray(0 until yPlaneSize)
        
        // Création Mat OpenCV
        val grayMat = Mat(height, width, CvType.CV_8UC1)
        grayMat.put(0, 0, yPlane)
        
        return grayMat
    }
    
    /**
     * Conversion NV21 → OpenCV Mat RGB (si couleur nécessaire)
     * Plus coûteux, réservé aux cas spéciaux
     */
    fun nv21ToRgbMat(nv21Data: ByteArray, width: Int, height: Int): Mat {
        // Création Mat YUV complet
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21Data)
        
        // Conversion couleur OpenCV
        val rgbMat = Mat(height, width, CvType.CV_8UC3)
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        
        // Nettoyage Mat intermédiaire
        yuvMat.release()
        
        return rgbMat
    }
    
    /**
     * Conversion Mat → ByteArray pour décodeurs externes
     */
    fun matToByteArray(mat: Mat): ByteArray {
        val totalBytes = (mat.total() * mat.elemSize()).toInt()
        val byteArray = ByteArray(totalBytes)
        mat.get(0, 0, byteArray)
        return byteArray
    }
    
    /**
     * Rotation Mat selon orientation caméra
     * Nécessaire pour harmoniser avec rotationDegrees Phase 0
     */
    fun rotateMatIfNeeded(sourceMat: Mat, rotationDegrees: Int): Mat {
        if (rotationDegrees == 0) return sourceMat
        
        return when (rotationDegrees) {
            90 -> {
                val rotated = Mat()
                Core.transpose(sourceMat, rotated)
                Core.flip(rotated, rotated, 1) // Flip horizontal
                rotated
            }
            180 -> {
                val rotated = Mat()
                Core.flip(sourceMat, rotated, -1) // Flip both axes
                rotated
            }
            270 -> {
                val rotated = Mat()
                Core.transpose(sourceMat, rotated)
                Core.flip(rotated, rotated, 0) // Flip vertical
                rotated
            }
            else -> {
                // Rotation arbitraire (moins optimisée)
                val center = Point(sourceMat.width() / 2.0, sourceMat.height() / 2.0)
                val rotationMatrix = Imgproc.getRotationMatrix2D(center, rotationDegrees.toDouble(), 1.0)
                val rotated = Mat()
                Imgproc.warpAffine(sourceMat, rotated, rotationMatrix, sourceMat.size())
                rotationMatrix.release()
                rotated
            }
        }
    }
}
```

### 3. Enhanced ScannerArbitrator
```kotlin
class ScannerArbitrator {
    
    companion object {
        private const val TAG = "ScannerArbitrator"
        private const val OPENCV_TIMEOUT_MS = 45L // Respecte contrainte 50ms globale
    }
    
    private val mlkitScanner = MLKitScanner()        // Phase 0 (inchangé)
    private val openCVMsiScanner = OpenCVMsiScanner() // Nouveau
    
    private var useOpenCVMsi = true
    private val resultDelivered = AtomicBoolean(false)
    
    /**
     * Point d'entrée unifié - interface Phase 0 préservée
     */
    fun processFrame(
        nv21Data: ByteArray,  // ← Source commune depuis Phase 0
        width: Int,
        height: Int,
        rotationDegrees: Int, // ← Rotation Phase 0 harmonisée 
        callback: (ScanResult) -> Unit
    ) {
        val processingStartTime = System.currentTimeMillis()
        resultDelivered.set(false)
        
        // PRIORITÉ 1: MLKit (formats éprouvés, performance garantie)
        mlkitScanner.scanFrame(nv21Data, width, height, rotationDegrees) { mlkitResult ->
            val mlkitTime = System.currentTimeMillis() - processingStartTime
            
            when (mlkitResult) {
                is ScanResult.Success -> {
                    // MLKit succès → livraison immédiate, pas d'OpenCV
                    if (resultDelivered.compareAndSet(false, true)) {
                        Log.d(TAG, "MLKit SUCCESS in ${mlkitTime}ms: ${mlkitResult.format}")
                        callback(mlkitResult)
                    }
                }
                
                is ScanResult.NoResult -> {
                    // MLKit n'a rien trouvé → essayer OpenCV MSI
                    if (useOpenCVMsi) {
                        Log.d(TAG, "MLKit no result in ${mlkitTime}ms → trying OpenCV MSI")
                        tryOpenCVMsiDetection(nv21Data, width, height, rotationDegrees, callback, processingStartTime)
                    } else {
                        if (resultDelivered.compareAndSet(false, true)) {
                            callback(ScanResult.NoResult)
                        }
                    }
                }
                
                is ScanResult.Error -> {
                    // MLKit erreur → fallback OpenCV ou propagation erreur
                    Log.w(TAG, "MLKit error in ${mlkitTime}ms → OpenCV fallback")
                    if (useOpenCVMsi) {
                        tryOpenCVMsiDetection(nv21Data, width, height, rotationDegrees, callback, processingStartTime)
                    } else {
                        if (resultDelivered.compareAndSet(false, true)) {
                            callback(mlkitResult)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Tentative OpenCV MSI avec timeout strict
     */
    private fun tryOpenCVMsiDetection(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit,
        startTime: Long
    ) {
        // Protection timeout: temps restant depuis début processing
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = OPENCV_TIMEOUT_MS - elapsedTime
        
        if (remainingTime <= 5L) {
            // Pas assez de temps restant
            Log.w(TAG, "OpenCV skipped - insufficient time: ${remainingTime}ms remaining")
            if (resultDelivered.compareAndSet(false, true)) {
                callback(ScanResult.NoResult)
            }
            return
        }
        
        // Timeout handler
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (resultDelivered.compareAndSet(false, true)) {
                Log.w(TAG, "OpenCV timeout after ${remainingTime}ms")
                callback(ScanResult.NoResult)
            }
        }
        handler.postDelayed(timeoutRunnable, remainingTime)
        
        try {
            // Appel OpenCV MSI scanner
            openCVMsiScanner.scanFrame(nv21Data, width, height, rotationDegrees) { openCVResult ->
                handler.removeCallbacks(timeoutRunnable) // Annule timeout
                
                val totalTime = System.currentTimeMillis() - startTime
                
                when (openCVResult) {
                    is ScanResult.Success -> {
                        Log.d(TAG, "OpenCV MSI SUCCESS in ${totalTime}ms: ${openCVResult.data}")
                        if (resultDelivered.compareAndSet(false, true)) {
                            callback(openCVResult)
                        }
                    }
                    else -> {
                        Log.d(TAG, "OpenCV MSI no result in ${totalTime}ms")
                        if (resultDelivered.compareAndSet(false, true)) {
                            callback(ScanResult.NoResult)
                        }
                    }
                }
            }
            
        } catch (exception: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            Log.e(TAG, "OpenCV MSI processing error", exception)
            
            if (resultDelivered.compareAndSet(false, true)) {
                callback(ScanResult.Error(exception, ScanSource.MSI))
            }
        }
    }
    
    // Configuration methods
    fun setOpenCVMsiEnabled(enabled: Boolean) {
        useOpenCVMsi = enabled
        Log.d(TAG, "OpenCV MSI enabled: $enabled")
    }
}
```

### 4. OpenCV MSI Scanner Implementation
```kotlin
class OpenCVMsiScanner : Scanner {
    
    companion object {
        private const val TAG = "OpenCVMsiScanner"
        private const val MAX_PROCESSING_TIME = 40L // Marge 5ms avant timeout arbitrator
    }
    
    private val roiDetector = BarcodeROIDetector()
    private val roiExtractor = ROIExtractor()
    private val msiDecoder = MSIDecoder() // Interface vers T-106
    
    override fun scanFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        val scanStartTime = System.currentTimeMillis()
        
        try {
            // ÉTAPE 1: Conversion NV21 → OpenCV Mat (source commune avec MLKit)
            val conversionStart = System.currentTimeMillis()
            val grayMat = OpenCVMatConverter.nv21ToGrayMat(nv21Data, width, height)
            val conversionTime = System.currentTimeMillis() - conversionStart
            
            // ÉTAPE 2: Correction rotation (harmonisation avec Phase 0)
            val rotatedMat = OpenCVMatConverter.rotateMatIfNeeded(grayMat, rotationDegrees)
            val rotationTime = System.currentTimeMillis() - conversionStart - conversionTime
            
            // ÉTAPE 3: Détection ROI codes-barres
            val detectionStart = System.currentTimeMillis()
            val roiCandidates = roiDetector.detectBarcodeROIs(
                // On repasse par NV21 pour cohérence avec interface détection
                nv21Data, width, height, rotationDegrees
            )
            val detectionTime = System.currentTimeMillis() - detectionStart
            
            if (roiCandidates.isNotEmpty()) {
                // ÉTAPE 4: Traitement meilleur candidat
                val bestCandidate = roiCandidates.first()
                
                val extractionStart = System.currentTimeMillis()
                val binaryResult = roiExtractor.extractAndPrepareMSI(rotatedMat, bestCandidate)
                val extractionTime = System.currentTimeMillis() - extractionStart
                
                if (binaryResult != null && binaryResult.isHighQuality()) {
                    // ÉTAPE 5: Décodage MSI
                    val decodingStart = System.currentTimeMillis()
                    val msiResult = attemptMSIDecoding(binaryResult)
                    val decodingTime = System.currentTimeMillis() - decodingStart
                    
                    val totalTime = System.currentTimeMillis() - scanStartTime
                    
                    if (msiResult is ScanResult.Success) {
                        // Enrichissement résultat avec métriques
                        val enrichedResult = msiResult.copy(
                            processingTimeMs = totalTime,
                            boundingBox = bestCandidate.toAndroidRect()  // Compatible T-008
                        )
                        
                        Log.d(TAG, "OpenCV MSI decode success: conv=${conversionTime}ms, det=${detectionTime}ms, ext=${extractionTime}ms, dec=${decodingTime}ms, total=${totalTime}ms")
                        
                        callback(enrichedResult)
                    } else {
                        Log.d(TAG, "MSI decode failed after ${totalTime}ms")
                        callback(ScanResult.NoResult)
                    }
                    
                    binaryResult.cleanup()
                } else {
                    Log.d(TAG, "ROI extraction/quality failed")
                    callback(ScanResult.NoResult)
                }
            } else {
                val totalTime = System.currentTimeMillis() - scanStartTime
                Log.d(TAG, "No ROI candidates found in ${totalTime}ms")
                callback(ScanResult.NoResult)
            }
            
            // Nettoyage mémoire OpenCV
            grayMat.release()
            if (rotationDegrees != 0) rotatedMat.release()
            
        } catch (exception: Exception) {
            val totalTime = System.currentTimeMillis() - scanStartTime
            Log.e(TAG, "OpenCV MSI scan failed in ${totalTime}ms", exception)
            callback(ScanResult.Error(exception, ScanSource.MSI))
        }
    }
    
    private fun attemptMSIDecoding(binaryResult: MSIBinaryResult): ScanResult {
        // Interface vers le décodeur MSI existant (T-106)
        val binaryArray = binaryResult.toBinaryArray()
        
        return msiDecoder.decode(
            binaryArray,
            binaryResult.binaryMat.width(),
            binaryResult.binaryMat.height(),
            binaryResult.originalROI.orientation
        )
    }
}
```

## 🔧 Intégration MainActivity

### Extension MainActivity Phase 0
```kotlin
// MainActivity.kt - Extensions pour OpenCV (Phase 0 inchangée)
class MainActivity : AppCompatActivity() {
    
    // Composants Phase 0 (inchangés)
    private lateinit var cameraController: LifecycleCameraController
    private lateinit var scannerArbitrator: ScannerArbitrator
    
    // Ajout OpenCV lifecycle
    private val openCVLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.d(TAG, "OpenCV loaded successfully")
                    // Activer OpenCV MSI dans arbitrator
                    scannerArbitrator.setOpenCVMsiEnabled(true)
                }
                else -> {
                    Log.w(TAG, "OpenCV load failed, status: $status")
                    // Continuer sans OpenCV (MLKit seul)
                    scannerArbitrator.setOpenCVMsiEnabled(false)
                    super.onManagerConnected(status)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup Phase 0 inchangé
        setupCameraX()
        setupScannerArbitrator()
        
        // L'arbitrator gère automatiquement MLKit/OpenCV selon disponibilité
    }
    
    override fun onResume() {
        super.onResume()
        
        // Chargement OpenCV (non bloquant)
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback)
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!")
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
        
        // Phase 0 resume logic (inchangé)
        resumePhase0Logic()
    }
    
    /**
     * Gestion résultats unifié Phase 0 + OpenCV
     */
    private fun handleScanResult(result: ScanResult) {
        when (result.source) {
            ScanSource.MLKIT -> {
                // Traitement Phase 0 existant (inchangé)
                handleMLKitResult(result)
            }
            ScanSource.MSI -> {
                // Nouveau: traitement résultats OpenCV MSI
                handleOpenCVMsiResult(result)
            }
        }
        
        // Métriques communes (Phase 0 + extension OpenCV)
        updateCommonMetrics(result)
    }
    
    private fun handleOpenCVMsiResult(result: ScanResult) {
        if (result is ScanResult.Success) {
            // Utilisation T-008 coordinates (même logique que MLKit)
            result.boundingBox?.let { boundingRect ->
                val transformedRect = transformMLKitToPreview(
                    boundingRect,
                    currentFrameWidth,
                    currentFrameHeight,
                    binding.previewView.width,
                    binding.previewView.height
                )
                
                // Utilisation T-009 overlay (couleur MSI)
                binding.roiOverlay.updateMsiResult(transformedRect)
            }
            
            // Debug snapshot T-007 compatible
            debugSnapshot.updateMsiData(result)
            
            Log.d(TAG, "OpenCV MSI result: ${result.data}")
        }
    }
}
```

## 🎯 Points d'Attention Critiques

### 1. Gestion Mémoire OpenCV
```kotlin
// Pattern de nettoyage systématique
try {
    val mat = OpenCVMatConverter.nv21ToGrayMat(nv21Data, width, height)
    // ... processing
    return result
} finally {
    mat?.release() // CRITIQUE: toujours nettoyer
}
```

### 2. Threading et Synchronisation
```kotlin
// Execution sur ImageAnalysis thread (Phase 0 preserved)
// PAS de nouveau thread pour OpenCV - même thread que MLKit
override fun analyze(image: ImageProxy) {
    // Thread: CameraX-ImageAnalysis
    val nv21Data = yuvToNv21Converter.convert(image) 
    scannerArbitrator.processFrame(nv21Data, ...) { result ->
        // Callback sur même thread
        runOnUiThread {
            handleScanResult(result) // UI updates sur main thread
        }
    }
    image.close()
}
```

### 3. Performance Monitoring
```kotlin
class OpenCVPerformanceMonitor {
    private var conversionTimeMs = 0L
    private var detectionTimeMs = 0L  
    private var extractionTimeMs = 0L
    private var totalProcessedFrames = 0
    
    fun trackConversion(timeMs: Long) { conversionTimeMs += timeMs }
    fun trackDetection(timeMs: Long) { detectionTimeMs += timeMs }
    
    fun getAverages(): OpenCVStats {
        return if (totalProcessedFrames > 0) {
            OpenCVStats(
                avgConversionMs = conversionTimeMs / totalProcessedFrames,
                avgDetectionMs = detectionTimeMs / totalProcessedFrames,
                totalFrames = totalProcessedFrames
            )
        } else OpenCVStats()
    }
}
```

## ⚡ Optimisations Intégration

### 1. Lazy Initialization OpenCV
```kotlin
class OpenCVComponents {
    private val roiDetector by lazy { BarcodeROIDetector() }
    private val roiExtractor by lazy { ROIExtractor() }
    
    // Initialisation seulement si OpenCV disponible et activé
    fun isInitialized(): Boolean = ::roiDetector.isInitialized
}
```

### 2. Fallback Gracieux
```kotlin
// Configuration fallback si OpenCV indisponible
data class ScannerConfig(
    val openCVAvailable: Boolean = false,
    val openCVEnabled: Boolean = false,
    val fallbackToMLKitOnly: Boolean = true
)
```

## 🎯 Critères de Réussite Intégration

### Compatibilité Phase 0
- ✅ **Interface inchangée** : ScannerArbitrator.processFrame() identique
- ✅ **Performance préservée** : MLKit path exact même temps qu'avant  
- ✅ **Métriques compatibles** : Extension métriques Phase 0 sans disruption
- ✅ **État management** : Système persistance Phase 0 fonctionne avec MSI

### Performance OpenCV
- ✅ **Conversion NV21→Mat** : <2ms pour 640×480
- ✅ **Total OpenCV path** : <45ms respecte timeout arbitrator
- ✅ **Memory impact** : <8MB heap increase avec nettoyage proper
- ✅ **Threading** : Aucun nouveau thread, réutilise ImageAnalysis thread

### Robustesse
- ✅ **Fallback gracieux** : Si OpenCV échoue, application continue normalement  
- ✅ **Error handling** : Exceptions OpenCV n'impactent pas pipeline MLKit
- ✅ **Resource management** : Nettoyage Mat automatique prévient memory leaks
- ✅ **Lifecycle management** : OpenCV init/cleanup harmonisé avec Activity lifecycle

---

**🎯 Cette intégration garantit le partage optimal de la source CameraX entre MLKit et OpenCV, avec préservation complète de l'infrastructure Phase 0 et extension transparente des capacités de détection MSI.**