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
│       ├── T-006 - Persistance & Restauration d'état.md
│       └── T-007 - Overlay Snapshot JSON (debug ponctuel).md
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

### Mini-lots Terminés (5/7)
- **T-001** ✅ : Infrastructure de base Android + CameraX Preview
- **T-002** ✅ : Pipeline analyse YUV + métriques temps réel  
- **T-003** ✅ : Contrôle START/STOP + gestion d'état
- **T-004** ✅ : **Boutons Torch/Zoom + Persistance États (APPROVED)**
- **T-005** ✅ : **ML Kit Whitelist + Arbitre Scanners (APPROVED)**

### T-004 : Contrôles Caméra Complets
- ✅ **UI** : Boutons torch/zoom 56x56dp avec texte visible ("T", "1"/"2"/"3")  
- ✅ **Torch** : Toggle ON/OFF + inversion couleurs + auto-OFF au STOP scanner
- ✅ **Zoom** : Cyclique 1×→2×→3× respectant maxZoom caméra
- ✅ **Persistance** : SharedPreferences + restauration parfaite (même pause/resume)
- ✅ **Fix Critique** : MaterialButton insets supprimés pour affichage texte
- ✅ **Architecture** : CameraControlsManager + PreferencesRepository extensible

### T-005 : Scanner ML Kit + Arbitre MSI 
- ✅ **ML Kit Integration** : Whitelist DataMatrix, EAN-13/8, Code-128, QR
- ✅ **MSI Scanner Stub** : Interface complète prête Phase 1+
- ✅ **Arbitrateur** : ML Kit prioritaire → MSI fallback + métriques temps réel
- ✅ **Pipeline complet** : ImageAnalysis → Arbitre → Callback + debounce 750ms
- ✅ **Overlay pro** : `ML: 15ms, hits: 3` + `SRC: none/ML_KIT` (timeout 1s)
- ✅ **Performance** : FPS stable 23+, async processing, cleanup lifecycle

### Prochains Mini-lots
- **T-006** : ~~Persistance SharedPreferences~~ → Extension persistance + config avancée
- **T-007** : Overlay snapshot JSON debug + lifecycle complet

### Architecture Solide Renforcée
- **Dynamic binding** CameraX Preview + ImageAnalysis
- **State management** complet : Scanner + Camera + Persistance
- **Scanner Pipeline** : ML Kit + MSI Arbitrator avec priorité et métriques
- **Performance** validée (FPS: 23, ML Kit: 15ms, debounce: 750ms)
- **UX** complète avec contrôles intuitifs + overlay professionnel temps réel
- **Infrastructure** prête pour MSI détection réelle Phase 1+

---
*Document vivant mis à jour à chaque phase*
*Dernière révision: Phase 0 - T-005 APPROVED (2025-08-15)*