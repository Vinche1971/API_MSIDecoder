# 05 - Optimisations Performance OpenCV Android

## 🎯 Objectif

Optimiser les performances OpenCV pour respecter les **contraintes strictes** du projet MSI Decoder : **50ms max par frame**, **mémoire limitée**, et **batterie préservée**, tout en maintenant la qualité de détection des codes MSI.

## ⚡ Contraintes Performance Critiques

### Contraintes Temps Réel
```
Budget Temps Total: 50ms/frame
├── MLKit Path (prioritaire): ~15ms
├── OpenCV Path (fallback): 45ms MAX
│   ├── NV21→Mat conversion: 2ms
│   ├── ROI detection: 25ms
│   ├── ROI extraction: 8ms  
│   ├── Binarisation: 5ms
│   └── MSI decode attempt: 5ms
└── Result processing: 5ms
```

### Contraintes Mémoire
- **Heap increase max** : 10MB au-dessus baseline Phase 0
- **Mat cache** : 5 Mat max réutilisables
- **Memory leaks** : Zero tolérance (cleanup automatique)
- **GC pressure** : Minimiser allocations temporaires

## 🚀 Optimisations Techniques

### 1. Conversion NV21→Mat Ultra-Optimisée
```kotlin
object FastNV21Converter {
    
    // Cache buffer réutilisable pour éviter allocations répétées
    private val threadLocalBuffer = ThreadLocal.withInitial { 
        ByteArray(640 * 480) // Taille max courante
    }
    
    /**
     * Conversion NV21 → Mat optimisée avec buffer cache
     * Target: <2ms pour 640×480
     */
    fun fastNV21ToGrayMat(nv21Data: ByteArray, width: Int, height: Int): Mat {
        val yPlaneSize = width * height
        
        // Validation rapide
        if (nv21Data.size < yPlaneSize) {
            throw IllegalArgumentException("Invalid NV21 size")
        }
        
        // Réutilisation buffer thread-local pour éviter allocation
        val buffer = threadLocalBuffer.get()
        if (buffer.size < yPlaneSize) {
            threadLocalBuffer.set(ByteArray(yPlaneSize))
        }
        
        // Copie directe plan Y (plus rapide que sliceArray)
        System.arraycopy(nv21Data, 0, buffer, 0, yPlaneSize)
        
        // Création Mat avec buffer direct
        val grayMat = Mat(height, width, CvType.CV_8UC1)
        grayMat.put(0, 0, buffer, 0, yPlaneSize)
        
        return grayMat
    }
    
    /**
     * Conversion avec downscaling pour performance (si résolution élevée)
     */
    fun fastNV21ToGrayMatDownscaled(
        nv21Data: ByteArray, 
        width: Int, 
        height: Int,
        targetWidth: Int = 320,
        targetHeight: Int = 240
    ): Mat {
        // Si résolution déjà petite, pas de downscaling
        if (width <= targetWidth || height <= targetHeight) {
            return fastNV21ToGrayMat(nv21Data, width, height)
        }
        
        // Conversion standard puis resize
        val originalMat = fastNV21ToGrayMat(nv21Data, width, height)
        val resized = Mat()
        
        Imgproc.resize(
            originalMat, resized,
            Size(targetWidth.toDouble(), targetHeight.toDouble()),
            0.0, 0.0, Imgproc.INTER_LINEAR  // INTER_LINEAR plus rapide que CUBIC
        )
        
        originalMat.release()
        return resized
    }
}
```

### 2. Mat Pool Manager Ultra-Efficace
```kotlin
class HighPerformanceMatPool private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: HighPerformanceMatPool? = null
        
        fun getInstance(): HighPerformanceMatPool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HighPerformanceMatPool().also { INSTANCE = it }
            }
        }
    }
    
    // Pools séparés par taille pour réutilisation optimale
    private val pools = mutableMapOf<String, ArrayDeque<Mat>>()
    private val maxPoolSize = 3  // Limité pour éviter memory bloat
    private val lockObject = Object()
    
    /**
     * Récupération Mat du pool (ou création si nécessaire)
     */
    fun borrowMat(width: Int, height: Int, type: Int): Mat {
        val key = "${width}x${height}_$type"
        
        synchronized(lockObject) {
            val pool = pools.getOrPut(key) { ArrayDeque() }
            
            return if (pool.isNotEmpty()) {
                val reusedMat = pool.removeFirst()
                reusedMat.setTo(Scalar.all(0.0))  // Reset rapide
                reusedMat
            } else {
                Mat(height, width, type)  // Nouvelle allocation si pool vide
            }
        }
    }
    
    /**
     * Retour Mat au pool pour réutilisation
     */
    fun returnMat(mat: Mat, width: Int, height: Int, type: Int) {
        if (mat.empty()) return
        
        val key = "${width}x${height}_$type"
        
        synchronized(lockObject) {
            val pool = pools.getOrPut(key) { ArrayDeque() }
            
            if (pool.size < maxPoolSize) {
                pool.addLast(mat)
            } else {
                mat.release()  // Pool plein → release
            }
        }
    }
    
    /**
     * Nettoyage complet (appelé onDestroy)
     */
    fun cleanup() {
        synchronized(lockObject) {
            pools.values.forEach { pool ->
                while (pool.isNotEmpty()) {
                    pool.removeFirst().release()
                }
            }
            pools.clear()
        }
    }
    
    /**
     * Statistiques pool pour monitoring
     */
    fun getPoolStats(): Map<String, Int> {
        synchronized(lockObject) {
            return pools.mapValues { it.value.size }
        }
    }
}
```

### 3. Pipeline ROI Detection Optimisé
```kotlin
class OptimizedBarcodeROIDetector {
    
    companion object {
        // Paramètres optimisés performance/qualité
        private const val FAST_GRADIENT_KERNEL = 3     // Sobel 3x3 plus rapide que 5x5
        private val FAST_MORPHO_KERNEL = Size(15.0, 5.0) // Kernel réduit pour vitesse
        private const val MAX_CONTOURS_PROCESS = 10    // Limite contours traités
        private const val MIN_CONTOUR_AREA_FAST = 800  // Seuil plus élevé pour filtrage rapide
    }
    
    private val matPool = HighPerformanceMatPool.getInstance()
    
    /**
     * Détection ROI optimisée performance - version allégée
     */
    fun detectBarcodeROIsFast(grayMat: Mat): List<BarcodeROI> {
        val candidates = mutableListOf<BarcodeROI>()
        
        try {
            // OPTIMISATION 1: Downscale pour détection rapide si image grande
            val workingMat = if (grayMat.width() > 400 || grayMat.height() > 300) {
                val downscaled = matPool.borrowMat(320, 240, CvType.CV_8UC1)
                Imgproc.resize(grayMat, downscaled, Size(320.0, 240.0), 0.0, 0.0, Imgproc.INTER_LINEAR)
                downscaled
            } else {
                grayMat
            }
            
            // OPTIMISATION 2: Gradient rapide (kernel 3x3)
            val gradX = matPool.borrowMat(workingMat.width(), workingMat.height(), CvType.CV_16S)
            Imgproc.Sobel(workingMat, gradX, CvType.CV_16S, 1, 0, FAST_GRADIENT_KERNEL)
            
            val absGradX = matPool.borrowMat(workingMat.width(), workingMat.height(), CvType.CV_8UC1)
            Core.convertScaleAbs(gradX, absGradX)
            
            // OPTIMISATION 3: Seuillage simple (pas d'orientation complexe)
            val thresh = matPool.borrowMat(workingMat.width(), workingMat.height(), CvType.CV_8UC1)
            Imgproc.threshold(absGradX, thresh, 40.0, 255.0, Imgproc.THRESH_BINARY)
            
            // OPTIMISATION 4: Morphologie réduite
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, FAST_MORPHO_KERNEL)
            Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, kernel)
            
            // OPTIMISATION 5: Contours avec limite processing
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = matPool.borrowMat(1, 1, CvType.CV_8UC1) // Dummy pour hierarchy
            
            Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            // OPTIMISATION 6: Tri rapide par aire et traitement limité
            val sortedContours = contours
                .filter { Imgproc.contourArea(it) >= MIN_CONTOUR_AREA_FAST }
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(MAX_CONTOURS_PROCESS)
            
            // Validation géométrique simplifiée
            for ((index, contour) in sortedContours.withIndex()) {
                val boundingRect = Imgproc.boundingRect(contour)
                
                // Validation rapide
                val aspectRatio = boundingRect.width.toDouble() / boundingRect.height
                if (aspectRatio >= 2.5 && boundingRect.width >= 40) {
                    
                    // Calcul confiance simple
                    val area = Imgproc.contourArea(contour)
                    val rectArea = boundingRect.width * boundingRect.height
                    val density = area / rectArea
                    val confidence = (aspectRatio / 10.0 + density).coerceAtMost(1.0)
                    
                    // Remapping coordonnées si downscaled
                    val finalRect = if (workingMat !== grayMat) {
                        val scaleX = grayMat.width().toDouble() / workingMat.width()
                        val scaleY = grayMat.height().toDouble() / workingMat.height()
                        Rect(
                            (boundingRect.x * scaleX).toInt(),
                            (boundingRect.y * scaleY).toInt(),
                            (boundingRect.width * scaleX).toInt(),
                            (boundingRect.height * scaleY).toInt()
                        )
                    } else {
                        boundingRect
                    }
                    
                    candidates.add(
                        BarcodeROI(
                            rect = finalRect,
                            orientation = BarcodeOrientation.HORIZONTAL,
                            confidence = confidence,
                            contourArea = area,
                            aspectRatio = aspectRatio
                        )
                    )
                }
            }
            
            // Nettoyage rapide
            if (workingMat !== grayMat) {
                matPool.returnMat(workingMat, 320, 240, CvType.CV_8UC1)
            }
            matPool.returnMat(gradX, workingMat.width(), workingMat.height(), CvType.CV_16S)
            matPool.returnMat(absGradX, workingMat.width(), workingMat.height(), CvType.CV_8UC1)
            matPool.returnMat(thresh, workingMat.width(), workingMat.height(), CvType.CV_8UC1)
            matPool.returnMat(hierarchy, 1, 1, CvType.CV_8UC1)
            kernel.release()
            contours.forEach { it.release() }
            
        } catch (exception: Exception) {
            Log.e(TAG, "Fast ROI detection error", exception)
        }
        
        return candidates.take(2)  // Max 2 ROI pour performance
    }
}
```

### 4. Binarisation Performance-Optimisée
```kotlin
class FastBinarization {
    
    /**
     * Binarisation rapide avec méthode unique (pas de multi-méthodes)
     * Target: <3ms pour ROI 200×60
     */
    fun fastBinarizeROI(roiMat: Mat): Mat? {
        try {
            // Méthode unique: Otsu si contraste suffisant, sinon adaptatif simple
            val mean = Core.mean(roiMat).`val`[0]
            val stddev = MatOfDouble()
            Core.meanStdDev(roiMat, MatOfDouble(), stddev)
            val std = stddev.get(0, 0)[0]
            stddev.release()
            
            val binary = Mat()
            
            if (std > 25.0) {
                // Bon contraste → Otsu rapide
                Imgproc.threshold(roiMat, binary, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            } else {
                // Faible contraste → Adaptatif simple
                Imgproc.adaptiveThreshold(
                    roiMat, binary, 255.0,
                    Imgproc.ADAPTIVE_THRESH_MEAN_C,
                    Imgproc.THRESH_BINARY,
                    9,    // Block size réduit pour vitesse
                    2.0   // C constant réduit
                )
            }
            
            return binary
            
        } catch (exception: Exception) {
            Log.e(TAG, "Fast binarization error", exception)
            return null
        }
    }
}
```

### 5. Memory Management Patterns
```kotlin
/**
 * Pattern RAII pour gestion automatique mémoire OpenCV
 */
inline fun <T> useMat(mat: Mat, block: (Mat) -> T): T {
    return try {
        block(mat)
    } finally {
        if (!mat.empty()) {
            mat.release()
        }
    }
}

inline fun <T> useMatPool(width: Int, height: Int, type: Int, block: (Mat) -> T): T {
    val pool = HighPerformanceMatPool.getInstance()
    val mat = pool.borrowMat(width, height, type)
    return try {
        block(mat)
    } finally {
        pool.returnMat(mat, width, height, type)
    }
}

// Usage exemple:
fun processROI(nv21Data: ByteArray, width: Int, height: Int): BarcodeROI? {
    return useMat(FastNV21Converter.fastNV21ToGrayMat(nv21Data, width, height)) { grayMat ->
        useMatPool(width, height, CvType.CV_8UC1) { workingMat ->
            // Processing logic ici
            // Cleanup automatique
            detectROI(grayMat, workingMat)
        }
    }
}
```

## 📊 Monitoring Performance

### 1. Performance Profiler Intégré
```kotlin
class OpenCVPerformanceProfiler {
    
    data class PerformanceMetrics(
        val avgConversionTimeMs: Double = 0.0,
        val avgDetectionTimeMs: Double = 0.0,
        val avgBinarizationTimeMs: Double = 0.0,
        val avgTotalTimeMs: Double = 0.0,
        val memoryUsageMB: Double = 0.0,
        val successRate: Double = 0.0,
        val frameCount: Int = 0
    )
    
    private val conversionTimes = mutableListOf<Long>()
    private val detectionTimes = mutableListOf<Long>()
    private val binarizationTimes = mutableListOf<Long>()
    private val totalTimes = mutableListOf<Long>()
    
    private var successCount = 0
    private var totalAttempts = 0
    private val maxSamples = 100  // Limite historique pour éviter memory bloat
    
    fun recordConversion(timeMs: Long) {
        synchronized(this) {
            conversionTimes.add(timeMs)
            if (conversionTimes.size > maxSamples) {
                conversionTimes.removeFirst()
            }
        }
    }
    
    fun recordDetection(timeMs: Long) {
        synchronized(this) {
            detectionTimes.add(timeMs)
            if (detectionTimes.size > maxSamples) {
                detectionTimes.removeFirst()
            }
        }
    }
    
    fun recordSuccess() {
        synchronized(this) {
            successCount++
            totalAttempts++
        }
    }
    
    fun recordFailure() {
        synchronized(this) {
            totalAttempts++
        }
    }
    
    fun getCurrentMetrics(): PerformanceMetrics {
        synchronized(this) {
            val runtime = Runtime.getRuntime()
            val memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
            
            return PerformanceMetrics(
                avgConversionTimeMs = conversionTimes.average().takeIf { !it.isNaN() } ?: 0.0,
                avgDetectionTimeMs = detectionTimes.average().takeIf { !it.isNaN() } ?: 0.0,
                avgTotalTimeMs = totalTimes.average().takeIf { !it.isNaN() } ?: 0.0,
                memoryUsageMB = memoryUsed,
                successRate = if (totalAttempts > 0) successCount.toDouble() / totalAttempts else 0.0,
                frameCount = totalAttempts
            )
        }
    }
    
    fun shouldOptimize(): Boolean {
        val metrics = getCurrentMetrics()
        return metrics.avgTotalTimeMs > 40.0 ||  // Approaching timeout
               metrics.memoryUsageMB > 100.0 ||  // High memory usage
               metrics.successRate < 0.6         // Low success rate
    }
}
```

### 2. Configuration Adaptative Performance
```kotlin
class AdaptivePerformanceConfig {
    
    data class PerformanceLevel(
        val maxROICandidates: Int,
        val downscaleResolution: Boolean,
        val simplifiedBinarization: Boolean,
        val reducedMorphologyKernel: Boolean
    )
    
    companion object {
        val HIGH_PERFORMANCE = PerformanceLevel(
            maxROICandidates = 3,
            downscaleResolution = false,
            simplifiedBinarization = false,
            reducedMorphologyKernel = false
        )
        
        val BALANCED = PerformanceLevel(
            maxROICandidates = 2,
            downscaleResolution = true,
            simplifiedBinarization = false,
            reducedMorphologyKernel = true
        )
        
        val FAST = PerformanceLevel(
            maxROICandidates = 1,
            downscaleResolution = true,
            simplifiedBinarization = true,
            reducedMorphologyKernel = true
        )
    }
    
    private var currentLevel = BALANCED
    private val profiler = OpenCVPerformanceProfiler()
    
    fun adaptConfiguration(): PerformanceLevel {
        if (profiler.shouldOptimize()) {
            currentLevel = when (currentLevel) {
                HIGH_PERFORMANCE -> BALANCED
                BALANCED -> FAST
                FAST -> FAST  // Already at minimum
            }
            Log.d(TAG, "Performance level adapted to: $currentLevel")
        }
        
        return currentLevel
    }
}
```

## 🔧 Build Configuration Optimisé

### Gradle Optimizations
```gradle
android {
    // ... existing config
    
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libopenmp.so'
        
        // Exclusions pour réduire APK size
        exclude '**/libopencv_java4.so'  // Si utilisant OpenCV Manager
        exclude '**/arm64-v8a/libopencv_*.so'  // Si pas nécessaire ARM64
    }
    
    // Optimisations compilation
    buildTypes {
        debug {
            minifyEnabled false
            shrinkResources false
            debuggable true
            
            // Debug OpenCV
            buildConfigField "boolean", "OPENCV_DEBUG", "true"
            buildConfigField "int", "OPENCV_CACHE_SIZE", "5"
        }
        
        release {
            minifyEnabled true
            shrinkResources true
            
            // Production OpenCV
            buildConfigField "boolean", "OPENCV_DEBUG", "false"
            buildConfigField "int", "OPENCV_CACHE_SIZE", "3"
            
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    // Native optimizations
    externalNativeBuild {
        cmake {
            arguments "-DANDROID_STL=c++_shared",
                     "-DANDROID_ARM_NEON=TRUE",
                     "-DCMAKE_BUILD_TYPE=Release"
        }
    }
}

dependencies {
    // OpenCV optimisé pour release size
    implementation('org.opencv:opencv-android:4.8.0') {
        exclude group: 'org.opencv', module: 'opencv-contrib'  // Si contrib pas nécessaire
    }
}
```

### ProGuard Rules pour OpenCV
```proguard
# proguard-rules.pro

# OpenCV
-keep class org.opencv.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Performance classes
-keep class com.msidecoder.opencv.** { *; }
-keepclassmembers class com.msidecoder.opencv.** {
    public <methods>;
}

# Optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
```

## 🎯 Benchmarks Performance Cibles

### Temps Processing (640×480)
| Opération | Temps Cible | Temps Acceptable | Temps Critique |
|-----------|-------------|------------------|----------------|
| NV21→Mat | <2ms | <3ms | <5ms |
| ROI Detection | <20ms | <25ms | <30ms |
| ROI Extraction | <5ms | <8ms | <10ms |
| Binarisation | <3ms | <5ms | <7ms |
| **TOTAL OpenCV** | **<30ms** | **<40ms** | **<45ms** |

### Utilisation Mémoire
- **Baseline** (sans OpenCV) : ~15MB
- **Target** (avec OpenCV) : <25MB (+10MB)
- **Acceptable** : <30MB (+15MB)  
- **Critique** : >35MB (optimisation requise)

### Success Rate
- **Target** : >85% codes MSI détectés dans conditions normales
- **Acceptable** : >75% 
- **Critique** : <60% (revoir paramètres détection)

---

**🎯 Ces optimisations garantissent que l'intégration OpenCV respecte les contraintes performance strictes tout en maximisant les chances de détection des codes MSI dans le budget temps alloué.**