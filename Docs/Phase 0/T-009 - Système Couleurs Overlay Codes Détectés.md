# T-009 - Système Couleurs Overlay Codes Détectés

## 🎯 Objectif
Implémenter un système de couleurs intuitif et professionnel pour différencier visuellement les types de codes-barres détectés dans l'overlay ROI, permettant un debug immédiat et une UX claire.

## 🎨 Système de Couleurs Proposé

### Palette Couleurs Professionnelle
```kotlin
enum class DetectionType(
    val color: Int,
    val strokeWidth: Float,
    val description: String
) {
    // MLKit détections
    MLKIT_SUCCESS(
        color = Color.parseColor("#00C853"),      // Vert intense
        strokeWidth = 4.0f,
        description = "Code reconnu MLKit (EAN, QR, DataMatrix, etc.)"
    ),
    
    MLKIT_UNKNOWN(
        color = Color.parseColor("#2196F3"),      // Bleu standard  
        strokeWidth = 3.0f,
        description = "Format détecté MLKit mais non supporté"
    ),
    
    // OpenCV détections (future)
    OPENCV_BARCODE(
        color = Color.parseColor("#FF9800"),      // Orange OpenCV
        strokeWidth = 3.0f, 
        description = "Code-barres détecté OpenCV"
    ),
    
    // MSI spécifique
    MSI_CANDIDATE(
        color = Color.parseColor("#E91E63"),      // Rose/Rouge MSI
        strokeWidth = 4.0f,
        description = "Candidat MSI en cours d'analyse"  
    ),
    
    MSI_DECODED(
        color = Color.parseColor("#4CAF50"),      // Vert succès
        strokeWidth = 5.0f,
        description = "MSI décodé avec succès"
    ),
    
    MSI_FAILED(
        color = Color.parseColor("#F44336"),      // Rouge échec
        strokeWidth = 2.0f,
        description = "MSI détecté mais échec décodage"
    ),
    
    // Debug et développement  
    DEBUG_REGION(
        color = Color.parseColor("#9C27B0"),      // Violet debug
        strokeWidth = 1.0f,
        description = "Zone debug développement"
    )
}
```

## 🔍 Logique d'Attribution Couleurs

### Priorité d'Affichage (si multiple détections)
1. **MSI_DECODED** (Vert) → Priorité absolue
2. **MLKIT_SUCCESS** (Vert intense) → Codes standards  
3. **MSI_CANDIDATE** (Rose) → MSI en cours
4. **OPENCV_BARCODE** (Orange) → Détection OpenCV
5. **MLKIT_UNKNOWN** (Bleu) → Format inconnu
6. **MSI_FAILED** (Rouge) → Échecs visibles
7. **DEBUG_REGION** (Violet) → Debug seulement

### Règles Couleurs par Contexte

#### Phase 0 (MLKit Only)
```kotlin
when (scanResult) {
    is ScanResult.Success -> {
        if (scanResult.source == ScanSource.ML_KIT) {
            DetectionType.MLKIT_SUCCESS  // Vert intense
        }
    }
    is ScanResult.UnknownFormat -> {
        DetectionType.MLKIT_UNKNOWN     // Bleu  
    }
}
```

#### Phase 1+ (MLKit + OpenCV/MSI)  
```kotlin
when {
    msiResult.isSuccess() -> DetectionType.MSI_DECODED      // Vert
    msiResult.isCandidate() -> DetectionType.MSI_CANDIDATE  // Rose
    msiResult.isFailed() -> DetectionType.MSI_FAILED        // Rouge
    mlkitResult.isSuccess() -> DetectionType.MLKIT_SUCCESS  // Vert intense
    openCVResult.isDetected() -> DetectionType.OPENCV_BARCODE // Orange
    mlkitResult.isUnknown() -> DetectionType.MLKIT_UNKNOWN  // Bleu
}
```

## 🎨 Styles Visuels Avancés

### Style Base ROI
```kotlin
data class RoiStyle(
    val strokeColor: Int,
    val strokeWidth: Float,
    val fillColor: Int = Color.TRANSPARENT,
    val cornerRadius: Float = 8.0f,
    val dashPattern: FloatArray? = null,     // Pour styles pointillés
    val animation: AnimationType = AnimationType.NONE
)

enum class AnimationType {
    NONE,
    PULSE,          // Pulsation pour succès
    BLINK,          // Clignotement pour erreurs  
    SCANNING        // Animation balayage pour candidats
}
```

### Styles par Type
```kotlin
fun DetectionType.toRoiStyle(): RoiStyle = when (this) {
    MLKIT_SUCCESS -> RoiStyle(
        strokeColor = color,
        strokeWidth = strokeWidth,
        animation = AnimationType.PULSE,     // Succès = pulsation douce
        fillColor = Color.parseColor("#1000C853")  // Fill semi-transparent
    )
    
    MSI_CANDIDATE -> RoiStyle(
        strokeColor = color,
        strokeWidth = strokeWidth, 
        animation = AnimationType.SCANNING,  // Animation "scanning"
        dashPattern = floatArrayOf(10f, 5f) // Ligne pointillée
    )
    
    MSI_FAILED -> RoiStyle(
        strokeColor = color,
        strokeWidth = strokeWidth,
        animation = AnimationType.BLINK,     // Erreur = clignotement
        fillColor = Color.parseColor("#20F44336")  // Fill rouge léger
    )
    
    // ... autres styles
}
```

## 🔧 Implémentation Technique

### RoiOverlayView Extensions
```kotlin
class RoiOverlayView : View {
    
    private val paintCache = mutableMapOf<DetectionType, Paint>()
    
    private fun getPaint(detectionType: DetectionType): Paint {
        return paintCache.getOrPut(detectionType) {
            Paint().apply {
                color = detectionType.color
                strokeWidth = detectionType.strokeWidth * density
                style = Paint.Style.STROKE
                isAntiAlias = true
                pathEffect = detectionType.toDashPathEffect()
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        roiCandidates.forEach { roi ->
            val paint = getPaint(roi.detectionType)
            val style = roi.detectionType.toRoiStyle()
            
            // Draw main rectangle
            drawRoiRectangle(canvas, roi.boundingBox, paint, style)
            
            // Draw type indicator
            drawTypeIndicator(canvas, roi.boundingBox, roi.detectionType)
            
            // Apply animation if needed
            applyAnimation(roi.detectionType)
        }
    }
    
    private fun drawTypeIndicator(
        canvas: Canvas, 
        rect: android.graphics.Rect, 
        type: DetectionType
    ) {
        // Small colored circle in corner with type initial
        val radius = 12f * density
        val centerX = rect.right - radius - 4f
        val centerY = rect.top + radius + 4f
        
        // Background circle
        val bgPaint = Paint().apply {
            color = type.color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, radius, bgPaint)
        
        // Type letter
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 10f * density
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val typeChar = when (type) {
            DetectionType.MLKIT_SUCCESS -> "M"
            DetectionType.MSI_DECODED -> "✓"
            DetectionType.MSI_CANDIDATE -> "?"
            DetectionType.MSI_FAILED -> "✗"
            DetectionType.OPENCV_BARCODE -> "O"
            DetectionType.MLKIT_UNKNOWN -> "U"
            DetectionType.DEBUG_REGION -> "D"
        }
        canvas.drawText(typeChar, centerX, centerY + 4f, textPaint)
    }
}
```

### Data Classes
```kotlin
data class RoiCandidate(
    val boundingBox: android.graphics.Rect,    // T-008: Coordonnées parfaites
    val detectionType: DetectionType,          // T-009: Type pour couleur
    val confidence: Float = 1.0f,              // T-009: Intensité (future)
    val metadata: Map<String, Any> = emptyMap() // Debug info
)

data class OverlayState(
    val candidates: List<RoiCandidate>,
    val showTypeIndicators: Boolean = true,
    val showDebugInfo: Boolean = false,
    val animationsEnabled: Boolean = true
)
```

## 📊 Configurations Debug

### Modes Développement
```kotlin
enum class OverlayDebugMode {
    PRODUCTION,     // Couleurs simples seulement
    DEVELOPMENT,    // + Type indicators + stats
    FULL_DEBUG      // + Coordonnées + metrics + timings
}

class OverlayConfig {
    var debugMode: OverlayDebugMode = OverlayDebugMode.PRODUCTION
    var showFPS: Boolean = false
    var showCoordinates: Boolean = false  
    var showProcessingTime: Boolean = false
    var showConfidence: Boolean = false
    
    fun isDebug(): Boolean = debugMode != OverlayDebugMode.PRODUCTION
}
```

### Debug Text Overlay
```kotlin
private fun drawDebugInfo(canvas: Canvas, roi: RoiCandidate) {
    if (!overlayConfig.isDebug()) return
    
    val debugText = buildString {
        appendLine("${roi.detectionType.name}")
        if (overlayConfig.showCoordinates) {
            appendLine("${roi.boundingBox.left},${roi.boundingBox.top}")
        }
        if (overlayConfig.showConfidence) {
            appendLine("Conf: ${"%.2f".format(roi.confidence)}")
        }
        // Add processing time, etc.
    }
    
    // Draw semi-transparent background + text
    drawDebugTextBox(canvas, roi.boundingBox, debugText)
}
```

## 🎯 Critères d'Acceptation

### Visuel
- ✅ **Couleurs distinctes** : Chaque type facilement identifiable
- ✅ **Contraste suffisant** : Lisible sur tout background  
- ✅ **Cohérence** : Palette harmonieuse et professionnelle
- ✅ **Accessibilité** : Compatible daltonisme (éviter rouge/vert adjacents)

### Fonctionnel
- ✅ **Performance** : Pas de lag overlay < 16ms/frame
- ✅ **Mémoire** : Paint cache efficient, pas de leaks
- ✅ **Animations fluides** : 60fps si enabled
- ✅ **Debug toggle** : Mode debug activable/désactivable

### UX
- ✅ **Intuitif** : Couleurs correspondent aux attentes utilisateur
- ✅ **Informatif** : Type détection immédiatement visible
- ✅ **Non-intrusif** : N'obstrue pas le code scanné
- ✅ **Configurable** : Debug modes pour développement

## 📱 Interface Settings (Future)

```kotlin
// Dans DebugControlsView
class ColorSystemSettings {
    var enableAnimations: Boolean = true
    var showTypeIndicators: Boolean = true  
    var overlayOpacity: Float = 0.8f
    var debugMode: OverlayDebugMode = OverlayDebugMode.PRODUCTION
}

// UI Controls
Switch("Animations") { enableAnimations = it }
Switch("Type Indicators") { showTypeIndicators = it }  
Slider("Opacity", 0.3f..1.0f) { overlayOpacity = it }
Dropdown("Debug Mode", OverlayDebugMode.values()) { debugMode = it }
```

## 🔄 Évolutivité

### Extensions Futures
```kotlin
// Support nouveaux types détection
enum class DetectionType {
    // ... existing ...
    
    // Future formats
    PDF417_DETECTED,
    AZTEC_DETECTED,
    DATAMATRIX_CANDIDATE,
    
    // AI/ML enhanced
    AI_ENHANCED_MSI,
    ML_CONFIDENCE_LOW,
    ML_CONFIDENCE_HIGH
}

// Support métadonnées riches
data class RichRoiCandidate(
    val basic: RoiCandidate,
    val processingTime: Duration,
    val algorithmUsed: String,
    val qualityScore: Float,
    val additionalInfo: Map<String, Any>
)
```

## 🚀 Impact

### Développement
- ✅ **Debug visuel immédiat** → Bugs détectés plus vite
- ✅ **Types détection clairs** → Comprehension pipeline
- ✅ **Performance tracking** → Optimisations ciblées

### Production  
- ✅ **UX professionnelle** → Confiance utilisateur
- ✅ **Feedback visuel** → Utilisateur comprend l'état app
- ✅ **Différentiation claire** → Pas de confusion types codes

### Maintenance
- ✅ **Code extensible** → Nouveaux types faciles à ajouter
- ✅ **Configuration centralisée** → Modifications couleurs simples  
- ✅ **Debug modes** → Support technique facilité

---
**Priorité** : 🎨 **ÉLEVÉE** - UX et Debug Experience  
**Effort** : 📊 **0.5-1 jour** implémentation
**Risque** : ⚠️ **TRÈS BAS** - UI seulement, pas de logique complexe