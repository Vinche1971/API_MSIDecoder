# T-008 - Overlay Coordonnées MLKit Parfaites

## 🎯 Objectif
Établir une transformation coordonnées **pixel-perfect** entre les détections MLKit (espace caméra) et l'affichage ROI overlay (espace PreviewView utilisateur) pour une UX professionnelle.

## 📋 Problème Actuel
- ❌ **ROI décalées** : MLKit détecte correctement mais overlay mal positionnée
- ❌ **Mouvement erratique** : ROI "voyage" aléatoirement sur l'écran
- ❌ **Axes inversés** : Mouvement gauche-droite apparaît haut-bas
- ❌ **Portrait mode** : Transformation 90° rotation non maîtrisée

## 🔍 Analyse Technique

### Espaces de Coordonnées
1. **Espace Caméra** (CameraX ImageAnalysis)
   - Format : NV21, typiquement 640×480 (landscape)  
   - Orientation : Capteur natif (landscape)
   - Origine : (0,0) coin supérieur gauche capteur

2. **Espace MLKit**
   - Input : Image CameraX (640×480)
   - Output : `boundingBox` + `cornerPoints`
   - Coordonnées : Relatives à l'image d'entrée

3. **Espace PreviewView** (Interface utilisateur)
   - Format : Portrait (480×640 après rotation)
   - Orientation : Portrait forcé (AndroidManifest)
   - Origine : (0,0) coin supérieur gauche écran

### Transformation Requise
```
MLKit(640×480, landscape) → PreviewView(480×640, portrait)
Rotation 90° + Scale + Translate
```

## 🛠️ Implémentation Systématique

### Étape 1 : Collecte Métriques Précises
```kotlin
// Logs systématiques toutes transformations
Log.d("COORD_DEBUG", "=== FRAME ANALYSIS ===")
Log.d("COORD_DEBUG", "CameraX Frame: ${width}×${height}")  
Log.d("COORD_DEBUG", "PreviewView Size: ${previewView.width}×${previewView.height}")
Log.d("COORD_DEBUG", "MLKit BoundingBox: ${boundingBox}")
Log.d("COORD_DEBUG", "Device Orientation: ${resources.configuration.orientation}")
Log.d("COORD_DEBUG", "PreviewView ScaleType: ${previewView.scaleType}")
```

### Étape 2 : Matrix Transformation Portrait
```kotlin
private fun transformMLKitToPreview(
    mlkitBoundingBox: android.graphics.Rect,
    cameraWidth: Int,      // 640 
    cameraHeight: Int,     // 480
    previewWidth: Int,     // 480 (after rotation)
    previewHeight: Int     // 640 (after rotation)
): android.graphics.Rect {
    
    // Step 1: Convert to normalized coordinates [0,1]
    val normalizedLeft = mlkitBoundingBox.left.toFloat() / cameraWidth
    val normalizedTop = mlkitBoundingBox.top.toFloat() / cameraHeight
    val normalizedRight = mlkitBoundingBox.right.toFloat() / cameraWidth  
    val normalizedBottom = mlkitBoundingBox.bottom.toFloat() / cameraHeight
    
    // Step 2: Apply 90° rotation (landscape → portrait)
    // Rotation formula: (x,y) → (1-y, x)
    val rotatedLeft = 1.0f - normalizedBottom
    val rotatedTop = normalizedLeft
    val rotatedRight = 1.0f - normalizedTop
    val rotatedBottom = normalizedRight
    
    // Step 3: Scale to PreviewView dimensions
    val previewLeft = (rotatedLeft * previewWidth).toInt()
    val previewTop = (rotatedTop * previewHeight).toInt() 
    val previewRight = (rotatedRight * previewWidth).toInt()
    val previewBottom = (rotatedBottom * previewHeight).toInt()
    
    return android.graphics.Rect(previewLeft, previewTop, previewRight, previewBottom)
}
```

### Étape 3 : Validation Systématique

#### Test Pattern Positions
1. **Coin Supérieur Gauche** : QR placé (0%, 0%) → ROI doit apparaître coin supérieur gauche écran
2. **Centre Écran** : QR placé (50%, 50%) → ROI doit apparaître centre exact écran  
3. **Coin Inférieur Droit** : QR placé (100%, 100%) → ROI doit apparaître coin inférieur droit écran
4. **Positions Intermédiaires** : (25%, 25%), (75%, 75%) validation précision

#### Critères Validation
- ✅ **Précision ± 5px** : ROI centrée sur code réel
- ✅ **Stabilité** : ROI ne bouge pas si code statique
- ✅ **Réactivité** : ROI suit mouvement code en temps réel
- ✅ **Cohérence axes** : Mouvement horizontal → mouvement horizontal ROI

### Étape 4 : Gestion Edge Cases
```kotlin
// Gestion scaleType PreviewView
when (previewView.scaleType) {
    PreviewView.ScaleType.FILL_CENTER -> {
        // Ajustement crop/scale automatique
    }
    PreviewView.ScaleType.FIT_CENTER -> {
        // Ajustement letterbox/pillarbox
    }
}

// Validation bounds
private fun clampToPreview(rect: android.graphics.Rect, previewWidth: Int, previewHeight: Int): android.graphics.Rect {
    return android.graphics.Rect(
        rect.left.coerceIn(0, previewWidth),
        rect.top.coerceIn(0, previewHeight), 
        rect.right.coerceIn(0, previewWidth),
        rect.bottom.coerceIn(0, previewHeight)
    )
}
```

## 🎯 Critères d'Acceptation

### Tests Manuels Obligatoires
1. **QR Generator** : Générer QR codes test différentes tailles
2. **Positions Systematic** : 9 positions grille 3×3 sur écran
3. **Mouvement Fluide** : Déplacer code → ROI suit parfaitement
4. **Multi-codes** : Plusieurs QR simultanés → ROI multiples correctes

### Métriques Techniques
- ✅ **Latence** : ROI update < 50ms après détection MLKit
- ✅ **Précision** : Centre ROI ± 5px du centre code réel
- ✅ **Performance** : Transformation < 1ms CPU
- ✅ **Memory** : Pas de leak Matrix/Rect objects

### Validation Utilisateur
- ✅ **UX Intuitive** : ROI "colle" visuellement au code
- ✅ **Confiance** : Utilisateur voit que détection fonctionne
- ✅ **Debug facilité** : ROI visible = détection visible

## 🔄 Architecture Extensible

### Interface Réutilisable
```kotlin
interface CoordinateTransformer {
    fun cameraToPreview(
        detectionRect: android.graphics.Rect,
        cameraSize: Size,
        previewSize: Size  
    ): android.graphics.Rect
}

class MLKitCoordinateTransformer : CoordinateTransformer
class OpenCVCoordinateTransformer : CoordinateTransformer  // Future
```

### Intégration T-009 (Couleurs)
```kotlin
// Interface T-008 → T-009
data class OverlayRoi(
    val boundingBox: android.graphics.Rect,  // T-008: Coordonnées parfaites
    val detectionType: DetectionType,        // T-009: Type pour couleur
    val confidence: Float                    // T-009: Intensité couleur
)
```

## 📊 Livrables

### Code
- ✅ **CoordinateTransformer interface**
- ✅ **MLKitCoordinateTransformer implementation** 
- ✅ **RoiOverlayView integration**
- ✅ **Test validation methods**

### Documentation  
- ✅ **Matrix transformation explained**
- ✅ **Edge cases handled**
- ✅ **Performance benchmarks**
- ✅ **Test procedures defined**

### Validation
- ✅ **9-point grid test passed**
- ✅ **Multi-code test passed**
- ✅ **Movement tracking test passed**
- ✅ **Performance benchmarks met**

## 🚀 Impact

### Immediate
- ✅ **MLKit ROI overlay parfaite** → UX professionnelle
- ✅ **Debug visuel facilité** → Développement accéléré
- ✅ **Confiance utilisateur** → App crédible

### Future (OpenCV)
- ✅ **Logique réutilisable** → OpenCV integration directe
- ✅ **Pattern éprouvé** → Moins de bugs coordonnées
- ✅ **Architecture extensible** → Support multi-detecteurs

---
**Priorité** : 🔥 **CRITIQUE** - Foundation de toute détection visuelle
**Effort** : 📊 **1-2 jours** développement + validation
**Risque** : ⚠️ **BAS** - Logique mathématique claire, testing systématique