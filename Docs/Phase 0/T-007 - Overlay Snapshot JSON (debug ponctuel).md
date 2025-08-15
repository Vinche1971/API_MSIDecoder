# T-007 : Overlay Snapshot JSON (debug ponctuel)

## 🎯 Objectif
Permettre de capturer **à la demande** (sans spam) un "instantané" JSON des métriques courantes
pour diagnostiquer un cas terrain sans remplir les logs.

## 🛠 Détails techniques

### Déclencheur
- **Long-press** (1–1.5 s) sur la zone overlay.
- Feedback visuel (toast "Snapshot enregistré") + (option) vibration légère.

### Contenu du snapshot (JSON)
- `ts`          : timestamp (epoch ms)
- `res`         : "WIDTHxHEIGHT" (ex: "1280x720")
- `fps`         : double (EMA)
- `procMs`      : double (latence pipeline)
- `queue`       : int (frames in-flight)
- `rotationDeg` : int (0/90/180/270)
- `torch`       : "ON" | "OFF"
- `zoom`        : { "ratio": float, "type": "optique|numerique" }
- `ml`          : { "latMs": double|null, "hits": int }
- `msi`         : { "latMs": double|null, "status": "stub|ok|timeout|error" }
- `lastPub`     : { "text": string|null, "src": "MLKit|MSI|null", "ts": long }
- (Phase 1+) `msiDbg` : { "angle": float?, "wPx": float?, "snr": float?, "runs": int? }

### Stockage & export
- Sauvegarder le JSON dans un fichier interne (ex: `snapshots/snap_YYYYMMDD_HHMMSS.json`).
- Afficher le chemin court dans un toast (ou compteur de snapshots).
- (Option) Bouton "Partager dernier snapshot" → Intent de partage (email, Slack…).

### UI Overlay (rappel)
- Refresh <= 10 Hz.
- Informations Phase 0 :
  - `FPS`, `Proc ms`, `Res`, `Queue`
  - `Torch`, `Zoom`
  - `ML: xx.x ms, hits: n`
  - `MSI: —` (stub)
  - `SRC: MLKit|MSI` quand publication

## 💡 Points d’attention
- Le snapshot doit être **instantané** (pas de pause/stop camera).
- Pas de logs console réguliers : le snapshot remplace un log continu.
- Éviter les données personnelles : ne pas inclure l’image brute; au besoin,
  ajouter une option "Inclure une frame JPEG basse qualité" (opt-in uniquement).

## ✅ Critères d’acceptation
- Long-press → création d’un fichier JSON unique, pas de freeze UI.
- Contenu JSON complet et cohérent avec l’overlay au moment T.
- Partage du dernier snapshot opérationnel (si option activée).
- Aucun snapshot automatique : **uniquement** sur action explicite.