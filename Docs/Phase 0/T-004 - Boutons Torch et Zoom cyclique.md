# T-004 : Boutons Torch et Zoom cyclique

## 🎯 Objectif
Ajouter deux contrôles terrain :
1. **Torch (lampe)** : on/off pour éclairage en faible lumière.
2. **Zoom cyclique** : 1× → 2× → 3× → 1×…, en optique si dispo, sinon numérique.

---

## 🛠 Détails techniques

### Torch
- Toggle indépendant du Start/Stop.
- Auto-OFF à STOP ou à `onPause()`.
- UI : icône ampoule + état visuel ON/OFF.
- Utiliser `cameraControl.enableTorch(true|false)`.
- (Optionnel) Lock **AE/AF/AWB** après focus pour éviter variations de luminosité.

### Zoom cyclique
- État persistant (SharedPreferences).
- Cycle : `1.0× → 2.0× → 3.0× → 1.0×`.
- **Si téléobjectif dispo** :
  - Changer `CameraSelector` pour l’objectif télé (optique) plutôt que zoom numérique.
- **Sinon** : utiliser `cameraControl.setZoomRatio(ratio)`.
- Afficher dans overlay : `Zoom: 2.0× (optique|numérique)`.

---

## 💡 Points d’attention
- Zoom numérique : ne pas dépasser `maxZoomRatio` renvoyé par `cameraInfo`.
- Rebinding Preview + Analysis si changement d’objectif.
- Torch : vérifier disponibilité (`cameraInfo.hasTorchUnit()`).

---

## ✅ Critères d’acceptation
- Torch ON/OFF en direct, OFF automatique à STOP.
- Zoom cycle opérationnel, affichage correct dans overlay.
- Persistance : revenir avec même zoom/torch après redémarrage app.
- Aucun crash si zoom demandé > maxZoomRatio.