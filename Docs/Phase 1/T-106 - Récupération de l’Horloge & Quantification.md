# T-106 : Récupération de l’Horloge & Quantification

## 🎯 Objectif
Déterminer la largeur de module de base et quantifier les runs.

## 🛠 Techniques
- Histogramme des runs courts ou autocorrélation pour estimer `wPx`.
- Quantification relative (tolérance ±35%).
- Correction progressive (moyenne mobile).
- Retourner `runsQuant = [1,2,1,1,...]`.

## ✅ Validation
- `wPx` stable.
- ≥70% des runs correctement quantifiés.
