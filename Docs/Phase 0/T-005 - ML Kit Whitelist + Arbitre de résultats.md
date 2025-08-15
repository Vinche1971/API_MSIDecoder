# T-005 : ML Kit Whitelist + Arbitre de résultats

## 🎯 Objectif
Mettre en place le pipeline ML Kit pour lire les formats supportés et publier les résultats via un arbitre.
Le MSI n’étant pas supporté, il reste traité par notre pipeline maison (stub pour l’instant).

---

## 🛠 Détails techniques

### ML Kit
- BarcodeScannerOptions avec whitelist :
  - `Barcode.FORMAT_DATA_MATRIX`
  - `Barcode.FORMAT_EAN_13`
  - `Barcode.FORMAT_EAN_8`
  - `Barcode.FORMAT_CODE_128`
  - `Barcode.FORMAT_QR_CODE`
- Création InputImage :
  - `InputImage.fromByteArray(nv21, w, h, rotationDeg, InputImage.IMAGE_FORMAT_NV21)`
- Traitement asynchrone via `process()`.

### Arbitre
- Si ML Kit détecte au moins 1 code (≠ MSI) :
  - Publier immédiatement (beep + haptique + debounce 700–800 ms).
- Sinon :
  - Essayer le pipeline MSI (Phase 0 = stub → aucun résultat publié).
- Overlay :
  - `ML: xx.x ms, hits: n`
  - `SRC: MLKit` si résultat publié.
  - `MSI: —` en Phase 0.

---

## 💡 Points d’attention
- Toujours passer `rotationDeg` à ML Kit.
- Ne pas bloquer la caméra en attendant MSI → exécuter MSI en parallèle sur executor dédié.
- Si les deux pipelines trouvent un code en même temps :
  - Priorité ML Kit si format dans whitelist.
- Debounce : empêcher publication répétée d’un même code pendant 700–800 ms.

---

## ✅ Critères d’acceptation
- Les formats whitelist sont lus correctement par ML Kit.
- Les autres formats (dont MSI) passent au pipeline MSI (même si stub).
- Publication source=MLKit visible dans overlay.
- Aucun freeze, FPS stable, latence affichée correcte.