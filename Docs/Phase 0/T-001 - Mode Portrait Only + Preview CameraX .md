# T-001 : Mode Portrait Only + Preview CameraX

## 🎯 Objectif
Mettre en place l’affichage de la caméra en mode **portrait verrouillé** via CameraX avec un `PreviewView` plein écran.

## 🛠 Détails techniques
- **Orientation forcée** :
  - Dans `AndroidManifest.xml` : `android:screenOrientation="portrait"`.
  - Bloquer la rotation dans l’Activity principale.
- **CameraX Preview** :
  - Utiliser `PreviewView` (AndroidX).
  - Configurer `Preview.Builder()` avec :
    - `setTargetRotation(Surface.ROTATION_0)` (portrait).
    - Ratio 16:9 ou adapté au device.
  - Fournir le `SurfaceProvider` du `PreviewView`.
- **Permissions** :
  - Demander `CAMERA` au runtime.
  - Si refus → afficher un écran explicatif et fermer l’app.

## 💡 Points d’attention
- Tester sur un appareil qui pivote physiquement : l’orientation doit rester en portrait, sans rotation de l’aperçu.
- Utiliser un `ConstraintLayout` ou `FrameLayout` pour positionner le `PreviewView` en plein écran.
- Prévoir l’emplacement futur des boutons et overlay.

## ✅ Critères d’acceptation
- L’app démarre en portrait, aucune rotation possible.
- La preview caméra s’affiche en plein écran, fluide.
- En cas de refus de permission : message clair + fermeture.