# 03 - Pipeline de Binarisation pour Codes MSI 1D

## 🎯 Objectif

Transformer les **ROI détectées** par OpenCV en **images binarisées parfaites** prêtes pour le décodage MSI, avec correction de perspective, normalisation et seuillage adaptatif optimisé codes-barres.

## 🔄 Pipeline Complet

```
ROI Détectée (BarcodeROI)
    ↓
Extraction Rectangle Élargi
    ↓  
Recherche Contour Précis
    ↓
Correction Perspective (si nécessaire)
    ↓
Normalisation Taille & Orientation
    ↓
Binarisation Adaptative Multi-Méthodes
    ↓
Validation Qualité Binarisation
    ↓
Image Binaire Prête Décodage MSI
```

## 🛠️ Implémentation

### 1. ROIExtractor Principal
```kotlin
class ROIExtractor {
    
    companion object {
        private const val TAG = "ROIExtractor"
        
        // Paramètres extraction
        private const val ROI_MARGIN_PERCENT = 0.15f    // Marge 15% autour ROI détectée
        private const val MIN_EXTRACT_WIDTH = 80        // Largeur minimum extraction  
        private const val MIN_EXTRACT_HEIGHT = 25       // Hauteur minimum extraction
        
        // Paramètres normalisation
        private const val TARGET_HEIGHT_HORIZONTAL = 60 // Hauteur cible codes horizontaux
        private const val TARGET_WIDTH_VERTICAL = 60    // Largeur cible codes verticaux
        private const val MAX_NORMALIZED_SIZE = 800     // Taille max après normalisation
        
        // Qualité binarisation  
        private const val MIN_BINARY_CONTRAST = 50     // Contraste minimum acceptable
        private const val MIN_TRANSITION_COUNT = 8     // Transitions barres/espaces minimum
    }
    
    /**
     * Extraction et préparation complète ROI pour décodage MSI
     */
    fun extractAndPrepareMSI(
        sourceMat: Mat,
        roi: BarcodeROI,
        debugMode: Boolean = false
    ): MSIBinaryResult? {
        val startTime = System.currentTimeMillis()
        
        try {
            // ÉTAPE 1: Extraction rectangle élargi
            val expandedRect = expandROIWithMargin(roi.rect, sourceMat.size())
            val extractedMat = Mat(sourceMat, expandedRect)
            
            // ÉTAPE 2: Recherche contour précis dans ROI extraite
            val preciseContour = findPreciseBarcodeContour(extractedMat, roi.orientation)
            
            // ÉTAPE 3: Correction perspective si nécessaire
            val correctedMat = if (preciseContour != null && needsPerspectiveCorrection(preciseContour)) {
                applyPerspectiveCorrection(extractedMat, preciseContour)
            } else {
                extractedMat.clone()
            }
            
            // ÉTAPE 4: Normalisation taille et orientation
            val normalizedMat = normalizeForMSIDecoding(correctedMat, roi.orientation)
            
            // ÉTAPE 5: Binarisation multi-méthodes
            val binarizationResult = performAdaptiveBinarization(normalizedMat, roi.orientation)
            
            // ÉTAPE 6: Validation qualité 
            val isQualityOK = validateBinaryQuality(binarizationResult.binaryMat, roi.orientation)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            // Nettoyage mémoire intermédiaire
            extractedMat.release()
            correctedMat.release() 
            normalizedMat.release()
            
            return if (isQualityOK) {
                MSIBinaryResult(
                    binaryMat = binarizationResult.binaryMat,
                    method = binarizationResult.method,
                    threshold = binarizationResult.threshold,
                    processingTimeMs = processingTime,
                    originalROI = roi,
                    expandedRect = expandedRect,
                    quality = calculateBinaryQuality(binarizationResult.binaryMat)
                )
            } else {
                binarizationResult.binaryMat.release()
                null
            }
            
        } catch (exception: Exception) {
            Log.e(TAG, "ROI extraction failed for ${roi.rect}", exception)
            return null
        }
    }
}
```

### 2. Expansion ROI avec Marge
```kotlin
private fun expandROIWithMargin(originalRect: Rect, imageSize: Size): Rect {
    val marginX = (originalRect.width * ROI_MARGIN_PERCENT).toInt()
    val marginY = (originalRect.height * ROI_MARGIN_PERCENT).toInt()
    
    val expandedX = maxOf(0, originalRect.x - marginX)
    val expandedY = maxOf(0, originalRect.y - marginY)
    val expandedWidth = minOf(
        imageSize.width.toInt() - expandedX,
        originalRect.width + 2 * marginX
    )
    val expandedHeight = minOf(
        imageSize.height.toInt() - expandedY, 
        originalRect.height + 2 * marginY
    )
    
    return Rect(expandedX, expandedY, expandedWidth, expandedHeight)
}
```

### 3. Recherche Contour Précis
```kotlin
private fun findPreciseBarcodeContour(
    extractedMat: Mat,
    orientation: BarcodeOrientation
): MatOfPoint? {
    try {
        // Amélioration contraste pour meilleure détection contour
        val enhanced = Mat()
        Imgproc.equalizeHist(extractedMat, enhanced)
        
        // Détection edges adaptée à l'orientation
        val edges = Mat()
        when (orientation) {
            BarcodeOrientation.HORIZONTAL -> {
                // Emphasis edges verticaux (barres horizontales)
                val kernel = Mat.ones(1, 3, CvType.CV_32F) // Horizontal edge kernel
                kernel.put(0, 1, floatArrayOf(-1f, 0f, 1f))
                Imgproc.filter2D(enhanced, edges, CvType.CV_8U, kernel)
            }
            BarcodeOrientation.VERTICAL -> {
                // Emphasis edges horizontaux (barres verticales) 
                val kernel = Mat.ones(3, 1, CvType.CV_32F) // Vertical edge kernel
                kernel.put(1, 0, floatArrayOf(-1f, 0f, 1f))
                Imgproc.filter2D(enhanced, edges, CvType.CV_8U, kernel)
            }
        }
        
        // Seuillage pour contours nets
        Imgproc.threshold(edges, edges, 30.0, 255.0, Imgproc.THRESH_BINARY)
        
        // Morphologie pour connecter edges fragmentés
        val kernel = when (orientation) {
            BarcodeOrientation.HORIZONTAL -> Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 3.0))
            BarcodeOrientation.VERTICAL -> Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 7.0))
        }
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
        
        // Détection contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        // Sélection meilleur contour (plus grande aire valide)
        val bestContour = contours
            .filter { contour ->
                val boundingRect = Imgproc.boundingRect(contour)
                isValidPreciseContour(boundingRect, orientation)
            }
            .maxByOrNull { Imgproc.contourArea(it) }
        
        // Nettoyage
        enhanced.release()
        edges.release()
        kernel.release()
        hierarchy.release()
        contours.forEach { if (it != bestContour) it.release() }
        
        return bestContour
        
    } catch (exception: Exception) {
        Log.w(TAG, "Precise contour detection failed", exception)
        return null
    }
}
```

### 4. Correction Perspective
```kotlin
private fun applyPerspectiveCorrection(
    sourceMat: Mat,
    contour: MatOfPoint
): Mat {
    try {
        // Approximation contour en quadrilatère
        val epsilon = 0.02 * Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        val approxCurve = MatOfPoint2f()
        Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approxCurve, epsilon, true)
        
        val approxPoints = approxCurve.toArray()
        
        if (approxPoints.size == 4) {
            // Ordre points: top-left, top-right, bottom-right, bottom-left
            val sortedPoints = sortQuadrilateralPoints(approxPoints)
            
            // Rectangle destination (normalisé)
            val boundingRect = Imgproc.boundingRect(contour)
            val destPoints = arrayOf(
                Point(0.0, 0.0),
                Point(boundingRect.width.toDouble(), 0.0),
                Point(boundingRect.width.toDouble(), boundingRect.height.toDouble()),
                Point(0.0, boundingRect.height.toDouble())
            )
            
            // Matrice transformation perspective
            val srcMat = MatOfPoint2f(*sortedPoints)
            val dstMat = MatOfPoint2f(*destPoints)
            val transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)
            
            // Application transformation
            val corrected = Mat()
            Imgproc.warpPerspective(
                sourceMat, corrected, transformMatrix,
                Size(boundingRect.width.toDouble(), boundingRect.height.toDouble())
            )
            
            // Nettoyage
            approxCurve.release()
            srcMat.release()
            dstMat.release()
            transformMatrix.release()
            
            return corrected
        } else {
            // Pas assez de points pour correction → retour original
            approxCurve.release()
            return sourceMat.clone()
        }
        
    } catch (exception: Exception) {
        Log.w(TAG, "Perspective correction failed", exception)
        return sourceMat.clone()
    }
}

private fun sortQuadrilateralPoints(points: Array<Point>): Array<Point> {
    // Tri points pour obtenir: top-left, top-right, bottom-right, bottom-left
    val sorted = points.sortedWith { a, b ->
        when {
            Math.abs(a.y - b.y) < 10 -> a.x.compareTo(b.x) // Même ligne → tri par X
            else -> a.y.compareTo(b.y) // Sinon tri par Y
        }
    }
    
    return if (sorted.size >= 4) {
        arrayOf(sorted[0], sorted[1], sorted[3], sorted[2]) // Réordonnancement quad
    } else {
        points // Fallback si tri échoue
    }
}
```

### 5. Normalisation pour Décodage MSI
```kotlin
private fun normalizeForMSIDecoding(
    sourceMat: Mat,
    orientation: BarcodeOrientation
): Mat {
    val normalized = Mat()
    
    when (orientation) {
        BarcodeOrientation.HORIZONTAL -> {
            // Codes horizontaux: hauteur fixe, largeur proportionnelle
            val targetHeight = TARGET_HEIGHT_HORIZONTAL
            val aspectRatio = sourceMat.width().toDouble() / sourceMat.height()
            val targetWidth = (targetHeight * aspectRatio).toInt().coerceAtMost(MAX_NORMALIZED_SIZE)
            
            Imgproc.resize(
                sourceMat, normalized,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_CUBIC
            )
        }
        
        BarcodeOrientation.VERTICAL -> {
            // Codes verticaux: largeur fixe, hauteur proportionnelle  
            val targetWidth = TARGET_WIDTH_VERTICAL
            val aspectRatio = sourceMat.height().toDouble() / sourceMat.width()
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtMost(MAX_NORMALIZED_SIZE)
            
            Imgproc.resize(
                sourceMat, normalized,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0, 0.0, Imgproc.INTER_CUBIC
            )
        }
    }
    
    return normalized
}
```

### 6. Binarisation Adaptative Multi-Méthodes
```kotlin
data class BinarizationResult(
    val binaryMat: Mat,
    val method: String,
    val threshold: Double,
    val confidence: Float
)

private fun performAdaptiveBinarization(
    sourceMat: Mat,
    orientation: BarcodeOrientation
): BinarizationResult {
    val methods = listOf(
        { otsuThresholding(sourceMat) },
        { adaptiveGaussianThresholding(sourceMat) },
        { adaptiveMeanThresholding(sourceMat) },
        { triangleThresholding(sourceMat) }
    )
    
    var bestResult: BinarizationResult? = null
    var bestScore = 0.0
    
    for ((index, method) in methods.withIndex()) {
        try {
            val result = method()
            val score = evaluateBinarizationQuality(result.binaryMat, orientation)
            
            if (score > bestScore) {
                bestResult?.binaryMat?.release() // Libère ancien meilleur
                bestResult = result.copy(confidence = score.toFloat())
                bestScore = score
            } else {
                result.binaryMat.release() // Libère résultat non retenu
            }
            
        } catch (exception: Exception) {
            Log.w(TAG, "Binarization method $index failed", exception)
        }
    }
    
    return bestResult ?: BinarizationResult(
        binaryMat = Mat.zeros(sourceMat.size(), CvType.CV_8UC1),
        method = "fallback",
        threshold = 127.0,
        confidence = 0.0f
    )
}

private fun otsuThresholding(sourceMat: Mat): BinarizationResult {
    val binary = Mat()
    val threshold = Imgproc.threshold(sourceMat, binary, 0.0, 255.0, 
        Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
    
    return BinarizationResult(binary, "Otsu", threshold, 0.0f)
}

private fun adaptiveGaussianThresholding(sourceMat: Mat): BinarizationResult {
    val binary = Mat()
    Imgproc.adaptiveThreshold(
        sourceMat, binary, 255.0,
        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
        Imgproc.THRESH_BINARY,
        15,   // Block size
        5.0   // C constant
    )
    
    return BinarizationResult(binary, "AdaptiveGaussian", -1.0, 0.0f)
}

private fun adaptiveMeanThresholding(sourceMat: Mat): BinarizationResult {
    val binary = Mat()
    Imgproc.adaptiveThreshold(
        sourceMat, binary, 255.0,
        Imgproc.ADAPTIVE_THRESH_MEAN_C,
        Imgproc.THRESH_BINARY,
        11,   // Block size  
        3.0   // C constant
    )
    
    return BinarizationResult(binary, "AdaptiveMean", -1.0, 0.0f)
}
```

### 7. Validation Qualité Binarisation
```kotlin
private fun evaluateBinarizationQuality(
    binaryMat: Mat,
    orientation: BarcodeOrientation
): Double {
    var qualityScore = 0.0
    
    try {
        // Facteur 1: Contraste binaire (séparation noir/blanc)
        val mean = Core.mean(binaryMat).`val`[0]
        val contrastScore = when {
            mean < 50 || mean > 205 -> 1.0      // Bon contraste (très noir ou très blanc)
            mean < 80 || mean > 175 -> 0.7      // Contraste moyen
            else -> 0.3                          // Faible contraste
        }
        
        // Facteur 2: Nombre de transitions (barres/espaces)
        val transitionCount = countBinaryTransitions(binaryMat, orientation)
        val transitionScore = when {
            transitionCount >= MIN_TRANSITION_COUNT -> 1.0
            transitionCount >= MIN_TRANSITION_COUNT / 2 -> 0.6
            else -> 0.2
        }
        
        // Facteur 3: Régularité espacements (codes-barres ont pattern régulier)
        val regularityScore = evaluateSpacingRegularity(binaryMat, orientation)
        
        // Facteur 4: Ratio pixels noirs/blancs (codes MSI ~40-60% noir)
        val blackPixels = binaryMat.total() - Core.countNonZero(binaryMat)
        val blackRatio = blackPixels.toDouble() / binaryMat.total()
        val ratioScore = when {
            blackRatio in 0.3..0.7 -> 1.0      // Ratio optimal
            blackRatio in 0.2..0.8 -> 0.7      // Acceptable
            else -> 0.3                          // Non optimal
        }
        
        // Combinaison pondérée
        qualityScore = (contrastScore * 0.3) + 
                      (transitionScore * 0.4) + 
                      (regularityScore * 0.2) + 
                      (ratioScore * 0.1)
        
    } catch (exception: Exception) {
        Log.w(TAG, "Quality evaluation failed", exception)
        qualityScore = 0.1
    }
    
    return qualityScore.coerceIn(0.0, 1.0)
}

private fun countBinaryTransitions(
    binaryMat: Mat,
    orientation: BarcodeOrientation
): Int {
    // Compter transitions le long de la ligne médiane du code-barres
    val transitions = when (orientation) {
        BarcodeOrientation.HORIZONTAL -> {
            val midRow = binaryMat.height() / 2
            val rowData = ByteArray(binaryMat.width())
            binaryMat.get(midRow, 0, rowData)
            countTransitionsInArray(rowData)
        }
        BarcodeOrientation.VERTICAL -> {
            val midCol = binaryMat.width() / 2  
            val colData = ByteArray(binaryMat.height())
            for (row in 0 until binaryMat.height()) {
                colData[row] = binaryMat.get(row, midCol)[0].toByte()
            }
            countTransitionsInArray(colData)
        }
    }
    
    return transitions
}

private fun countTransitionsInArray(data: ByteArray): Int {
    if (data.size < 2) return 0
    
    var transitions = 0
    var currentState = data[0] > 127  // true = blanc, false = noir
    
    for (i in 1 until data.size) {
        val newState = data[i] > 127
        if (newState != currentState) {
            transitions++
            currentState = newState
        }
    }
    
    return transitions
}
```

## 📊 Structure Résultat

### MSIBinaryResult
```kotlin
data class MSIBinaryResult(
    val binaryMat: Mat,                    // Image binaire finale
    val method: String,                    // Méthode binarisation utilisée
    val threshold: Double,                 // Seuil appliqué (-1 si adaptatif)
    val processingTimeMs: Long,            // Temps traitement total
    val originalROI: BarcodeROI,           // ROI source
    val expandedRect: Rect,                // Rectangle extraction élargi
    val quality: Double,                   // Score qualité [0.0-1.0]
    val debugInfo: BinarizationDebugInfo? = null
) {
    fun isHighQuality(): Boolean = quality >= 0.7
    fun isMediumQuality(): Boolean = quality >= 0.5
    fun isLowQuality(): Boolean = quality < 0.3
    
    // Conversion vers ByteArray pour décodeur MSI
    fun toBinaryArray(): ByteArray {
        val byteArray = ByteArray((binaryMat.total() * binaryMat.elemSize()).toInt())
        binaryMat.get(0, 0, byteArray)
        return byteArray
    }
    
    fun cleanup() {
        binaryMat.release()
    }
}

data class BinarizationDebugInfo(
    val methodsAttempted: List<String>,
    val contrastScore: Double,
    val transitionCount: Int,
    val regularityScore: Double,
    val blackPixelRatio: Double
)
```

## ⚡ Optimisations Performance

### 1. Cache Kernels Morphologie
```kotlin
object MorphologyKernelCache {
    private val cache = mutableMapOf<String, Mat>()
    
    fun getKernel(shape: Int, size: Size): Mat {
        val key = "${shape}_${size.width}_${size.height}"
        return cache.getOrPut(key) {
            Imgproc.getStructuringElement(shape, size)
        }
    }
}
```

### 2. Réutilisation Mat Intermédiaires
```kotlin
private val matPool = ThreadLocal.withInitial { mutableListOf<Mat>() }

private fun borrowMat(rows: Int, cols: Int, type: Int): Mat {
    val pool = matPool.get()
    val reusable = pool.find { 
        it.rows() == rows && it.cols() == cols && it.type() == type 
    }
    
    return if (reusable != null) {
        pool.remove(reusable)
        reusable.setTo(Scalar.all(0.0)) // Reset
        reusable
    } else {
        Mat(rows, cols, type)
    }
}

private fun returnMat(mat: Mat) {
    val pool = matPool.get()
    if (pool.size < 5) {  // Limite cache
        pool.add(mat)
    } else {
        mat.release()
    }
}
```

## 🎯 Critères de Réussite

### Qualité Binarisation
- **Contrast score** : ≥0.7 pour images utilisables
- **Transition count** : ≥8 transitions barres/espaces pour codes MSI typiques
- **Binary ratio** : 30-70% pixels noirs (caractéristique codes MSI)
- **Regularity score** : ≥0.5 pour espacements cohérents

### Performance
- **Temps total** : <10ms pour extraction + binarisation
- **Success rate** : >85% ROI détectées → binarisations utilisables
- **Memory efficiency** : <3MB heap increase avec pooling Mat
- **Quality consistency** : Score qualité cohérent avec qualité visuelle

---

**🎯 Ce pipeline de binarisation produit des images optimales pour le décodage MSI, avec validation qualité automatique et fallback multi-méthodes pour maximiser les chances de réussite.**