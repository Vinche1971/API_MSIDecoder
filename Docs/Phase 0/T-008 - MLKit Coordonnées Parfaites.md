# T-008 - MLKit Coordonnées Parfaites (EN COURS)

## 🎯 Objectif
Transformer correctement les coordonnées MLKit de l'espace caméra vers l'espace PreviewView pour un overlay ROI pixel-perfect.

## 📋 Problème Initial
- Les coordonnées MLKit sont dans l'espace caméra (640×480 landscape)
- Le PreviewView est en portrait (1080×2201) avec ScaleType FILL_CENTER
- Transformation directe donne des positions incorrectes à l'écran

## 🔍 Analyse Progressive

### Phase 1: Identification Axes Croisés
**Symptômes découverts:**
- QR bouge **BAS** → carré bouge **DROITE** ❌
- QR bouge **DROITE** → carré bouge **HAUT** ❌

**Cause:** Mauvaise rotation 90° dans la transformation

**Solution appliquée:** Suppression de la rotation (axes maintenant corrects)

### Phase 2: Problème Scaling FILL_CENTER
**Test 4 corners révélateur:**

| Position QR | Camera Y % | Display Y | Problème |
|-------------|------------|-----------|-----------|
| Haut Gauche | 12% | 279px | ✅ Correct |
| Haut Droit  | 8% | 183px | ✅ Correct |
| Bas Gauche  | 104% | 2311px | ❌ +500px trop bas |
| Bas Droit   | 113% | 2499px | ❌ +600px trop bas |

**Root Cause:** Coordonnées Y > 100% (hors cadre caméra) ignorent le crop FILL_CENTER

### Phase 3: Tentatives de Solutions

#### 3.1 Transformation FILL_CENTER Complex
- Calcul scaling uniforme + offsets crop
- **Résultat:** Coordonnées négatives, toujours incorrect

#### 3.2 Mapping Proportionnel Simple  
- Transformation directe normalized × preview dimensions
- **Résultat:** Axes corrects mais décalages persistants

#### 3.3 Recherche Solution Officielle
- Découverte **MlKitAnalyzer** avec `COORDINATE_SYSTEM_VIEW_REFERENCED`
- **Problème:** API non disponible/incompatible avec CameraX 1.4.0-rc01
- **Abandon:** Erreurs compilation `getValue()` / `getResult()`

## 🛠️ État Actuel - Architecture Implémentée

### Composants Créés

#### MLKitCoordinateTransformer.kt
```kotlin
// Transformation sans rotation (axes corrects)
val rotatedLeft = normalizedLeft
val rotatedTop = normalizedTop
val rotatedRight = normalizedRight  
val rotatedBottom = normalizedBottom

// Scaling proportionnel simple
val displayLeft = (rotatedLeft * previewWidth).toInt()
val displayTop = (rotatedTop * previewHeight).toInt()
```

#### MainActivity.kt - Diagnostic Enrichi
- **Logging décalage** pixel-perfect calculé
- **Analyse attendu vs réel** pour chaque détection
- **Intégration RoiOverlayView** avec coordonnées transformées

#### RoiOverlayView.kt - Debug Visuel
- **Grille référence 3×3** avec lignes vertes
- **9 points test P0-P8** pour analyse systématique
- **Overlay ROI bleu** avec informations debug

### Dependencies Ajoutées
```gradle
// CameraX 1.4.0-rc01 avec extensions
implementation "androidx.camera:camera-extensions:${camerax_version}"
```

## 📊 Outils de Diagnostic Disponibles

### Logging Automatique
```
=== DÉCALAGE ANALYSIS ===
Attendu centre: (expectedX, expectedY)
Réel centre: (actualX, actualY)
DÉCALAGE: X=offsetXpx, Y=offsetYpx
```

### Guide Visuel Écran
- ✅ Grille 3×3 pour positionnement précis
- ✅ 9 points référence P0-P8 marqués
- ✅ Carré ROI bleu superposé

## 🚀 Prochaines Étapes

### Test Systématique 9 Positions
1. **Tester QR** sur chaque point P0-P8 de la grille
2. **Collecter logs** de décalage pour chaque position
3. **Analyser pattern** des décalages (constant/linéaire/non-linéaire)
4. **Créer formule correction** basée sur les données

### Solutions Potentielles
- **Offset constant** : Simple addition/soustraction
- **Scaling non-uniforme** : Facteurs différents X/Y
- **Transformation matrice** : Matrice 2D complète si pattern complexe

## 🔧 État Technique

### Fichiers Modifiés
- `app/build.gradle` - Dependencies CameraX 1.4.0-rc01
- `MLKitCoordinateTransformer.kt` - Transformation sans rotation
- `MainActivity.kt` - Diagnostic décalages
- `RoiOverlayView.kt` - Grille référence visuelle

### Performance
- ✅ **Axes corrects** (QR bas → carré bas)
- ⚠️ **Décalages constants** à quantifier précisément
- ✅ **Architecture extensible** pour correction finale

### Buildable State
- ✅ Compilation sans erreurs
- ✅ Overlay ROI visible à l'écran
- ✅ Logs diagnostic complets
- 🔄 **Test 9 positions en cours** pour calibration finale

---
*Dernière mise à jour: 2025-08-21*
*Status: Diagnostic tools ready, calibration phase needed*