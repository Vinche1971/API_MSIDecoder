# T-104 : Profils Multi-Lignes (Médiane)

## 🎯 Objectif
Extraire un profil 1D robuste via médiane de plusieurs lignes horizontales.

## 🛠 Techniques
- Sélectionner 16–32 lignes centrales (35–65% de la hauteur).
- Pour chaque x : calcul de la **médiane**.
- Option : lisser légèrement (gaussien σ=1–2).
- Générer `profileMedian` et éventuellement profils voisins.

Oui 👍 T-104 est pile le bon moment pour afficher un visuel, et tu peux le faire proprement sans plomber les perfs :

* Bande 1 : **profil médian** rendu en niveaux de gris (une colonne par x).
* Bande 2 : **dérivée** (tant que T-105 n’est pas fait) puis **binaire** dès que tu as le seuil local.
* Rafraîchi ≤10 Hz, dessiné au Canvas, largeur = celle de la ROI rectifiée.

Je t’ai rédigé la section complète à **coller** dans `T-104_Profils_MultiLignes_Mediane.md` :

---

### 👁️ Visualisation (overlay “profil 1D”)

**But**
Valider visuellement que le profil médian est propre et que les transitions sont nettes.

**Ce qu’on dessine (2 bandes empilées)**

* **Bande 1 — Profil médian (gris)**

  * Hauteur fixe \~12–16 px ; **largeur = largeur ROI**.
  * Pour chaque x : colonne verticale avec gris `g = 1.0 - profileMedian[x]`.
  * Option : fine courbe du profil (1 px) par-dessus.

* **Bande 2 — Dérivée (T‑104) → Binaire (T‑105)**

  * T‑104 : heatmap simple de `|d(profile)/dx|` (sombre = transition forte).
  * T‑105 : remplacer par la **bande binaire** (barre=Noir, espace=Blanc) issue du seuil **local**.

**Placement & UX**

* Dans l’overlay (haut/bas), aligné sur la ROI.
* Labels discrets : `Profile` / `Binary`.
* Refresh ≤10 Hz.

**Perf / Implémentation**

* Dessin via **Canvas**; éviter bitmaps lourds.
* Sous-échantillonnage horizontal si ROI très large (ex. 1:2).
* Un seul `invalidate()` par tick.

**Diagnostic rapide**

* Bande 1 : alternances claires, peu de “plats”.
* Bande 2 : pics nets (dérivée) puis alternances propres (binaire).

**Snapshot (T‑007)**

* `profileLen`, `profileSample[20]`, `derivativeStats{mean,p95}`
* (T‑105) `binMethod`, `thresholdStats`.

**Critères d’acceptation**

* Profil lisible et transitions nettes.
* Rendu fluide, collé à la ROI, sans drop de FPS.

---

## ✅ Validation
- Profil médian montre des transitions nettes (barres bien visibles).
