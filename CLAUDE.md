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
- **OS** : WSL Ubuntu (Windows Subsystem for Linux)
- **IDE** : Android Studio (Windows)
- **Build** : Gradle via Android Studio uniquement (pas de build CLI possible en WSL)
- **Test** : Device Android via Android Studio
- **Limitation Critique** : Aucun build Gradle possible depuis WSL - obligatoire d'utiliser Android Studio

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

## 📁 Architecture Projet & Organisation

### Structure Arborescence Réelle
```
/mnt/c/DEV/WORK/API_MSIDecoder/
├── CLAUDE.md                        # Documentation technique vivante
├── README.md                        # Documentation projet
├── build.gradle                     # Configuration racine Gradle
├── settings.gradle                  # Modules Gradle
│
├── Docs/                           # TOUTE la documentation
│   └── Phase 0/                    # Fiches techniques par phase
│       ├── Phase 0.MD              # Vision générale Phase 0
│       ├── T-001 - Mode Portrait Only + Preview CameraX.md
│       ├── T-001_Approved.md       # Validation T-001
│       ├── T-002 - ImageAnalysis + Overlay métriques.md
│       ├── T-002_Approved.md       # Validation T-002
│       ├── T-003 - Bouton Start Stop Scanner.md
│       ├── T-003_Approved.md       # Validation T-003
│       ├── T-004 - Boutons Torch et Zoom cyclique.md
│       ├── T-004_Approved.md       # Validation T-004 ✅
│       ├── T-005 - ML Kit Whitelist + Arbitre de résultats.md
│       ├── T-005_Approved.md       # Validation T-005 ✅
│       ├── T-006 - Persistance & Restauration d'état.md
│       ├── T-006_Approved.md       # Validation T-006 ✅
│       ├── T-007 - Overlay Snapshot JSON (debug ponctuel).md
│       └── T-007_Approved.md       # Validation T-007 ✅
│
├── Log/                            # Logs de debug
│   └── Log.txt                     # Fichier de logs courant
│
└── app/                            # Module Android principal
    ├── build.gradle                # Config module app
    ├── src/main/
    │   ├── AndroidManifest.xml     # Permissions, orientation
    │   ├── kotlin/com/msidecoder/scanner/
    │   │   ├── MainActivity.kt     # Activity principale
    │   │   ├── camera/
    │   │   │   └── YuvToNv21Converter.kt
    │   │   ├── debug/              # T-007: Debug & Snapshots
    │   │   │   ├── SnapshotData.kt
    │   │   │   └── SnapshotManager.kt
    │   │   ├── scanner/            # Scanner Pipeline
    │   │   │   ├── MLKitScanner.kt
    │   │   │   ├── MSIScanner.kt
    │   │   │   ├── ScannerArbitrator.kt
    │   │   │   └── ScanResult.kt
    │   │   ├── state/              # State Management
    │   │   │   ├── CameraControlsState.kt
    │   │   │   ├── PreferencesRepository.kt
    │   │   │   └── ScannerState.kt
    │   │   ├── ui/
    │   │   │   └── MetricsOverlayView.kt
    │   │   └── utils/
    │   │       └── MetricsCollector.kt
    │   └── res/
    │       ├── layout/
    │       │   └── activity_main.xml    # UI Layout principal
    │       ├── values/
    │       │   ├── colors.xml          # Couleurs (blue_tender)
    │       │   ├── strings.xml         # Textes i18n
    │       │   └── themes.xml          # Thème Material3
    │       └── drawable/               # Icônes vectorielles
    └── proguard-rules.pro
```

### Convention Documentation
- **Docs/Phase X/** : Fiches techniques `.md` + validations `_Approved.md`
- **Log/** : Fichiers de logs pour debugging sessions
- **CLAUDE.md** : Documentation technique centrale (ce fichier)
- **Approved.md** : Validation formelle des mini-lots terminés

### Contraintes Environnement WSL
- **Pas de build CLI** : `./gradlew build` impossible depuis WSL Ubuntu
- **Android Studio requis** : Seule méthode pour build/test/deploy
- **Partage fichiers** : `/mnt/c/` accès aux fichiers Windows
- **Logs externes** : Copie manuelle des logs Android Studio → `Log/Log.txt`
- **Git fonctionnel** : Commits/push possibles depuis WSL

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

### Mini-lots Terminés (7/7) ✅ **PHASE 0 COMPLÈTE**
- **T-001** ✅ : Infrastructure de base Android + CameraX Preview
- **T-002** ✅ : Pipeline analyse YUV + métriques temps réel  
- **T-003** ✅ : Contrôle START/STOP + gestion d'état
- **T-004** ✅ : Boutons Torch/Zoom + Persistance États
- **T-005** ✅ : ML Kit Whitelist + Arbitre Scanners
- **T-006** ✅ : Persistance & Restauration Complète
- **T-007** ✅ : **Overlay Snapshot JSON Debug (APPROVED)**

### T-007 : Debug Snapshot JSON Complet ⭐
- ✅ **Bouton "SS" explicite** : Click instantané bouton violet dédié (56x56dp)
- ✅ **Capture instantanée** : Snapshot JSON sans interruption scanner/caméra
- ✅ **Structure complète** : ts, res, fps, procMs, queue, rotation, torch, zoom, ml, msi, lastPub
- ✅ **Stockage intelligent** : `Downloads/MSISnapshots/` (Android 10+) ou internal fallback
- ✅ **Feedback UX** : Toast + vibration 100ms sur succès capture
- ✅ **JSON formaté** : Pretty-print 2 espaces pour lisibilité debug
- ✅ **Architecture** : SnapshotManager + Bouton SS + permissions + stockage adaptatif
- ✅ **Debug professionnel** : Snapshots accessibles depuis explorateur fichiers

## 🏆 PHASE 0 COMPLÈTE - Infrastructure MSI Decoder ✅

### Architecture Finale Solide
- **Dynamic binding** CameraX Preview + ImageAnalysis
- **State management** complet : Scanner + Camera + Persistance + Lifecycle
- **Scanner Pipeline** : ML Kit + MSI Arbitrator avec priorité et métriques
- **Persistance intelligente** : Auto-start + anti-republication + torch intention utilisateur
- **Debug professionnel** : Snapshots JSON instantanés sur demande (bouton "SS")
- **Performance** validée (FPS: 23, ML Kit: 15ms, debounce: 750ms, restore: <200ms)
- **UX** expérience continue : Kill/reboot/background transparent pour utilisateur
- **Infrastructure** prête pour MSI détection réelle Phase 1+

### Livrables Phase 0 Terminés
1. **Application Android fonctionnelle** avec scanning ML Kit opérationnel
2. **Architecture extensible** pour intégration MSI detector (stub → implémentation)
3. **Interface utilisateur complète** (controls + métriques + debug)
4. **Persistance bulletproof** (states + lifecycle + anti-republication)
5. **Documentation technique** complète (spécifications + validations)
6. **Debug tooling** professionnel (JSON snapshots)

### Transition vers Phase 1
L'infrastructure Phase 0 fournit :
- **Pipeline dual** prêt (ML Kit ✅ + MSI interface ✅)
- **Data structures** complètes (FrameNV21, rotationDeg, callbacks)
- **Performance baseline** établie (FPS, latency, queue metrics)
- **User experience** raffinée (controls, feedback, persistance)
- **Debug capabilities** (snapshot JSON avec tous états système)

**→ Phase 1 peut maintenant implémenter le détecteur MSI réel**

## 📋 Progression Phase 1

### Mini-lots Phase 1 (3/8) ✅ 
- **T-100** ✅ : **Diagnostic & Debug Snapshot (APPROVED)**
- **T-101** ✅ : **ROI Detection Anisotrope + Overlay Unifié (APPROVED)**
- **T-102** ✅ : **Orientation Estimation Structure Tensor (APPROVED)**

### T-100 : Monitoring System MSI ⭐
- ✅ **MsiDebugSnapshot** : Data classes pipeline stages + metrics complètes
- ✅ **MsiDebugManager** : Gestionnaire monitoring avec overlay status temps réel
- ✅ **Intégration T-007** : Extension SnapshotData avec champ `msiDbg` optionnel
- ✅ **Overlay optimisé** : Police réduite (/2) + ligne "MSI DEBUG: —"
- ✅ **Sauvegarde Downloads** : /Download/MSISnapshots/ accessible publiquement
- ✅ **Architecture extensible** : Pipeline stages T-101→T-109 ready
- ✅ **Foundation monitoring** : Debug professionnel pour Phase 1 complète

### T-101 : ROI Detection Revolutionary ⭐⭐⭐
- ✅ **Algorithme anisotrope** : Gradient énergie horizontal (Sobel X) + fermeture morphologique 1×15
- ✅ **MsiRoiDetector complet** : Pipeline NV21 → normalisation → gradient → morpho → bounding boxes
- ✅ **Critères MSI optimisés** : Ratio ≥3, quiet zones, seuil gradient 0.3, détection 150-250ms
- ✅ **Performance validée** : 0-9 candidats/frame, scores 0.32-1.18, timeout 250ms respecté
- ✅ **Debug integration** : RoiStats complètes dans JSON snapshots avec persistance 2.5s
- ✅ **Overlay MSI unifié RÉVOLUTIONNAIRE** : 80% largeur centré, section MSI orange/jaune dédiée
- ✅ **ROI persistance** : Détections visibles 2.5s (fini le flash invisible)
- ✅ **Interface temps réel** : Candidats, scores, coordonnées, temps affichés en continu

### T-102 : Orientation Estimation Structure Tensor ⭐⭐⭐
- ✅ **Structure Tensor** : Algorithme Gx², Gy², GxGy → ½ atan2(2·GxGy, Gxx−Gyy) 
- ✅ **OrientationEstimator complet** : Pipeline ROI → downsample → gradients Sobel → Structure Tensor → angle
- ✅ **Performance exceptionnelle** : 0-1ms par ROI, overhead negligible <5ms
- ✅ **Angles multiples validés** : -37.5°, +79.4°, -35.0°, +70.2°, etc. précision cohérente
- ✅ **Integration transparente** : RoiCandidate enrichi avec orientationDegrees automatique
- ✅ **Debug professionnel** : Logs Structure Tensor (Gxx, Gyy, Gxy) + JSON export angle
- ✅ **Overlay temps réel Enhanced** : "Orientation: X.X° (Structure Tensor)" en continu
- ✅ **Downsampling intelligent** : Bilinear interpolation pour ROI >32px, performance optimisée

### Prochains Mini-lots Phase 1
- **T-103** : Rectification Perspective (ROI + angle → image rectifiée)
- **T-104** : Profils Multi-Lignes Médiane
- **T-105** : Seuil 1D Adaptatif
- **T-106** : Récupération Horloge & Quantification
- **T-107** : Agrégation Multi-ROI et Multi-Profils
- **T-108** : Overlay & Snapshot Integration

---
*Document vivant mis à jour à chaque phase*
*Dernière révision: **T-102 ORIENTATION ESTIMATION APPROVED** - Structure Tensor Exceptional Performance (2025-08-16)*