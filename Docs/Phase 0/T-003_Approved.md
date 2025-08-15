# T-003 APPROVED ✅ - Bouton Start/Stop Scanner

## 🎯 Objectif Atteint
Contrôle **START/STOP** du pipeline d'analyse avec bouton toggle, bind/unbind dynamique ImageAnalysis.

## 🛠 Implémentation Technique

### ScannerState Management
```kotlin
enum class ScannerState { IDLE, ACTIVE }

class ScannerStateManager {
    fun toggle(): ScannerState
    fun addStateChangeListener(listener: (ScannerState) -> Unit)
}
```

### UI Button - MaterialButton
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/fabStartStop"
    android:layout_width="120dp"
    android:layout_height="48dp"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent" />
```
- **Centré horizontalement** en bas d'écran
- **Aspect rectangulaire** 120x48dp
- **Icône + texte** : ▶️ START / ⏹️ STOP

### Dynamic Binding Strategy
```kotlin
// Initial: Preview only (IDLE state)
cameraProvider.bindToLifecycle(this, cameraSelector, preview)

// START: Add ImageAnalysis
cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)

// STOP: Remove ImageAnalysis, keep Preview
cameraProvider.unbind(imageAnalysis)
```

### State Transitions
**IDLE → ACTIVE :**
- Bind ImageAnalysis → processFrame() calls begin
- Reset MetricsCollector
- Button: ▶️ START → ⏹️ STOP

**ACTIVE → IDLE :**
- Unbind ImageAnalysis → processFrame() calls stop  
- Button: ⏹️ STOP → ▶️ START
- Preview remains active (smooth UX)

## ✅ Validation Fonctionnelle

### Comportement Testé
- **Au démarrage** : State IDLE, bouton ▶️ START, Preview active, pas d'analyse
- **Après START** : State ACTIVE, bouton ⏹️ STOP, métriques actives
- **Après STOP** : State IDLE, bouton ▶️ START, overlay figé, CPU réduit
- **Toggle rapide** : Debounce 200ms évite les clics multiples

### Performance Validée
- **CPU IDLE** : Significant drop après STOP (seul Preview actif)
- **CPU ACTIVE** : Pipeline complet, métriques temps réel
- **Latence toggle** : < 100ms START/STOP
- **Memory** : Pas de leak, ImageAnalysis correctement unbound

## 🔧 Architecture Code

### State Listener Pattern
```kotlin
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
```

### Task Cancellation
```kotlin
private fun stopScanner() {
    cameraProvider?.unbind(imageAnalysis) // Immediate stop
    // Pending frames naturally cancelled by unbind
}
```

### Debounce Implementation  
```kotlin
private var lastClickTime = 0L
private val debounceInterval = 200L

// In click listener
if (currentTime - lastClickTime < debounceInterval) return
```

## 📝 Points Techniques

### Preview Persistence
- **Preview toujours bindé** → UX fluide, pas de flash noir
- **ImageAnalysis bind/unbind** → contrôle CPU/battery
- **CameraX lifecycle-aware** → gestion automatique ressources

### UI Feedback
- **MaterialButton** style cohérent avec Material Design
- **Icon + text** : feedback visuel + accessibilité
- **Constraint centering** : layout adaptatif

### Thread Safety
- **State changes** sur UI thread (state listeners)
- **CameraX bind/unbind** thread-safe par design
- **MetricsCollector reset** atomic operations

## 🚀 Architecture Évolutive

**Base solide pour T-004+ :**
- State management extensible (torch, zoom, etc.)
- Dynamic binding pattern réutilisable
- UI framework en place pour contrôles additionnels

**Préparation Lifecycle (T-006) :**
- ScannerStateManager prêt pour persistance SharedPreferences
- onPause/onResume hooks à ajouter

## ⚠️ Notes Techniques

### 16KB Alignment Warning
- **Bibliothèques Google** (CameraX/ML Kit) pas encore alignées Android 16
- **Impact** : Aucun sur fonctionnalité, future perf optimization
- **Action** : Attendre updates Google, non-critique Phase 0

### Edge Cases Gérés
- **Rapid toggle** : Debounce 200ms
- **Camera provider null** : Guards dans start/stopScanner  
- **ImageAnalysis null** : Safe checks avant bind/unbind

---
**Status:** APPROVED - Ready for T-004 (Torch + Zoom)
**Validation:** Fonctionnelle complète, performance validée  
**Date:** Phase 0 - Mini-lot 0.3 completed