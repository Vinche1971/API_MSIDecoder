# T-104 : Profils Multi-Lignes (Médiane)

## 🎯 Objectif
Extraire un profil 1D robuste via médiane de plusieurs lignes horizontales.

## 🛠 Techniques
- Sélectionner 16–32 lignes centrales (35–65% de la hauteur).
- Pour chaque x : calcul de la **médiane**.
- Option : lisser légèrement (gaussien σ=1–2).
- Générer `profileMedian` et éventuellement profils voisins.

## ✅ Validation
- Profil médian montre des transitions nettes (barres bien visibles).
