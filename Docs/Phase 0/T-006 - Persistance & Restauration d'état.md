# T-006 : Persistance & Restauration d'état

## 🎯 Objectif
Mémoriser et restaurer automatiquement l'état de l'app (scanner, torch, zoom, etc.)
après multitâche, mise en veille, kill/relaunch, voire redémarrage du smartphone.

## 🛠 Détails techniques

### Stockage (SharedPreferences)
Clé/valeurs recommandées (types simples) :
- `scannerState` : "ACTIVE" | "STOPPED"
- `torchState`   : true | false
- `zoomRatio`    : float (1.0 / 2.0 / 3.0)
- `zoomType`     : "optique" | "numerique"
- `lastResult`   : string (dernier code publié)
- `lastResultTs` : long (epoch ms)
- `aeLocked`     : true | false (optionnel, si géré)
- (Phase 1+) `msiConfig.*` : paramètres du décodeur (facultatif)

### Écriture
- Sauvegarder **immédiatement** à chaque changement d’état (click START/STOP, torch, zoom).
- Ne pas attendre `onPause()` (Android peut tuer l’app sans prévenir).

### Restauration
- À `onCreate()` (ou `onResume()` si plus pratique) :
  - Relire toutes les prefs et mettre à jour l’UI (libellés/icônes des boutons, overlay).
  - Tenter de restaurer `torch` et `zoom`. Si capteur indisponible (ex: télé), fallback propre (1.0× numérique).
  - Si `scannerState == ACTIVE` :
    - Vérifier disponibilité caméra.
    - Relancer l’analyse (START silencieux).
    - **Ne pas** republisher `lastResult` immédiatement (éviter bip).

### Cas limites
- Caméra occupée → basculer `scannerState=STOPPED` + message overlay.
- Permissions révoquées → écran explicatif, rester en STOP.
- Objectif télé indisponible → fallback numérique + note overlay.

## 💡 Points d’attention
- Définir une **option** (préférence) "Toujours démarrer en STOP" (false par défaut).
- Anti-bip au lancement : ignorer les détections du **même code** que `lastResult` pendant 800 ms.
- Torch : l’éteindre automatiquement à STOP et à `onPause()`.

## ✅ Critères d’acceptation
- Kill/relaunch : l’app revient **exactement** au même état (UI + caméra), ou STOP si indisponible.
- Reboot device : mêmes prefs restaurées.
- Aucun crash si capteur/zoom non supportés → fallback propre.
- Aucune repub intempestive au démarrage (pas de beep/vibe).