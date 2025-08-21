# T-107 : Agrégation Multi-ROI et Multi-Profils

## 🎯 Objectif
Sélectionner le meilleur candidat MSI parmi plusieurs ROI et profils.

## 🛠 Techniques
- Appliquer pipeline T-101 → T-106 à chaque ROI.
- Conserver 1 profil médian + quelques profils voisins.
- Score = stabilité(w) × % runs valides × SNR.
- Choisir meilleur candidat.

## ✅ Validation
- Toujours le même candidat retenu pour un MSI unique.
- Résultats cohérents entre frames successives.
