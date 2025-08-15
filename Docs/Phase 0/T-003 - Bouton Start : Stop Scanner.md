# T-003 : Bouton Start / Stop Scanner

## 🎯 Objectif
Permettre à l’utilisateur d’activer/désactiver le pipeline d’analyse (ML Kit + MSI stub) avec un bouton unique (toggle).

## 🛠 Détails techniques
- **État global** : `scannerState = ACTIVE|STOPPED`.
- **START** :
  - Binder `ImageAnalysis` à la lifecycle.
  - Réinitialiser métriques (fps, proc, queue).
  - Préparer ML Kit (pré-chauffage).
- **STOP** :
  - Unbind `ImageAnalysis` **ou** flag interne `scanning=false`.
  - Annuler toutes les tâches pendantes (futures MSI, callbacks ML Kit).
  - Éteindre torch si allumée.
- **UI** :
  - Icône ▶︎ pour START, ■ pour STOP.
  - Debounce tactile : 200 ms.

## 💡 Points d’attention
- Lifecycle :
  - À `onPause()` → passer automatiquement à STOP.
  - À `onResume()` → restaurer état précédent si config le demande.
- En STOP, CPU doit redescendre significativement (vérifiable via Profiler).
- Éviter toute fuite de ressources (threads, callbacks ML Kit).

## ✅ Critères d’acceptation
- En START : preview + analyse fonctionnent, overlay bouge.
- En STOP : overlay figé, CPU bas, pas de traitement en arrière-plan.
- Aucun crash en enchaînant START→STOP→START rapidement.