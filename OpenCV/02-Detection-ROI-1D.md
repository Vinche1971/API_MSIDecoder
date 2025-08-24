# 02 - Détection ROI Codes-Barres 1D avec OpenCV

## 🎯 Objectif

Détecter la **présence** et la **localisation** de codes-barres 1D (incluant MSI) dans les images sans les décoder, en utilisant les techniques OpenCV d'analyse de gradient et de morphologie.

## 🔬 Principes Techniques

### Caractéristiques Codes-Barres 1D
```
MSI Code Example: 48334890
┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐ ┌─┐
│ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │  ← Barres (foncées)
└─┘ └─┘ └─┘ └─┘ └─┘ └─┘ └─┘ └─┘
  Espaces (claires)

Propriétés détectables:
• Pattern barres/espaces répétitif
• Gradient perpendiculaire intense (transitions noir↔blanc)
• Aspect ratio: Largeur >> Hauteur (≥ 3:1)
• Alignement horizontal ou vertical
```

### Pipeline de Détection OpenCV
```
Image NV21 → Mat Grayscale
    ↓
Analyse Gradient (Sobel X/Y)
    ↓
Morphologie (fermeture rectangulaire) 
    ↓
Détection Contours
    ↓
Filtrage Géométrique
    ↓
Liste ROI Candidates
```

## 🛠️ Implémentation Technique

### 1. BarcodeROIDetector Principal
```kotlin
class BarcodeROIDetector {
    
    companion object {
        private const val TAG = "BarcodeROIDetector"
        
        // Paramètres gradient analysis
        private const val MIN_GRADIENT_RATIO = 3.0    // X/Y gradient ratio pour codes horizontaux
        private const val GRADIENT_THRESHOLD = 30.0   // Seuil magnitude gradient
        
        // Paramètres morphologie
        private val HORIZONTAL_KERNEL = Size(21.0, 7.0)  // Kernel fermeture horizontale 
        private val VERTICAL_KERNEL = Size(7.0, 21.0)    // Kernel fermeture verticale
        private val NOISE_KERNEL = Size(3.0, 3.0)       // Kernel suppression bruit
        
        // Contraintes géométriques ROI
        private const val MIN_ROI_WIDTH = 60
        private const val MIN_ROI_HEIGHT = 20
        private const val MAX_ROI_WIDTH = 800
        private const val MAX_ROI_HEIGHT = 200
        private const val MIN_ASPECT_RATIO = 2.5
        private const val MIN_AREA = 1200
    }
    
    /**
     * Détection ROI codes-barres 1D multi-orientation
     */
    fun detectBarcodeROIs(
        nv21Data: ByteArray,
        width: Int,
        height: Int, 
        rotationDegrees: Int
    ): List<BarcodeROI> {
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. Conversion NV21 → OpenCV Mat
            val grayMat = OpenCVUtils.nv21ToGrayMat(nv21Data, width, height)
            
            // 2. Correction rotation caméra
            val correctedMat = if (rotationDegrees != 0) {
                OpenCVUtils.rotateImage(grayMat, rotationDegrees)
            } else grayMat
            
            // 3. Détection multi-orientation
            val roiCandidates = mutableListOf<BarcodeROI>()
            
            // Codes horizontaux (plus fréquents)
            roiCandidates.addAll(detectHorizontalBarcodes(correctedMat))
            
            // Codes verticaux
            roiCandidates.addAll(detectVerticalBarcodes(correctedMat))
            
            // 4. Filtrage et tri par confiance
            val filteredROIs = filterAndRankROIs(roiCandidates, correctedMat)
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "ROI detection: ${filteredROIs.size} candidates in ${processingTime}ms")
            
            // Nettoyage mémoire
            grayMat.release()
            if (rotationDegrees != 0) correctedMat.release()
            
            return filteredROIs.take(3) // Max 3 ROI par frame pour performance
            
        } catch (exception: Exception) {
            Log.e(TAG, "ROI detection failed", exception)
            return emptyList()
        }
    }
}
```

### 2. Détection Codes Horizontaux
```kotlin
private fun detectHorizontalBarcodes(grayMat: Mat): List<BarcodeROI> {
    val roiCandidates = mutableListOf<BarcodeROI>()
    
    // ÉTAPE 1: Analyse Gradient
    val gradientX = Mat()
    val gradientY = Mat()
    
    // Gradient Sobel - privilégié sur Scharr pour performance
    Imgproc.Sobel(grayMat, gradientX, CvType.CV_32F, 1, 0, 3)  // Gradient vertical (détecte barres horizontales)
    Imgproc.Sobel(grayMat, gradientY, CvType.CV_32F, 0, 1, 3)  // Gradient horizontal
    
    // Magnitude et orientation
    val magnitude = Mat()
    val orientation = Mat()
    Core.cartToPolar(gradientX, gradientY, magnitude, orientation)
    
    // ÉTAPE 2: Masque Barcode Horizontal
    val barcodeRaask = Mat()
    
    // Critère 1: Gradient fort (transitions nettes noir/blanc)
    val strongGradient = Mat()
    Imgproc.threshold(magnitude, strongGradient, GRADIENT_THRESHOLD, 255.0, Imgproc.THRESH_BINARY)
    
    // Critère 2: Orientation verticale dominante (±15° autour 90°)
    val verticalOrientation = Mat()
    val lowerBound = Scalar(Math.PI/2 - Math.PI/12)  // 75°
    val upperBound = Scalar(Math.PI/2 + Math.PI/12)  // 105°
    Core.inRange(orientation, lowerBound, upperBound, verticalOrientation)
    
    // Combinaison critères
    Core.bitwise_and(strongGradient, verticalOrientation, barcodeRaask)
    
    // ÉTAPE 3: Morphologie Spécialisée Codes-Barres
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, HORIZONTAL_KERNEL)
    
    // Fermeture: connecter barres proches horizontalement
    Imgproc.morphologyEx(barcodeRaask, barcodeRaask, Imgproc.MORPH_CLOSE, kernel)
    
    // Suppression bruit résiduel
    val noiseKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, NOISE_KERNEL)
    Imgproc.morphologyEx(barcodeRaask, barcodeRaask, Imgproc.MORPH_OPEN, noiseKernel)
    
    // ÉTAPE 4: Détection et Filtrage Contours
    val contours = mutableListOf<MatOfPoint>()
    val hierarchy = Mat()
    
    Imgproc.findContours(
        barcodeRaask,
        contours,
        hierarchy,
        Imgproc.RETR_EXTERNAL,
        Imgproc.CHAIN_APPROX_SIMPLE
    )
    
    // ÉTAPE 5: Validation Géométrique
    for (contour in contours) {
        val boundingRect = Imgproc.boundingRect(contour)
        
        if (isValidHorizontalBarcode(boundingRect, contour)) {
            val confidence = calculateHorizontalConfidence(boundingRect, barcodeRaask, contour)
            
            roiCandidates.add(
                BarcodeROI(
                    rect = boundingRect,
                    orientation = BarcodeOrientation.HORIZONTAL,
                    confidence = confidence,
                    contourArea = Imgproc.contourArea(contour),
                    aspectRatio = boundingRect.width.toDouble() / boundingRect.height
                )
            )
        }
    }
    
    // Nettoyage mémoire OpenCV
    gradientX.release()
    gradientY.release()
    magnitude.release()
    orientation.release()
    strongGradient.release()
    verticalOrientation.release()
    barcodeRaask.release()
    kernel.release()
    noiseKernel.release() 
    hierarchy.release()
    contours.forEach { it.release() }
    
    return roiCandidates
}
```

### 3. Validation Géométrique
```kotlin
private fun isValidHorizontalBarcode(rect: Rect, contour: MatOfPoint): Boolean {
    // Validation taille
    if (rect.width < MIN_ROI_WIDTH || rect.height < MIN_ROI_HEIGHT) return false
    if (rect.width > MAX_ROI_WIDTH || rect.height > MAX_ROI_HEIGHT) return false
    
    // Validation aspect ratio (codes 1D : largeur >> hauteur)
    val aspectRatio = rect.width.toDouble() / rect.height
    if (aspectRatio < MIN_ASPECT_RATIO) return false
    
    // Validation aire minimum
    if (rect.area() < MIN_AREA) return false
    
    // Validation densité contour (doit remplir decent portion du rectangle)
    val contourArea = Imgproc.contourArea(contour)
    val rectArea = rect.width * rect.height
    val densityRatio = contourArea / rectArea
    if (densityRatio < 0.3) return false  // 30% minimum
    
    // Validation convexité (codes-barres relativement convexes)
    val hull = MatOfInt()
    Imgproc.convexHull(contour, hull)
    val hullPoints = hull.toArray().map { contour.toArray()[it] }.toTypedArray()
    val hullArea = Imgproc.contourArea(MatOfPoint(*hullPoints))
    val convexity = contourArea / hullArea
    hull.release()
    
    return convexity >= 0.7  // 70% convexité minimum
}
```

### 4. Calcul Score de Confiance
```kotlin
private fun calculateHorizontalConfidence(
    rect: Rect,
    binaryMask: Mat,
    contour: MatOfPoint
): Double {
    var confidence = 0.0
    
    try {
        // Facteur 1: Ratio aspect (plus proche de codes-barres typiques = mieux)
        val aspectRatio = rect.width.toDouble() / rect.height
        val aspectScore = when {
            aspectRatio >= 8.0 -> 1.0      // Excellent (codes longs)
            aspectRatio >= 5.0 -> 0.8      // Bon
            aspectRatio >= 3.0 -> 0.6      // Acceptable  
            else -> 0.3                    // Faible
        }
        
        // Facteur 2: Densité gradient dans ROI
        val roi = Mat(binaryMask, rect)
        val nonZeroCount = Core.countNonZero(roi)
        val totalPixels = rect.area()
        val densityScore = (nonZeroCount.toDouble() / totalPixels).coerceIn(0.0, 1.0)
        roi.release()
        
        // Facteur 3: Régularité contour (périmètre vs aire)
        val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        val area = Imgproc.contourArea(contour)
        val compactness = (4 * Math.PI * area) / (perimeter * perimeter)
        val compactnessScore = compactness.coerceIn(0.0, 1.0)
        
        // Facteur 4: Position dans l'image (centre souvent meilleur)
        val imageCenter = Point(binaryMask.width() / 2.0, binaryMask.height() / 2.0)
        val rectCenter = Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0)
        val distance = Math.sqrt(
            Math.pow(rectCenter.x - imageCenter.x, 2.0) + 
            Math.pow(rectCenter.y - imageCenter.y, 2.0)
        )
        val maxDistance = Math.sqrt(
            Math.pow(binaryMask.width() / 2.0, 2.0) + 
            Math.pow(binaryMask.height() / 2.0, 2.0)
        )
        val positionScore = 1.0 - (distance / maxDistance)
        
        // Combinaison pondérée
        confidence = (aspectScore * 0.4) + 
                    (densityScore * 0.3) + 
                    (compactnessScore * 0.2) + 
                    (positionScore * 0.1)
                    
    } catch (exception: Exception) {
        Log.w(TAG, "Error calculating confidence", exception)
        confidence = 0.5  // Valeur neutre en cas d'erreur
    }
    
    return confidence.coerceIn(0.0, 1.0)
}
```

## 🔄 Détection Multi-Orientation

### Support Orientations Multiples
```kotlin
enum class BarcodeOrientation(val angleDegrees: Int, val tolerance: Int) {
    HORIZONTAL(0, 15),      // ±15° autour horizontal
    VERTICAL(90, 15),       // ±15° autour vertical  
    DIAGONAL_45(45, 10),    // ±10° autour 45°
    DIAGONAL_135(135, 10)   // ±10° autour 135°
}

private fun detectVerticalBarcodes(grayMat: Mat): List<BarcodeROI> {
    // Logique similaire à detectHorizontalBarcodes mais:
    // • Kernel morphologique vertical (7×21 au lieu de 21×7)
    // • Orientation horizontale dominante (±15° autour 0°/180°) 
    // • Validation aspect ratio: hauteur >> largeur
    
    // Implémentation...
    return verticalROIs
}
```

## 📊 Structures de Données

### BarcodeROI Candidate
```kotlin
data class BarcodeROI(
    val rect: Rect,                      // Rectangle englobant OpenCV
    val orientation: BarcodeOrientation,  // Orientation détectée
    val confidence: Double,              // Score confiance [0.0-1.0]
    val contourArea: Double,             // Aire contour pixels
    val aspectRatio: Double,             // Ratio largeur/hauteur
    val detectionTimeMs: Long = 0L,      // Temps détection individuel
    val debugInfo: ROIDebugInfo? = null  // Info debug optionnelle
) {
    val isHighConfidence: Boolean get() = confidence >= 0.8
    val isLowConfidence: Boolean get() = confidence < 0.4
    
    // Conversion vers Android Rect pour T-008 compatibility
    fun toAndroidRect(): android.graphics.Rect {
        return android.graphics.Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
    }
}

data class ROIDebugInfo(
    val gradientDensity: Double,
    val morphologyKernelUsed: Size,
    val contourPoints: Int,
    val convexityScore: Double
)
```

## ⚡ Optimisations Performance

### 1. Réutilisation Mémoire Mat
```kotlin
object MatCache {
    private val cache = mutableMapOf<String, Mat>()
    
    fun getMat(key: String, rows: Int, cols: Int, type: Int): Mat {
        val cacheKey = "${key}_${rows}_${cols}_${type}"
        return cache.getOrPut(cacheKey) {
            Mat(rows, cols, type)
        }
    }
    
    fun recycleMat(key: String, mat: Mat) {
        mat.setTo(Scalar.all(0.0))  // Reset contenu
        // Mat reste en cache pour réutilisation
    }
}
```

### 2. Filtrage Pré-Traitement
```kotlin
private fun prefilterImage(grayMat: Mat): Mat {
    // Filtrage rapide pour éliminer zones sans intérêt
    val mean = Core.mean(grayMat).`val`[0]
    val stddev = MatOfDouble()
    Core.meanStdDev(grayMat, MatOfDouble(), stddev)
    val std = stddev.get(0, 0)[0]
    
    // Si image trop uniforme (pas de contraste), skip processing
    if (std < 15.0) {
        return Mat() // Mat vide = pas de ROI candidates
    }
    
    return grayMat
}
```

## 🎯 Critères de Réussite

### Performance
- **Temps détection** : <35ms pour respecter contrainte 45ms totale 
- **ROI candidates** : 1-3 par frame (éviter surcharge)
- **Memory usage** : <5MB heap increase
- **False positives** : <10% sur images non-codes-barres

### Qualité Détection
- **Codes MSI horizontaux** : >90% détection (aspect ratio 6-12:1)
- **Codes MSI verticaux** : >80% détection (moins fréquents)  
- **Multi-orientation** : ±15° tolérance minimum
- **Robustesse éclairage** : Conditions normales + faible luminosité

---

**🎯 Ce pipeline de détection ROI fournit les régions candidates optimales pour la binarisation et le décodage MSI, avec une performance compatible avec les contraintes temps réel de l'application.**