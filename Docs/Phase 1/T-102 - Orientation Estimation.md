# T-102 : Orientation Estimation

## 🎯 Objectif
Estimer l’angle d’inclinaison du code barre dans chaque ROI.

## 🛠 Techniques
- Calcul du **structure tensor** (Gx², Gy², GxGy) sur la ROI downsamplée.
- Angle = ½ atan2(2·GxGy, Gx²−Gy²).
- Alternative : Hough lines light sur crêtes du gradient.
- Moyenne ou médiane des angles sur la ROI.

## ✅ Validation
- Angle estimé avec précision ±2–3°.
- Overlay affiche un angle cohérent avec la réalité.
