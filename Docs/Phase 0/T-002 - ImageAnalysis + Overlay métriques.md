# T-002 : ImageAnalysis + Overlay métriques

## 🎯 Objectif
Ajouter un `ImageAnalysis` CameraX pour traiter les frames YUV et afficher en temps réel des métriques sur un overlay.

## 🛠 Détails techniques
- **ImageAnalysis** :
  - Format : `YUV_420_888`.
  - Stratégie : `KEEP_ONLY_LATEST`.
  - Rotation : `setTargetRotation(Surface.ROTATION_0)` (portrait).
  - Conversion YUV → NV21 (ByteArray).
  - Fermeture immédiate de `ImageProxy` après copie.
- **Executor** :
  - Thread dédié à l’analyse (`Executors.newSingleThreadExecutor()`).
- **Overlay** :
  - Affiche :
    - FPS (EMA lissée)
    - Latence traitement (`Proc ms`)
    - Résolution (`WxH`)
    - Frames en attente (`Queue`)
  - Rafraîchissement max : 10 Hz.
  - Fond semi-transparent pour lisibilité.

## 💡 Points d’attention
- Pas de logs console pour ces métriques → tout passe par l’overlay.
- Calcul FPS :
  - Échantillonner sur 1 seconde, puis lisser.
- Mesure latence : début/fin traitement de chaque frame.
- Gérer `Queue` via compteur in-flight.

## ✅ Critères d’acceptation
- Overlay visible en permanence, chiffres stables.
- FPS ≈ 25–30 en continu.
- Latence (`Proc ms`) stable et basse (< 15 ms pour stub).
- Queue ≈ 0 en usage normal.