# 01 - Architecture d'Intégration OpenCV

## 🎯 Vision Globale

Intégrer OpenCV dans l'infrastructure MSI Decoder existante pour détecter et extraire les codes-barres **MSI 1D** que MLKit ne reconnaît pas, tout en préservant l'architecture Phase 0 validée.

## 🏗️ Architecture Générale

### Phase 0 + OpenCV Integration
```
┌─────────────────────────────────────────────────────────────────┐
│                    PHASE 0 EXISTANTE (PRÉSERVÉE)               │
├─────────────────────────────────────────────────────────────────┤
│ CameraX → ImageAnalysis → YuvToNv21Converter                   │
│     ↓                                                           │
│ PreviewView + MetricsOverlay + SnapshotManager                 │
│     ↓                                                           │
│ ScannerArbitrator (État Central)                               │
│     ↓                                                           │
│ ┌─────────────────────┐    ┌─────────────────────────────────┐  │
│ │   MLKit Scanner     │    │        NOUVEAU: OpenCV          │  │
│ │   (PRIORITAIRE)     │ ←→ │        MSI Pipeline             │  │
│ │                     │    │                                 │  │
│ │ • QR Code           │    │ • Détection générique 1D       │  │
│ │ • DataMatrix        │    │ • Extraction ROI                │  │
│ │ • EAN-13/8          │    │ • Binarisation adaptative      │  │
│ │ • Code-128          │    │ • Interface → T-106 Decoder    │  │
│ └─────────────────────┘    └─────────────────────────────────┘  │
│     ↓                                      ↓                   │
│ ScanResult.Success           ScanResult.Success (MSI)          │
│     ↓                                      ↓                   │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │        PHASE 0 RESULT PROCESSING (INCHANGÉ)                │ │
│ │ T-008 Coordinates → T-009 Overlay → State Persistence      │ │
│ │ Metrics Collection → Debug Snapshots → UI Display          │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 Flux de Données Détaillé

### 1. Source Commune CameraX
```kotlin
// Flux partagé depuis Phase 0 (INCHANGÉ)
CameraX ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        val nv21Data = yuvToNv21Converter.convert(image)
        val width = image.width
        val height = image.height  
        val rotation = image.imageInfo.rotationDegrees
        
        // Distribution vers arbitre central
        scannerArbitrator.processFrame(nv21Data, width, height, rotation) { result ->
            // Traitement résultat unifié
            handleScanResult(result)
        }
        
        image.close() // ✅ Phase 0 pattern preserved
    }
}
```

### 2. Arbitrage MLKit → OpenCV
```kotlin
class EnhancedScannerArbitrator {
    
    private val mlkitScanner = MLKitScanner()      // Phase 0
    private val openCVMsiScanner = OpenCVMsiScanner() // Nouveau
    
    fun processFrame(nv21Data: ByteArray, width: Int, height: Int, rotation: Int, callback: (ScanResult) -> Unit) {
        val resultDelivered = AtomicBoolean(false)
        
        // ÉTAPE 1: MLKit PRIORITAIRE (formats connus)
        mlkitScanner.scanFrame(nv21Data, width, height, rotation) { mlkitResult ->
            when (mlkitResult) {
                is ScanResult.Success -> {
                    // MLKit a trouvé un format supporté → livraison immédiate
                    if (resultDelivered.compareAndSet(false, true)) {
                        callback(mlkitResult)
                    }
                }
                
                is ScanResult.NoResult -> {
                    // MLKit n'a rien trouvé → tenter OpenCV MSI
                    tryOpenCVMsiDetection(nv21Data, width, height, rotation, callback, resultDelivered)
                }
                
                is ScanResult.Error -> {
                    // MLKit a échoué → fallback OpenCV
                    tryOpenCVMsiDetection(nv21Data, width, height, rotation, callback, resultDelivered)
                }
            }
        }
    }
    
    private fun tryOpenCVMsiDetection(
        nv21Data: ByteArray, width: Int, height: Int, rotation: Int,
        callback: (ScanResult) -> Unit, resultDelivered: AtomicBoolean
    ) {
        // Timeout protection: 45ms max pour respecter contrainte 50ms globale
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (resultDelivered.compareAndSet(false, true)) {
                callback(ScanResult.NoResult)
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 45L)
        
        try {
            openCVMsiScanner.scanFrame(nv21Data, width, height, rotation) { openCVResult ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                
                if (resultDelivered.compareAndSet(false, true)) {
                    callback(openCVResult)
                }
            }
        } catch (exception: Exception) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            if (resultDelivered.compareAndSet(false, true)) {
                callback(ScanResult.Error(exception, ScanSource.MSI))
            }
        }
    }
}
```

## 🧩 Composants OpenCV Nouveaux

### 1. BarcodeROIDetector
**Rôle** : Détection générique de structures codes-barres 1D
```kotlin
class BarcodeROIDetector {
    fun detectBarcodeROIs(nv21Data: ByteArray, width: Int, height: Int, rotation: Int): List<BarcodeROI>
}
```

### 2. ROIExtractor  
**Rôle** : Extraction et correction perspective des ROI détectées
```kotlin
class ROIExtractor {
    fun extractAndCorrectROI(sourceMat: Mat, roi: BarcodeROI): Mat?
}
```

### 3. BinarizationEngine
**Rôle** : Binarisation adaptative pour codes-barres MSI
```kotlin
class BinarizationEngine {
    fun binarizeForMSI(roiMat: Mat): Mat
}
```

### 4. OpenCVMsiScanner
**Rôle** : Scanner principal interface compatible Phase 0
```kotlin
class OpenCVMsiScanner : Scanner {
    override fun scanFrame(nv21Data: ByteArray, width: Int, height: Int, rotation: Int, callback: (ScanResult) -> Unit)
}
```

## 🔌 Points d'Intégration avec Phase 0

### 1. Interface Scanner Préservée
```kotlin
// Interface Phase 0 maintenue à l'identique
interface Scanner {
    fun scanFrame(
        nv21Data: ByteArray,
        width: Int, 
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    )
}

// OpenCVMsiScanner implémente cette interface
class OpenCVMsiScanner : Scanner {
    // Implémentation compatible
}
```

### 2. ScanResult Extensions
```kotlin
// Extension pour résultats MSI
data class ScanResult.Success(
    val data: String,
    val format: BarcodeFormat, // BarcodeFormat.MSI ajouté
    val source: ScanSource,    // ScanSource.MSI ajouté  
    val processingTimeMs: Long,
    val boundingBox: android.graphics.Rect? = null, // Compatible T-008
    val cornerPoints: Array<android.graphics.Point>? = null // Compatible T-008
)

enum class ScanSource {
    MLKIT,  // Existant Phase 0
    MSI     // Nouveau OpenCV
}

enum class BarcodeFormat {
    // Formats MLKit existants...
    QR_CODE, DATA_MATRIX, EAN_13, EAN_8, CODE_128,
    
    // Nouveau format MSI
    MSI
}
```

### 3. Métriques Intégrées
```kotlin
// Extension MetricsCollector pour OpenCV
class MetricsCollector {
    // Métriques Phase 0 existantes
    var mlkitSuccessCount = 0
    var mlkitProcessingTimeMs = 0L
    
    // Nouvelles métriques OpenCV
    var openCVDetectionCount = 0
    var openCVSuccessCount = 0
    var openCVProcessingTimeMs = 0L
    var roiCandidatesFound = 0
    var binarizationTimeMs = 0L
    
    fun getOpenCVStats(): OpenCVStats {
        return OpenCVStats(
            detectionAttempts = openCVDetectionCount,
            successfulDecodes = openCVSuccessCount,
            averageProcessingTime = if (openCVDetectionCount > 0) openCVProcessingTimeMs / openCVDetectionCount else 0L,
            averageROICandidates = if (openCVDetectionCount > 0) roiCandidatesFound / openCVDetectionCount else 0
        )
    }
}
```

## ⚙️ Configuration et Feature Flags

### 1. Configuration Runtime
```kotlin
data class OpenCVConfig(
    val enabled: Boolean = true,
    val timeoutMs: Long = 45L,
    val maxROICandidates: Int = 3,
    val minConfidenceThreshold: Float = 0.6f,
    val enableDebugLogging: Boolean = false
)

object ConfigManager {
    private var openCVConfig = OpenCVConfig()
    
    fun updateOpenCVConfig(config: OpenCVConfig) {
        openCVConfig = config
        // Appliquer aux composants OpenCV
    }
    
    fun isOpenCVEnabled(): Boolean = openCVConfig.enabled
}
```

### 2. A/B Testing Ready
```kotlin
enum class ScannerMode {
    PHASE_0_ONLY,     // MLKit uniquement (fallback)
    PHASE_1_OPENCV,   // MLKit + OpenCV MSI (production)
    DEBUG_PARALLEL    // MLKit + OpenCV en parallèle (comparaison)
}

class ScannerModeController {
    private var currentMode = ScannerMode.PHASE_1_OPENCV
    
    fun setScannerMode(mode: ScannerMode) {
        currentMode = mode
        // Reconfigurer ScannerArbitrator selon le mode
    }
}
```

## 🎯 Avantages Architecture

### ✅ Phase 0 Intacte
- **Aucun changement** dans CameraX setup
- **Interface Scanner préservée**
- **Métriques et debug compatibles**
- **État et persistance inchangés**

### ✅ Intégration Harmonieuse  
- **Priorité MLKit maintenue** (performance éprouvée)
- **OpenCV en fallback** (pas de conflit)
- **Timeout strict** (pas d'impact performance globale)
- **Fallback gracieux** (robustesse garantie)

### ✅ Extensibilité Future
- **Architecture modulaire** (ajout autres formats facile)
- **Configuration runtime** (tuning production)
- **A/B testing ready** (déploiement progressif)
- **Métriques séparées** (monitoring indépendant)

## 📊 Timeline d'Implémentation

| Étape | Composant | Durée Estimée | Dépendances |
|-------|-----------|---------------|-------------|
| 1 | Setup OpenCV Android SDK | 1 jour | - |
| 2 | BarcodeROIDetector | 2-3 jours | Setup OpenCV |
| 3 | ROIExtractor + Binarization | 2 jours | ROI Detection |
| 4 | OpenCVMsiScanner | 1 jour | ROI + Binarization |
| 5 | ScannerArbitrator Integration | 1 jour | OpenCV Scanner |
| 6 | Tests + Optimisation | 2-3 jours | Integration complète |

**Total** : **9-11 jours** pour intégration complète OpenCV dans Phase 0

---

**🎯 Cette architecture garantit une intégration OpenCV transparente et performante, étendant les capacités MSI Decoder sans compromettre la stabilité Phase 0 éprouvée.**