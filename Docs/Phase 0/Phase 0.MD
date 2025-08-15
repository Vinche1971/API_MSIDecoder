parfait, on verrouille la Phase 0 en mode “fiche technique découpée en mini-lots” — sans code, prêt à filer à Claude Code. Tu auras des paliers 0.1 → 0.6, chacun livrable, testable en 5–20 min.

Phase 0 — Vision

Mettre en place l’app portrait-only avec:
	•	CameraX Preview + Analysis (YUV), KEEP_ONLY_LATEST
	•	ML Kit (whitelist) actif en parallèle d’un stub MSI
	•	Arbitre de résultats (MLKit d’abord, MSI sinon)
	•	3 boutons: Start/Stop, Torch, Zoom cyclique
	•	Overlay temps réel (métriques, état)
	•	Persistance et restauration d’état complète (même après reboot)
	•	Gestion lifecycle propre (pause/reprise, torch off au stop, etc.)

⸻

Découpage en mini-lots (0.1 → 0.6)

0.1 – Squelette & Portrait

Objectif: activité unique, portrait verrouillé, Preview opérationnelle.
À faire:
	•	Forcer portrait (manifeste + activity).
	•	PreviewView plein écran, setTargetRotation(ROTATION_0).
	•	Permissions caméra (flow simple, si refus → écran explication + close).
Validation:
	•	Preview fluide, pas de crash rotation, orientation bloquée.

⸻

0.2 – Analysis + Overlay métriques

Objectif: analyser sans décoder, afficher perf en direct.
À faire:
	•	ImageAnalysis YUV_420_888, KEEP_ONLY_LATEST.
	•	Copie YUV→NV21, fermeture immédiate d’ImageProxy.
	•	Timer UI 10 Hz → overlay affiche: FPS, Proc ms, Res, Queue.
	•	Un executor dédié analyse (single thread).
Validation:
	•	FPS ~25–30, latence pipeline stable, Queue ≈ 0.

⸻

0.3 – Boutons START/STOP + États

Objectif: maîtriser le flux et la conso.
À faire:
	•	Toggle global IDLE ↔ ACTIVE.
	•	ACTIVE: binder Analysis + reset métriques; IDLE: unbind ou court-circuit.
	•	Annuler les tâches pendantes à STOP (sécurité).
	•	UI: libellé/icone change (▶︎/■), debounce 200 ms.
Validation:
	•	En STOP: CPU tombe bas, overlay fige. En START: tout repart net.

⸻

0.4 – Torch & Zoom cyclique

Objectif: contrôles terrain essentiels.
Torch:
	•	Toggle on/off indépendant, auto-OFF à STOP.
	•	(Option) afficher AE/AF/AWB: locked/unlocked si supporté; lock après focus.
Zoom:
	•	Cycle 1×→2×→3×→1×.
	•	Si téléobjectif dispo: prioriser changement d’objectif; sinon zoom ratio numérique.
	•	Overlay: Zoom: 1.0× (optique|numérique).
Validation:
	•	Torch togglable à chaud, s’éteint à STOP. Zoom fonctionne, pas de rebinding foireux.

⸻

0.5 – ML Kit (whitelist) + Arbitre

Objectif: résultats immédiats pour les formats supportés.
À faire:
	•	Whitelist: DataMatrix, EAN-13, EAN-8, Code-128, QR.
	•	Construction InputImage.fromByteArray(nv21, w, h, rotationDeg, NV21).
	•	Arbitre:
	•	Si ML Kit renvoie un code (≠ MSI): publier (beep+haptique+debounce ~800 ms).
	•	Sinon: place-holder MSI (stub, pas de résultat).
	•	Overlay: ML: xx.x ms, hits: n, SRC: MLKit|—.
Validation:
	•	EAN/QR/DM marchent; pas de freeze; debounce évite les rafales.

⸻

0.6 – Persistance & Restauration

Objectif: reprendre “comme si rien ne s’était passé”.
À stocker (SharedPreferences, write à chaque changement):
	•	scannerState (ACTIVE/STOPPED)
	•	torchState (ON/OFF)
	•	zoomState (1/2/3×) + type (optique/numérique si utile)
	•	lastResult + lastResultTs (pour éviter bip au lancement)
	•	(Option) ae/af/awbLocked
À restaurer (onCreate/onResume):
	•	Réappliquer boutons + UI.
	•	Si scannerState=ACTIVE et caméra dispo → relancer Analysis; sinon STOPPED.
	•	Torch OFF si permission/caméra indispo.
Cas limites:
	•	Capteur télé indispo: fallback 1× numérique; overlay avertit.
	•	Caméra occupée: basculer STOPPED + message overlay.
Validation:
	•	Tuer l’app → relancer: on retrouve l’état. Reboot device: idem.

⸻

Contrats & états (référence rapide)

États scanner
	•	IDLE: Analysis non bindée (ou flag scanning=false), Torch OFF, pas de tâches pendantes.
	•	ACTIVE: Analysis bindée, ML Kit en fan-out, MSI stub appelé (sans décoder encore).

Publication résultat
	•	Debounce: 700–800 ms (même texte, toutes sources confondues).
	•	Source tag: MLKit ou MSI.
	•	Feedback: beep + haptique uniquement si publié (pas pour duplicata).
	•	UI overlay: SRC, latence, horodatage court.

⸻

Overlay — infos à afficher (Phase 0)
	•	FPS: 27.8   Proc: 6.2 ms
	•	Res: 1280×720   Queue: 0
	•	Torch: ON   AE: locked (si dispo)
	•	Zoom: 2.0× (optique|numérique)
	•	ML: 22.5 ms, hits: 1
	•	MSI: — (stub)
	•	SRC: MLKit   L: 22.5 ms (lors d’une publication)

Refresh max 10 Hz. Long-press overlay = snapshot JSON (une fois, pour debug) avec: timestamp, res, zoom, torch, fps, proc, mlMs, mlHits, rotationDeg.

⸻

Performance & sécurité
	•	Fermer immédiatement ImageProxy après copie NV21.
	•	Une seule conversion YUV→NV21 par frame.
	•	Tâches MSI time-boxées (40–60 ms max) — si timeout, on abandonne la frame.
	•	KEEP_ONLY_LATEST pour éviter la backlog.
	•	Un executor dédié à l’analyse (1 thread).
	•	Sur devices faibles: option (Phase 0) pour ne lancer MSI qu’une frame sur deux (flag dev).

⸻

Rotation & portrait
	•	Preview et Analysis configurés avec targetRotation portrait.
	•	Toujours passer rotationDeg à ML Kit (il gère les 0–360°).
	•	Conserver rotationDeg pour le pipeline MSI (Phase 1 s’en servira).

⸻

Checklists d’acceptation

Fonctionnelle
	•	Portrait-only garanti.
	•	Start/Stop réactif, STOP coupe torche et analyse, aucune fuite.
	•	Torch toggle OK, persistance OK.
	•	Zoom cycle 1×→2×→3×; priorité téléobjectif si dispo; persistance OK.
	•	ML Kit lit EAN/QR/DM/Code128; debounce effectif; source=MLKit affichée.
	•	Overlay clair, refresh fluide, aucun spam.

Lifecycle & persistance
	•	Reprise après multitâche: état identique.
	•	Après kill/relaunch et reboot: état restauré.
	•	Caméra occupée: bascule STOP + message, pas de crash.

Perf
	•	Queue ≈ 0; FPS stable; Proc ms < 10–15 ms sur device cible.
	•	Aucune frame bloquée au-delà du timeout.
	•	Pas de fuite d’executors ni d’objets ML Kit à STOP.

⸻

“Tickets” prêts à coder (ordre conseillé)
	1.	T-001: Portrait-only + PreviewView + permissions.
	2.	T-002: Analysis YUV + conversion NV21 + overlay métriques.
	3.	T-003: Bouton Start/Stop + bind/unbind + annulation tâches.
	4.	T-004: Torch toggle + auto-OFF au STOP + (option) AE/AF/AWB lock.
	5.	T-005: Zoom cyclique (téléobjectif si dispo, sinon ratio).
	6.	T-006: ML Kit whitelist + fan-out + arbitre + debounce + feedback.
	7.	T-007: Persistance/restauration (SharedPreferences) + cas limites.
	8.	T-008: Overlay snapshot JSON (long-press).
	9.	T-009: Nettoyage perfs (timeouts MSI stub, KEEP_ONLY_LATEST, tests pause/reprise).

⸻

Notes pour la suite (Phase 1+)
	•	Le MSI stub recevra déjà FrameNV21 + rotationDeg pour que tu puisses brancher le détecteur ROI sans changer la signature.
	•	L’overlay accueillera bientôt angle, w(px), SNR, score, err quand on démarre la détection MSI.

⸻

si tu valides, on passe à Phase 1: Détection ROI MSI (sans OpenCV d’abord), avec :
	•	stratégie d’orientation (structure tensor/Hough light),
	•	filtrage aspect ratio/variance,
	•	affichage de la ROI + angle dans l’overlay,
	•	et critères de validation rapides.