
# T-100 : MSI Debug Harness & Hooks (préparation)

## 🎯 Objectif
Installer tout le **plancher commun** nécessaire aux tâches T-101 → T-109 :
- Schéma JSON `msiDbg` (snapshot T-007) figé,
- Slot d’overlay pour afficher un résumé MSI,
- Préférences/config MSI (paramètres tunables sans recompiler),
- Pipeline stub **synchronisé** (réception frame + rotation + budget temps),
- Points de mesure perf (latences) et sanitation (timeouts).

---

## 🧩 Livrables

1) **Schéma JSON `msiDbg`** (à insérer dans le snapshot T-007)
```json
"msiDbg": {
  "phase": "T100",
  "frame": { "w": 0, "h": 0, "rotationDeg": 0 },
  "perf":  { "procMs": 0.0, "fps": 0.0, "queue": 0 },
  "config": {
    "roiMax": 3,
    "quantTol": 0.35,
    "lines": 24,
    "timeoutMs": 50
  },
  "roiCount": 0,
  "roiChosen": null,
  "rois": [],
  "angleDegEst": null,
  "wPx": null,
  "runsPxCount": null,
  "runsQuantSample": null,
  "notes": []
}
````

> **Remarque** : Les clés non renseignées par T-100 restent `null` et seront remplies par T-101+.

2. **Overlay MSI (ligne dédiée)**
   Affichage minimal, rafraîchi ≤ 10 Hz :

```
MSI: —                         // tant qu’aucune info
```

Clé d’overlay à réserver (ex. `overlay.setMsiLine(text)`).

3. **Préférences MSI (SharedPreferences)**

* `msi.roiMax` (int, défaut 3)
* `msi.quantTol` (float, défaut 0.35)
* `msi.lines` (int, défaut 24)
* `msi.timeoutMs` (int, défaut 50)

4. **Stub pipeline MSI synchronisé**

* Méthode publique (lib) :

  ```kotlin
  fun process(frame: FrameNV21, rotationDeg: Int): MsiStubResult
  ```
* Implémentation T-100 :

  * Ne fait **rien** d’algorithmiquement significatif.
  * Remplit uniquement `msiDbg.phase = "T100"`, `frame`, `perf`, `config`.
  * Respecte **timeout** : si `> timeoutMs`, retourner statut `TIMEOUT`.

5. **Mesures perf**

* `procMs` calculé pour MSI stub,
* Passage des métriques au snapshot T-007 (fusion).

---

## 🛠 Détails d’implémentation

* **Entrée** : réutiliser la conversion NV21 de Phase 0, fermer `ImageProxy` immédiatement.
* **Rotation** : passer `rotationDeg` tel quel au stub MSI (il sera utilisé en T-101+).
* **Budget temps** : horodater au début/fin du stub, couper à `timeoutMs`.
* **Throttle overlay** : ne pas rafraîchir > 10 Hz.
* **Intégration snapshot** :

  * Étendre l’objet JSON existant avec la clé `msiDbg` (si absente, la créer).
  * S’assurer que la sauvegarde snapshot ne bloque pas le thread d’analyse (I/O en background).

---

## 💡 Points d’attention

* **Zéro log console** en continu (tout passe par overlay + snapshot ponctuel).
* Prévoir un **champ `notes`** (liste de strings) pour signaler facilement un état (ex. `"timeout"`, `"cameraBusy"`).
* Garder le **même ordre** et **les mêmes noms de clés** pour éviter les diffs inutiles lors des phases suivantes.

---

## ✅ Critères d’acceptation

* L’overlay affiche `MSI: —` en RUN, sans impacter FPS.
* Un snapshot (long-press) contient la clé `msiDbg` avec :

  * `phase="T100"`,
  * `frame` cohérent (w/h/rotationDeg),
  * `perf.procMs` remplie,
  * `config` reflétant les prefs.
* Le stub respecte `timeoutMs` (ajuste la valeur pour tester).
* Aucune régression Phase 0 (ML Kit, Start/Stop, Torch, Zoom fonctionnent inchangés).

---

## 🔜 Ce que débloque T-100

* T-101 pourra **remplir `rois[]`, `roiCount`, `roiChosen`, `notes`**.
* T-102 ajoutera **`angleDegEst`**,
* T-103 utilisera `corners` (si tu ajoutes la clé) et mettra à jour le rendu,
* T-104..106 rempliront **`wPx`, `runsPxCount`, `runsQuantSample`**,
* T-109 (Lite) consommera overlay MSI pour dessiner le cadre orange.

````

---

## ➕ Ajout à `/Docs/Phase1/README_Phase1.md`

Dans l’intro des tâches Phase 1, insère **T-100** avant T-101 :

```markdown
- **T-100 : MSI Debug Harness & Hooks**
  - Pose les bases d’instrumentation pour Phase 1 :
    - Schéma JSON `msiDbg` (snapshot) standardisé,
    - Slot d’overlay MSI,
    - Préférences/config MSI (roiMax, quantTol, lines, timeoutMs),
    - Stub pipeline synchronisé + mesures perf.
  - Aucun algorithme MSI implémenté, uniquement l’ossature et les métriques.
````

---

si tu veux, je peux aussi te faire un **mini `_Approved.md` template** pour T-100, avec des cases à cocher et la place pour coller 1–2 snapshots exemplaires.
