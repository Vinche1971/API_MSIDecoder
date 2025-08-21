
Voici le **plan de la Phase 1 – Détection & Préparation MSI**, toujours dans le même esprit que la Phase 0, avec des micro-tâches numérotées.
Cette phase a pour but de construire un **pipeline de traitement image brut → runs MSI exploitables**, **sans encore faire le décodage complet** (ça sera en Phase 2).

---

# 📍 Phase 1 : Détection ROI MSI (sans OpenCV)

## 🎯 Objectif global

* Isoler et binariser une zone de code-barres MSI à partir du flux caméra.
* Extraire des séquences de runs (largeurs de barres et d’espaces) propres et exploitables.
* Garder le système modulaire et testable par étape.

---

## 📑 Tâches proposées

### **T-100 : Diagnostic & Debug Snapshot**

* Objectif : Préparer un système de monitoring interne pour valider visuellement et via logs JSON chaque étape de la pipeline.
* Implémentation :
  * Créer un objet `MsiDebugSnapshot` qui enregistre :
    - frameId
    - horodatage
    - paramètres actifs (`binThreshold`, largeur ROI, filtre utilisé…)
    - stats signal (longueur ligne, moyenne, variance…)
  * Export JSON compact inséré dans la sortie de T-007 (Phase 0).
  * Affichage visuel minimal dans l’overlay (ex : “Diag OK / NOK”).
* Bénéfices :
  * Assure que chaque micro-tâche T-101 → T-105 peut être tracée et validée.
  * Évite d’engorger les logs classiques avec trop de détails.



### **T-101 : ROI Extraction (stub + heuristique simple)**

* Définir un pipeline minimal pour isoler un **Rectangle Of Interest (ROI)** dans le frame.
* Pour Phase 1 : pas d’IA, pas d’OpenCV → juste une heuristique basique :

  * Ligne horizontale médiane (portrait mode → donc code barre souvent “couché”).
  * Extraction d’un **bandeau de N pixels** autour de cette ligne (ex : 5–10 px).
* Moyennage vertical du bandeau pour réduire le bruit → donne une **ligne 1D de luminosité**.
* Output : array `[0..255]` représentant l’intensité des pixels.

### **T-102 : Normalisation & Filtrage**

* Nettoyer le signal 1D obtenu :

  * Normaliser `[0..255]` → `[0.0..1.0]`.
  * Appliquer un **filtre passe-bas léger** (moyenne glissante 3–5 px) pour lisser les petits bruits.
* Output : ligne 1D normalisée + lissée.

### **T-103 : Binarisation adaptative**

* Transformer la ligne en séquence **noir/blanc** :

  * Seuil adaptatif basé sur la moyenne glissante ou médiane locale.
  * Résultat attendu : `[1,0,0,1,1,0...]`
* Prévoir paramètre `binThreshold` configurable (et loggué dans snapshot).
* Output : ligne binaire.

### **T-104 : Runs extraction**

* Parcourir la ligne binaire et extraire les **runs consécutifs** :

  * `[1,0,0,1,1,0...]` → `[ (1,1), (0,2), (1,2), (0,1)... ]`
  * Stocker chaque run = (valeur, longueur).
* Output : array de runs.

### **T-105 : Runs normalization (base module)**

* Définir la largeur d’un **module** (fine barre) à partir d’une stat robuste (ex : mode, médiane des runs courts).
* Normaliser chaque run en multiple de ce module → `[1,2,1,2,3,...]`.
* Output : tableau d’entiers, base du futur décodage MSI.

### **T-106 : Intégration overlay & snapshot**

* Ajouter dans l’overlay Phase 0 un champ `MSI: ...`

  * Tant que T-101 → T-105 en place, afficher par ex :

    * `MSI ROI OK`
    * `Runs: [1,2,1,1,2,2,...]`
* Dans snapshot JSON (T-007) → ajouter clé `msiDbg` :

  * `runs`, `module`, `signalLength`, `threshold`, etc.


  ### **T-109 (Lite)** : Implémentation d’un overlay interactif minimal
  - Cadre orange = ROI MSI candidate (pas encore décodable).
  - Cadre vert = ML Kit quand décodage réussi.
  - Pas d’animation, pas de fade-out.
  - Permet de valider visuellement la stabilité et le mapping coordonnées → écran.
  - Version complète prévue en Phase 2.


---

## ⚠️ Contraintes spécifiques Phase 1

* **Mode portrait only** : ROI horizontal (ligne médiane).
* **Pas d’OpenCV** : uniquement traitement 1D basique.
* Code **structuré en classes** pour être plug-in friendly :

  * `MsiDecoderPipeline`

    * `extractROI(frame) -> line`
    * `normalize(line) -> line`
    * `binarize(line) -> bits`
    * `runs(bits) -> runs`
    * `normalizeRuns(runs) -> modules`

---

## ✅ Validation Phase 1

* L’app affiche en temps réel un overlay `Runs` cohérent quand on scanne un MSI.
* Les snapshots JSON contiennent les runs et params associés.
* Même si ça ne “décode” pas encore, la chaîne **ROI → runs** fonctionne de manière stable.
* Robustesse : tolère un peu de bruit, légère inclinaison, variations de luminosité.
* Chaque étape (T-100 à T-109) est vérifiable via snapshots JSON (`msiDbg`) et retour visuel (overlay).


---

👉 Après ça, la **Phase 2** pourra attaquer le **décodage MSI proprement dit** (start/stop pattern, checksum, extraction digits).

---

Tu veux que je détaille tout de suite les **micro-fiches T-101 → T-106** comme pour la Phase 0, ou tu préfères qu’on garde juste ce plan global pour l’instant ?
