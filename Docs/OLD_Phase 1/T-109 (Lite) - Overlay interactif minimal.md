# T-109 (Lite) : Overlay interactif minimal

## 🎯 Objectif
Fournir un overlay visuel simple pour :
- Déboguer la détection ROI MSI,
- Unifier l’expérience avec ML Kit (cadres visibles),
- Aider l’utilisateur à cadrer sans s’appuyer uniquement sur les logs.

Cette version "Lite" sera implémentée en **Phase 1**, avec des fonctionnalités limitées mais utiles.  
La version complète (avec transitions, fade-out, publication MSI) sera prévue en **Phase 2**.

---

## 🧩 Fonctionnalités Phase 1

### MSI
- Afficher la **meilleure ROI MSI** (bbox rectangulaire, axis-aligned).
- Cadre **orange** quand score ROI ≥ seuil (ex. 0.4).
- ROI lissée avec **EMA** pour éviter les tremblements.
- Pas de passage vert (décodage pas encore dispo).
- Pas de fade-out.

### ML Kit
- Afficher la **bbox** des codes whitelist (EAN13, EAN8, Code128, QRCode, DataMatrix).
- Cadre **vert** uniquement si ML Kit a **décodé avec succès**.
- Pas d’animation, simplement affichage vert statique.

---

## 🎨 Design simplifié

- Rectangle **stroke only**, 2–4 px d’épaisseur.
- Couleurs :
  - Orange MSI : `#FFA500`
  - Vert ML Kit : `#00C853`
- Rafraîchissement : max 10–15 Hz.
- Mise à jour uniquement si changement de ROI (pas de redraw inutile).

---

## 📐 Coordonnées

- Convertir bbox (ML Kit) et ROI (MSI) en coordonnées écran via `PreviewView` transformation matrix.
- Support mode portrait uniquement (aligné avec caméra preview).

---

## 🧠 Lissage anti-jitter
- EMA sur les coins du rectangle :
p_smooth = α * p_new + (1-α) * p_old

avec α ≈ 0.25.

---

stateDiagram-v2
    [*] --> Idle

    Idle: Aucun overlay
    CandidateMSI: Cadre orange (ROI stable MSI)
    ReadableMLKit: Cadre vert (ML Kit a décodé)
    
    Idle --> CandidateMSI: ROI MSI détectée (score ≥ seuil)
    CandidateMSI --> Idle: ROI perdue
    
    Idle --> ReadableMLKit: ML Kit code décodé
    ReadableMLKit --> Idle: Code hors cadre


## 🔀 États Phase 1

- **MSI**
- Candidate : Orange
- Pas de "Readable" / "Published"
- **ML Kit**
- Readable : Vert quand décodé
- Pas d’animation fade-out

---

## 🧪 Critères d’acceptation

- Quand un EAN/QR est scanné par ML Kit : cadre **vert** apparaît, correspond à la bbox.
- Quand un MSI est bien cadré : cadre **orange** apparaît et suit la zone détectée, lissé.
- Quand aucune ROI stable n’est trouvée : rien affiché.
- Overlay n’impacte pas la fluidité de la preview (30–60 fps).

---

## 📦 Préparation Phase 2

En Phase 2, l’overlay sera enrichi de :
- Transition orange → vert pour MSI quand décodage validé,
- Fade-out après publication,
- Quadrilatère (au lieu de bbox) si coins disponibles,
- Pulses / transitions de couleur animées.
