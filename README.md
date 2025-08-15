# MSI Barcode Experimental Scanner

## 📌 Vision du projet
Cette application Android expérimentale transforme un smartphone en **terminal d’inventaire** capable de :
- **Scanner les codes-barres standards** (DataMatrix GS1, EAN-13, EAN-8, Code-128, QR Code) via **ML Kit**.
- **Lire les codes MSI** grâce à un **décodeur propriétaire intégré** (développé sur mesure).
- **Combiner** les deux pipelines avec un **arbitre** qui publie le meilleur résultat.
- Fournir un **overlay temps réel** pour afficher métriques et états.
- Être **intégrable facilement** comme module (library) dans une application plus large (ex. WebView Pharmony).

L’application est verrouillée en **mode portrait**, optimisée pour un usage **terrain** :
- **Simplicité** : 3 boutons principaux (Start/Stop, Torch, Zoom).
- **Robustesse** : persistance d’état même après redémarrage.
- **Performance** : faible latence, consommation CPU maîtrisée.
- **Extensibilité** : architecture modulaire en phases.

---

## 🎯 Objectifs techniques
1. Fournir un **scanner double pipeline** :
   - **ML Kit** pour les formats supportés.
   - **MSI Decoder** pour les codes MSI (non pris en charge nativement).
2. Assurer une **invariance d’orientation 0–360°** dans le plan de l’image.
3. Tolérer une inclinaison hors-plan jusqu’à **~25–30°** avec correction perspective.
4. Maintenir une **latence de traitement** ≤ 50 ms/frame sur matériel cible.
5. Gérer **start/stop scan** à la volée, torch, zoom cyclique.
6. Sauvegarder et restaurer l’état complet de l’application.
7. Afficher **métriques en temps réel** (FPS, latence, état cam, zoom, torch).

---

## 📦 Architecture
### Composants principaux
- **UI Layer** : Activity/Fragment avec PreviewView, overlay et contrôles.
- **Camera Layer** : CameraX (Preview + ImageAnalysis), config portrait, `KEEP_ONLY_LATEST`.
- **ML Kit Layer** : BarcodeScanner avec whitelist (DataMatrix, EAN13/8, Code128, QR).
- **MSI Layer** : stub (Phase 0) → décodeur complet (Phase 1+).
- **Arbitre** : décide quelle source publie le résultat.
- **State Manager** : persistance/restauration via SharedPreferences.
- **Overlay Manager** : affichage métriques temps réel (rafraîchi 10 Hz max).

---

## 📅 Roadmap (Phases)

### Phase 0 – Infrastructure & UI de base
- Mode portrait, CameraX, Preview + Analysis (YUV→NV21)
- ML Kit whitelist + MSI stub + arbitre
- Boutons :
  - Start/Stop (toggle scanner)
  - Torch (on/off)
  - Zoom cyclique (1×/2×/3×, optique si dispo)
- Overlay métriques temps réel
- Persistance/restauration état complet
- Gestion lifecycle & multitâche

### Phase 1 – Détection ROI MSI
- Détecteur ROI sans OpenCV (Sobel 1D + morpho light)
- Filtrage par ratio d’aspect + variance
- Estimation orientation (structure tensor/Hough light)
- Overlay ROI + angle

### Phase 2 – Rectification & Profil
- Warp perspective + rotation verticale barres
- Extraction multi-lignes centrales
- Profil médian + lissage + dérivée

### Phase 3 – Binarisation 1D & Runs
- Seuil local glissant (adaptatif)
- Extraction runs bar/space avec largeur en px
- Polarity detection

### Phase 4 – Estimation module & Quantification
- Estimation module 1× (histogramme ou autocorrélation)
- Quantification runs → séquences normalisées

### Phase 5 – Décodage MSI (FSM)
- Tables MSI (rapports bar/space)
- Checksum Mod10 (puis Mod11 si config)
- Quiet zone checks
- Multi-hypothèses si ambiguïtés

### Phase 6 – Agrégation & Score
- Fusion multi-lignes
- Score de confiance
- Publication conditionnelle au seuil

### Phase 7 – Garde-fous & UX
- Indication tilt/distance/quiet zone
- Anti-flou (variance Laplacien)
- Conseils dynamiques sur l’overlay

### Phase 8 – Packaging Library
- Extraction du décodeur MSI en module AAR indépendant
- API stable (`process(FrameNV21) → DecodeResult`)
- Doc intégration

---

## 🔧 Technologies
- **Langage** : Kotlin
- **Caméra** : AndroidX CameraX
- **ML** : Google ML Kit Barcode Scanning
- **UI** : AndroidX, ConstraintLayout
- **Persistance** : SharedPreferences
- **Optionnel** (Phase 2+) : OpenCV pour accélérer détection/warp

---

## 🧪 Tests
- **Unit tests** : FSM MSI, checksum, quantification runs
- **Instrumented tests** : images synthétiques MSI, conditions réelles (éclairage, angle, bruit)
- **Perf tests** : FPS, latence moyenne, stabilité queue

---

## 🚀 Livraison
- **Mode expérimental** : APK debug signé, logs activés via overlay
- **Intégration finale** : MSI Decoder packagé en AAR, intégré à l’app terminal d’inventaire Pharmony

---

## 📌 Notes importantes
- Mode portrait only
- Codes MSI souvent horizontaux → pipeline capable de corriger orientation
- Tolérance hors-plan limitée (~25–30°)
- Quiet zone minimale ~10 modules
- Module width optimal : 3–6 px/barre fine


                 ┌──────────────────────────────────────────┐
                 │        CameraX (Preview + Analysis)       │
                 │  Portrait-only, YUV_420_888, KEEP_LATEST  │
                 └──────────────────────────────────────────┘
                                   │
                                   ▼
                 ┌──────────────────────────────────────────┐
                 │       Conversion unique YUV → NV21        │
                 │ RotationDeg conservée pour MLKit & MSI    │
                 └──────────────────────────────────────────┘
                      │                          │
                      │                          │
          ┌───────────▼───────────┐    ┌─────────▼─────────┐
          │    ML Kit Scanner     │    │   MSI Pipeline    │
          │ Whitelist: DM, EAN13, │    │  (Phase 0: stub   │
          │ EAN8, Code128, QR     │    │   → Phase 1–6)    │
          └───────────┬───────────┘    └─────────┬─────────┘
                      │                          │
                      └───────────┬──────────────┘
                                  ▼
                     ┌─────────────────────────────┐
                     │          Arbitre             │
                     │ - Publie MLKit si résultat   │
                     │ - Sinon tente MSI            │
                     └───────────┬─────────────────┘
                                  │
                                  ▼
                     ┌─────────────────────────────┐
                     │     Publication résultat     │
                     │  - Debounce 700–800 ms       │
                     │  - Beep + haptique           │
                     │  - Source tag (MLKit|MSI)    │
                     └───────────┬─────────────────┘
                                  │
                                  ▼
                     ┌─────────────────────────────┐
                     │     WebView / Terminal       │
                     │    d’inventaire final        │
                     └─────────────────────────────┘

───────────────────────────────────────────────────────────────
Pipeline MSI (Phases 1 → 6)
───────────────────────────────────────────────────────────────
1. Détection ROI (Sobel 1D + morpho, sans OpenCV au début)
2. Orientation (structure tensor / Hough)
3. Rectification (warp perspective + rotation barres verticales)
4. Extraction profils 1D (multi-lignes centrales)
5. Binarisation adaptative + runs bar/space
6. Estimation module width + quantification
7. FSM MSI + checksum (Mod10/Mod11)
8. Agrégation multi-lignes + score
───────────────────────────────────────────────────────────────

───────────────────────────────────────────────────────────────
UI & Contrôles
───────────────────────────────────────────────────────────────
- Bouton Start/Stop : active/désactive l’analyse
- Bouton Torch : on/off, auto-off à STOP
- Bouton Zoom cyclique : 1× → 2× → 3× (optique si dispo)
- Overlay métriques (10 Hz max) :
    FPS, Proc ms, Res, Queue
    Torch: ON/OFF
    Zoom: ratio + type
    ML: latence ms, hits
    MSI: latence ms ou —
    SRC: source publiée
───────────────────────────────────────────────────────────────

───────────────────────────────────────────────────────────────
Persistance & Lifecycle
───────────────────────────────────────────────────────────────
- SharedPreferences :
    scannerState (ACTIVE/STOPPED)
    torchState
    zoomState (+ type)
    lastResult + timestamp
    (option) AE/AF/AWB locked
- Restauration auto à onCreate/onResume
- Gestion mise en veille, multitâche, reboot
───────────────────────────────────────────────────────────────