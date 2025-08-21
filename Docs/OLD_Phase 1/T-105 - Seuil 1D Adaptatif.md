# T-105 : Seuil 1D Adaptatif

## 🎯 Objectif
Binariser le profil 1D avec un seuil local pour détecter les transitions.

## 🛠 Techniques
- Seuil adaptatif glissant (fenêtre 31–61 px).
- Détection des pics de gradient → transitions Bar/Espace.
- Génération d’une séquence `runsPx = [(isBar, widthPx), ...]`.

## ✅ Validation
- Alternance correcte Bar/Espace.
- Peu de double transitions.
