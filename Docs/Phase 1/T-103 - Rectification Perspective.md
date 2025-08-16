# T-103 : Rectification Perspective

## 🎯 Objectif
Redresser la ROI pour obtenir des barres verticales parallèles.

## 🛠 Techniques
- Détection d’un quadrilatère (coins).
- Application `warpPerspective` → ROI normalisée (ex. 1024×256).
- Rotation inverse de l’angle détecté.
- Clamp + re-normalisation.

## ✅ Validation
- Barres quasi verticales (±2°).
- Aucun étirement extrême → ROI réaliste.
