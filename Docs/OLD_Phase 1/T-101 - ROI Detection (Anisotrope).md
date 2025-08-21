# T-101 : ROI Detection (Anisotrope)

## 🎯 Objectif
Détecter les zones candidates contenant un code barre MSI grâce à l’énergie de gradient orientée.

## 🛠 Techniques
- Entrée : `nv21` (luminance Y) + `rotationDeg`.
- Normalisation des intensités [0..1].
- Renforcement du contraste local (CLAHE light ou normalisation locale).
- Calcul du **gradient horizontal (Sobel X)** → met en évidence les barres verticales.
- Fermeture morphologique 1×k (k=9–21) pour regrouper les raies.
- Détection des bounding boxes :
  - ratio largeur/hauteur ≥ 3
  - variance du gradient significative
  - quiet zones plausibles (zones claires aux extrémités).
- Retourner 1–3 ROI avec score.

## ✅ Validation
- 1–3 ROI stables par frame.
- Faible taux de faux positifs.
