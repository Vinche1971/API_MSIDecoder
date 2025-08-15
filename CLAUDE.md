# CLAUDE.md - Observations Techniques MSI Decoder Android

## 🎯 Compréhension du Projet

**Objectif Principal**: Scanner double pipeline (ML Kit + MSI propriétaire) pour terminal d'inventaire Android en mode portrait uniquement.

**Architecture Cible**: Module library intégrable, optimisé pour usage terrain avec robustesse et performance.

## 📋 Analyse Technique

### Technologies Identifiées
- **CameraX** : ImageAnalysis + Preview avec KEEP_ONLY_LATEST
- **ML Kit Barcode Scanning** : Whitelist (DataMatrix, EAN-13/8, Code-128, QR)
- **Kotlin** : Langage principal
- **AndroidX** : UI et lifecycle management
- **SharedPreferences** : Persistance d'état

### Environnement de Développement
- **IDE** : Android Studio (Windows)
- **Build** : Gradle via Android Studio uniquement (pas de build CLI en WSL)
- **Test** : Device Android via Android Studio

### Points Techniques Critiques
1. **Conversion YUV→NV21** : Une seule conversion par frame, fermeture immédiate ImageProxy
2. **Pipeline Dual** : Arbitre ML Kit prioritaire → MSI en fallback
3. **Performance** : Target ≤50ms/frame, executor dédié single-thread
4. **Orientation** : Invariance 0-360° plan, tolérance ±25-30° hors-plan
5. **Debounce** : 700-800ms pour éviter les publications multiples

### Structure Phases Validée
- **Phase 0** (Infrastructure) : 7 mini-lots T-001 à T-007
  - ✅ **T-001 COMPLETED** : Portrait-only + CameraX Preview + permissions (AGP 8.12.0)  
  - ✅ **T-002 COMPLETED** : ImageAnalysis + YUV→NV21 + overlay métriques (FPS: 23, Proc: 2.8ms)
  - ✅ **T-003 COMPLETED** : Boutons START/STOP + bind/unbind dynamique + state management
- **Phase 1-8** : Progression MSI détection → décodage → packaging AAR

## 🔧 Recommandations d'Implémentation

### Phase 0 - Priorités
1. **T-001** : Robustesse permissions caméra + gestion échecs
2. **T-002** : Métriques temps réel performantes (max 10Hz refresh)
3. **T-003** : Lifecycle bulletproof (pause/resume/multitask)
4. **T-005** : Gestion téléobjectif vs zoom numérique intelligente

### Architecture Code Suggérée
```
app/
├── camera/
│   ├── CameraController.kt
│   ├── ImageAnalyzer.kt
│   └── FrameConverter.kt
├── scanner/
│   ├── MLKitScanner.kt
│   ├── MSIScanner.kt (stub → complet)
│   └── ScannerArbitrator.kt
├── ui/
│   ├── MainActivity.kt
│   ├── OverlayManager.kt
│   └── ControlsManager.kt
├── state/
│   ├── AppStateManager.kt
│   └── PreferencesRepository.kt
└── utils/
    ├── PermissionHandler.kt
    └── MetricsCollector.kt
```

### Patterns Recommandés
- **StateFlow/LiveData** pour états réactifs
- **Coroutines** avec timeout pour pipeline MSI
- **Sealed classes** pour résultats scanner
- **Repository pattern** pour persistance
- **Factory pattern** pour configuration camera

## ⚠️ Points d'Attention

### Performance
- Surveiller memory leaks sur ImageProxy non fermés
- Timeout MSI impératif (40-60ms max)
- Profiling CPU/battery en usage prolongé

### Edge Cases Identifiés
- Caméra occupée par autre app
- Permissions révoquées en runtime
- Téléobjectif indisponible après restauration
- Rotation device pendant scan actif

### Tests Critiques
- Lifecycle complet (background/foreground/kill/reboot)
- Performance sous charge (scan continu 30+ min)
- Edge cases caméra/permissions
- MSI orientations multiples (0°,90°,180°,270°)

## 🚀 Stratégie de Développement

### Validation Continue
Chaque mini-lot (0.1→0.6) doit être **livrable et testable en 5-20min** avec critères d'acceptation stricts.

### Métriques de Réussite Phase 0
- FPS stable 25-30
- Latence pipeline < 15ms
- Queue Analysis ≈ 0
- Restauration état 100% fidèle
- Zero crash lifecycle

### Préparation Phase 1+
Le stub MSI recevra déjà la signature complète (FrameNV21 + rotationDeg) pour intégration transparente du détecteur ROI.

## 📝 Notes d'Architecture

### Extensibilité
- Interface MSIDecoder abstraite pour phases futures
- Plugin architecture pour overlay metrics
- Configuration runtime via flags développeur

### Intégration Future
- API stable pour module AAR
- WebView bridge pour terminal Pharmony
- Logs structurés pour debugging terrain

## 📋 Progression Phase 0

### Mini-lots Terminés (3/7)
- **T-001** ✅ : Infrastructure de base Android + CameraX Preview
- **T-002** ✅ : Pipeline analyse YUV + métriques temps réel  
- **T-003** ✅ : Contrôle START/STOP + gestion d'état

### Prochains Mini-lots
- **T-004** : Boutons Torch + Zoom cyclique (1×→2×→3×)
- **T-005** : ML Kit whitelist + arbitre MSI stub
- **T-006** : Persistance SharedPreferences + restauration état
- **T-007** : Overlay snapshot JSON debug + lifecycle complet

### Architecture Solide Établie
- **Dynamic binding** CameraX Preview + ImageAnalysis
- **State management** extensible pour contrôles futures  
- **Performance** validée (FPS: 23, Proc: 2.8ms)
- **UX** fluide avec feedback temps réel

---
*Document vivant mis à jour à chaque phase*
*Dernière révision: Phase 0 - T-003 Completed*