# T-101 - OpenCV Setup & Integration Android

## 🎯 Objectif
Intégrer OpenCV Android SDK de manière optimale dans l'infrastructure Phase 0 existante, établir les patterns de conversion NV21↔Mat performants, et valider la baseline pour les phases suivantes.

## 📋 Setup OpenCV Android

### Méthode d'Intégration Recommandée

#### Option A : OpenCV Manager (Recommandée pour dev)
**Avantages** :
- ✅ APK size minimal (~2MB vs 50MB)
- ✅ Updates OpenCV automatiques via Play Store
- ✅ Shared entre applications
- ✅ Setup simple développement

**Inconvénients** :
- ❌ Dépendance externe (OpenCV Manager app)
- ❌ Risk si user n'installe pas OpenCV Manager

#### Option B : Static Import (Production recommandée)
**Avantages** :
- ✅ Self-contained, aucune dépendance
- ✅ Contrôle version OpenCV exacte  
- ✅ Déploiement simplifié

**Inconvénients** :
- ❌ APK size +40-50MB
- ❌ Updates manuelles OpenCV

### Setup Gradle Configuration
```gradle
// app/build.gradle
android {
    compileSdkVersion 34
    
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'
        }
    }
    
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libopenmp.so'
    }
}

dependencies {
    // Option A: OpenCV Manager
    implementation 'org.opencv:opencv-android:4.8.0'
    
    // Option B: Static (uncomment for production)
    // implementation project(':opencv')
}
```

### OpenCV Module Import (Option B)
```bash
# Download OpenCV Android SDK 4.8.0
wget https://github.com/opencv/opencv/releases/download/4.8.0/opencv-4.8.0-android-sdk.zip
unzip opencv-4.8.0-android-sdk.zip

# Import dans Android Studio
# File → Import Module → opencv/java
# settings.gradle: include ':opencv'
```

## 🔧 Code Integration

### OpenCV Initialization
```kotlin
// Application.kt ou MainActivity.kt
class MSIDecoderApplication : Application() {
    
    companion object {
        private const val TAG = "MSIDecoderApp"
        
        // OpenCV loader callback
        private val openCVLoaderCallback = object : BaseLoaderCallback(this) {
            override fun onManagerConnected(status: Int) {
                when (status) {
                    LoaderCallbackInterface.SUCCESS -> {
                        Log.d(TAG, "OpenCV loaded successfully")
                        OpenCVNativeHelper.initializeNative()
                    }
                    else -> {
                        super.onManagerConnected(status)
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        // OpenCV will be loaded in onResume() of activities
    }
}

// MainActivity.kt
class MainActivity : AppCompatActivity() {
    
    override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback)
        } else {
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }
}
```

### NV21 ↔ Mat Conversion Optimized
```kotlin
/**
 * High-performance NV21 to OpenCV Mat conversion utility
 */
object OpenCVNativeHelper {
    
    private var isNativeInitialized = false
    
    @JvmStatic
    external fun nv21ToMat(nv21Data: ByteArray, width: Int, height: Int, matAddr: Long): Boolean
    
    @JvmStatic  
    external fun matToNv21(matAddr: Long, nv21Data: ByteArray): Boolean
    
    init {
        System.loadLibrary("opencv_java4")
        System.loadLibrary("msi_opencv_native") // Our native helpers
    }
    
    fun initializeNative() {
        isNativeInitialized = true
        Log.d("OpenCVHelper", "Native OpenCV helpers initialized")
    }
    
    /**
     * Convert NV21 byte array to OpenCV Mat (grayscale)
     * Optimized for barcode processing - grayscale only
     */
    fun nv21ToGrayMat(nv21Data: ByteArray, width: Int, height: Int): Mat {
        if (!isNativeInitialized) {
            throw IllegalStateException("OpenCV native not initialized")
        }
        
        val mat = Mat(height + height / 2, width, CvType.CV_8UC1)
        mat.put(0, 0, nv21Data)
        
        val grayMat = Mat(height, width, CvType.CV_8UC1)
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_YUV2GRAY_NV21)
        
        mat.release() // Important: release intermediate mat
        return grayMat
    }
    
    /**
     * Convert NV21 to RGB Mat (if color processing needed)  
     */
    fun nv21ToRgbMat(nv21Data: ByteArray, width: Int, height: Int): Mat {
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21Data)
        
        val rgbMat = Mat(height, width, CvType.CV_8UC3)
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        
        yuvMat.release()
        return rgbMat
    }
    
    /**
     * Memory-efficient Mat cleanup
     */
    fun releaseMat(vararg mats: Mat?) {
        mats.forEach { mat ->
            mat?.let {
                if (!it.empty()) {
                    it.release()
                }
            }
        }
    }
}
```

### Native Code Helpers (Optional Optimization)
```cpp
// jni/opencv_helpers.cpp
#include <opencv2/opencv.hpp>
#include <android/log.h>

using namespace cv;

extern "C" {
    
JNIEXPORT jboolean JNICALL
Java_com_msidecoder_scanner_opencv_OpenCVNativeHelper_nv21ToMat(
    JNIEnv *env, jclass clazz,
    jbyteArray nv21_data, jint width, jint height, jlong mat_addr) {
    
    try {
        Mat* mat = (Mat*)mat_addr;
        jbyte* nv21_ptr = env->GetByteArrayElements(nv21_data, 0);
        
        // Direct memory copy for performance
        Mat yuv(height + height/2, width, CV_8UC1, nv21_ptr);
        cvtColor(yuv, *mat, COLOR_YUV2GRAY_NV21);
        
        env->ReleaseByteArrayElements(nv21_data, nv21_ptr, JNI_ABORT);
        return JNI_TRUE;
        
    } catch (const cv::Exception& e) {
        __android_log_print(ANDROID_LOG_ERROR, "OpenCVHelper", "NV21 conversion error: %s", e.what());
        return JNI_FALSE;
    }
}

} // extern "C"
```

## 📊 Performance Baseline & Benchmarking

### Benchmark Suite
```kotlin
class OpenCVPerformanceBenchmark {
    
    companion object {
        private const val TAG = "OpenCVBenchmark"
        private const val BENCHMARK_ITERATIONS = 100
    }
    
    fun benchmarkNV21Conversion(nv21Data: ByteArray, width: Int, height: Int) {
        val times = mutableListOf<Long>()
        
        repeat(BENCHMARK_ITERATIONS) {
            val startTime = System.nanoTime()
            val mat = OpenCVNativeHelper.nv21ToGrayMat(nv21Data, width, height)
            val endTime = System.nanoTime()
            
            OpenCVNativeHelper.releaseMat(mat)
            times.add(endTime - startTime)
        }
        
        val avgTimeNs = times.average()
        val avgTimeMs = avgTimeNs / 1_000_000.0
        
        Log.d(TAG, "NV21→Mat conversion: avg=${avgTimeMs}ms, min=${times.minOrNull()!! / 1_000_000.0}ms, max=${times.maxOrNull()!! / 1_000_000.0}ms")
    }
    
    fun benchmarkBasicOperations(inputMat: Mat) {
        val results = mutableMapOf<String, Double>()
        
        // Gaussian Blur
        val blurred = Mat()
        val startBlur = System.nanoTime()
        Imgproc.GaussianBlur(inputMat, blurred, Size(5.0, 5.0), 0.0)
        results["GaussianBlur"] = (System.nanoTime() - startBlur) / 1_000_000.0
        
        // Sobel gradients
        val sobelX = Mat()
        val sobelY = Mat()
        val startSobel = System.nanoTime()
        Imgproc.Sobel(inputMat, sobelX, CvType.CV_16S, 1, 0, 3)
        Imgproc.Sobel(inputMat, sobelY, CvType.CV_16S, 0, 1, 3)
        results["Sobel"] = (System.nanoTime() - startSobel) / 1_000_000.0
        
        // Morphological operations
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(21.0, 7.0))
        val morphed = Mat()
        val startMorph = System.nanoTime()
        Imgproc.morphologyEx(inputMat, morphed, Imgproc.MORPH_CLOSE, kernel)
        results["Morphology"] = (System.nanoTime() - startMorph) / 1_000_000.0
        
        // Adaptive threshold  
        val binary = Mat()
        val startThresh = System.nanoTime()
        Imgproc.adaptiveThreshold(inputMat, binary, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, -2.0)
        results["AdaptiveThreshold"] = (System.nanoTime() - startThresh) / 1_000_000.0
        
        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        val startContours = System.nanoTime()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        results["FindContours"] = (System.nanoTime() - startContours) / 1_000_000.0
        
        // Log results
        results.forEach { (operation, timeMs) ->
            Log.d(TAG, "$operation: ${timeMs}ms")
        }
        
        // Cleanup
        OpenCVNativeHelper.releaseMat(blurred, sobelX, sobelY, morphed, binary, hierarchy)
        contours.forEach { it.release() }
    }
}
```

### Memory Management Patterns
```kotlin
class OpenCVMemoryManager {
    
    private val matPool = mutableMapOf<String, MutableList<Mat>>()
    private var totalAllocatedMats = 0
    
    /**
     * Get reusable Mat from pool or create new one
     */
    fun getMat(width: Int, height: Int, type: Int, poolKey: String = "default"): Mat {
        val pool = matPool.getOrPut(poolKey) { mutableListOf() }
        
        // Try to reuse existing Mat with same dimensions
        val reusable = pool.find { mat ->
            !mat.empty() && mat.width() == width && mat.height() == height && mat.type() == type
        }
        
        return if (reusable != null) {
            pool.remove(reusable)
            reusable
        } else {
            totalAllocatedMats++
            Mat(height, width, type)
        }
    }
    
    /**
     * Return Mat to pool for reuse
     */
    fun returnMat(mat: Mat, poolKey: String = "default") {
        if (!mat.empty()) {
            val pool = matPool.getOrPut(poolKey) { mutableListOf() }
            pool.add(mat)
        }
    }
    
    /**
     * Clean up all pooled Mats
     */
    fun cleanup() {
        matPool.values.forEach { pool ->
            pool.forEach { mat -> 
                if (!mat.empty()) mat.release() 
            }
            pool.clear()
        }
        matPool.clear()
        Log.d("MemoryManager", "Cleaned up $totalAllocatedMats allocated Mats")
        totalAllocatedMats = 0
    }
}
```

## 🎯 Integration with Phase 0

### Scanner Interface Compatibility
```kotlin
// Maintain Phase 0 Scanner interface
class OpenCVPreviewScanner : Scanner {
    
    private val memoryManager = OpenCVMemoryManager()
    private val benchmark = OpenCVPerformanceBenchmark()
    
    override fun scanFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Step 1: Convert to OpenCV Mat
            val grayMat = OpenCVNativeHelper.nv21ToGrayMat(nv21Data, width, height)
            
            // Step 2: Basic OpenCV processing (placeholder for T-102)
            val processedMat = preprocessForTesting(grayMat)
            
            // Step 3: Extract basic info for testing
            val nonZeroPixels = Core.countNonZero(processedMat)
            val totalPixels = processedMat.total()
            val density = nonZeroPixels.toDouble() / totalPixels
            
            val processingTime = System.currentTimeMillis() - startTime
            
            // Step 4: Return test result
            val result = if (density > 0.1) { // Placeholder threshold
                ScanResult.Success(
                    data = "OpenCV-Test-${System.currentTimeMillis()}",
                    format = "OPENCV_TEST",
                    source = ScanSource.MSI,
                    processingTimeMs = processingTime
                )
            } else {
                ScanResult.NoResult
            }
            
            callback(result)
            
            // Cleanup
            OpenCVNativeHelper.releaseMat(grayMat, processedMat)
            
        } catch (exception: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e("OpenCVPreview", "OpenCV processing failed", exception)
            callback(ScanResult.Error(exception, ScanSource.MSI))
        }
    }
    
    private fun preprocessForTesting(inputMat: Mat): Mat {
        val blurred = memoryManager.getMat(inputMat.width(), inputMat.height(), inputMat.type())
        Imgproc.GaussianBlur(inputMat, blurred, Size(5.0, 5.0), 0.0)
        return blurred
    }
    
    fun cleanup() {
        memoryManager.cleanup()
    }
}
```

### ScannerArbitrator Integration
```kotlin
// Extend existing ScannerArbitrator for OpenCV testing
class ScannerArbitrator {
    
    private val mlkitScanner = MLKitScanner()
    private val openCVPreviewScanner = OpenCVPreviewScanner()  // T-101: Testing only
    
    private var useOpenCVTesting = false  // Feature flag
    
    fun enableOpenCVTesting(enable: Boolean) {
        useOpenCVTesting = enable
        Log.d(TAG, "OpenCV testing mode: $enable")
    }
    
    fun processFrame(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        callback: (ScanResult) -> Unit
    ) {
        val resultDelivered = AtomicBoolean(false)
        
        if (useOpenCVTesting) {
            // T-101: OpenCV testing path
            openCVPreviewScanner.scanFrame(nv21Data, width, height, rotationDegrees) { openCVResult ->
                if (resultDelivered.compareAndSet(false, true)) {
                    callback(openCVResult)
                }
            }
        } else {
            // Phase 0: Original MLKit path (temporarily commented out for OpenCV focus)
            callback(ScanResult.NoResult)
        }
    }
}
```

## 🎯 Critères d'Acceptation T-101

### Setup & Build
- ✅ **OpenCV SDK intégré** : Build réussit sans erreurs
- ✅ **APK size acceptable** : <5MB increase pour dev, <50MB pour prod
- ✅ **Target devices compatibility** : ARM64 + ARMv7 minimum
- ✅ **No crashes** : App lance et OpenCV s'initialise correctement

### Performance Baseline
- ✅ **NV21→Mat conversion** : <5ms pour 640×480
- ✅ **Basic operations** : Blur + Sobel + Morph + Threshold <20ms total
- ✅ **Memory usage** : <5MB heap increase
- ✅ **No memory leaks** : Mat cleanup correct

### Integration  
- ✅ **Scanner interface preserved** : Phase 0 compatibility
- ✅ **Feature flag ready** : OpenCV activable/désactivable
- ✅ **Debug logging** : Performance metrics logged
- ✅ **Error handling** : Graceful fallback si OpenCV échoue

### Testing
- ✅ **Unit tests** : Conversion + memory management
- ✅ **Performance benchmarks** : Baseline établie
- ✅ **Device testing** : 3+ devices Android différents
- ✅ **Memory profiling** : Pas de leaks sur 30min utilisation

## 📊 Livrables T-101

### Code
- ✅ **OpenCVNativeHelper class** : Conversions optimisées
- ✅ **OpenCVMemoryManager class** : Mat pooling et cleanup
- ✅ **OpenCVPreviewScanner class** : Interface Scanner compatible
- ✅ **Performance benchmark suite** : Métriques baseline

### Configuration
- ✅ **Gradle setup** : OpenCV dependencies
- ✅ **Native code** : JNI helpers (optional)
- ✅ **Feature flags** : OpenCV enable/disable
- ✅ **Logging configuration** : Debug modes

### Documentation
- ✅ **Setup instructions** : Step-by-step integration
- ✅ **Performance baselines** : Benchmarks reference
- ✅ **Memory management** : Best practices documented
- ✅ **Troubleshooting guide** : Common issues solutions

---
**T-101 Foundation** : Établir OpenCV comme foundation technique solide et performante pour les modules T-102→T-104, tout en maintenant compatibilité Phase 0.

**Success Criteria** : OpenCV intégré, performant, stable et prêt pour développement barcode detection industrielle.