
```markdown
<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=Fira+Code&size=32&pause=1000&color=7F52FF&center=true&vCenter=true&width=600&lines=ToonIA;Studio+de+Webtoons+IA;Par+GANDXO+TECH" alt="Typing SVG" />

### 🎬 Une idée. Un canon narratif rigoureux. Un webtoon exportable.

<br/>



![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)




![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)




![Android](https://img.shields.io/badge/Android-Native-3DDC84?style=for-the-badge&logo=android&logoColor=white)




![Status](https://img.shields.io/badge/Statut-En%20développement%20actif-FFC107?style=for-the-badge)



</div>

<br/>

## 📖 Sommaire

- [🌟 Vision](#-vision)
- [🧩 Workflow progressif](#-workflow-progressif)
- [🛠️ Stack technique](#️-stack-technique)
- [📂 Structure du projet](#-structure-du-projet)
- [🚀 Installation](#-installation)
- [🗺️ Roadmap](#️-roadmap)
- [👤 Auteur](#-auteur)
- [📄 Licence](#-licence)

<br/>

## 🌟 Vision

> **ToonIA n'est pas un générateur d'images.**
> C'est un moteur narratif structuré — personnages, univers, histoire, chapitres, panels, dialogues — construit **progressivement**, jamais en un seul bloc, et gardé cohérent grâce à une mémoire canonique versionnée : le **CanonMemory**.

Une contradiction avec un fait déjà établi n'est **jamais** corrigée en silence — elle est détectée et signalée. Le canon n'évolue que si l'utilisateur valide explicitement.

<br/>

## 🧩 Workflow progressif

<table>
<tr><th align="center">Étape</th><th align="left">Module</th><th align="left">Description</th></tr>
<tr><td align="center">1️⃣</td><td><b>Bible & Univers</b></td><td>CanonMemory versionné : Character Bible, World Bible, Story Bible, Visual Bible, Humor DNA</td></tr>
<tr><td align="center">2️⃣</td><td><b>Scénario & Chapitres</b></td><td>Structuration d'arcs narratifs cohérents avec le canon</td></tr>
<tr><td align="center">3️⃣</td><td><b>Storyboard & Panels</b></td><td>Découpage scène → panels (caméra, composition, action, continuité)</td></tr>
<tr><td align="center">4️⃣</td><td><b>Dialogues</b> <sub>Phase 13</sub></td><td>JSON structuré (<code>responseSchema</code>) — <code>SPEECH</code> · <code>THOUGHT</code> · <code>SHOUT</code> · <code>WHISPER</code> · <code>NARRATION</code></td></tr>
<tr><td align="center">5️⃣</td><td><b>Génération visuelle</b> <sub>Phase 14</sub></td><td>Illustration via l'API Gemini, cohérence des personnages par image de référence canonique, jamais de texte incrusté</td></tr>
<tr><td align="center">6️⃣</td><td><b>Lettering</b> <sub>Phase 15</sub></td><td>Bulles rendues nativement en Compose, éditables sans jamais régénérer l'image</td></tr>
<tr><td align="center">7️⃣</td><td><b>Lecture verticale</b> <sub>Phase 16</sub></td><td>Assemblage en flux continu, format webtoon mobile</td></tr>
<tr><td align="center">8️⃣</td><td><b>Export & partage</b> <sub>Phase 17</sub></td><td>PDF complet (couverture, synopsis, chapitres, panels, dialogues) + partage natif</td></tr>
<tr><td align="center">9️⃣</td><td><b>Validation de chapitre</b> <sub>Phase 18</sub></td><td>Clôture → mise à jour du canon → chapitre suivant débloqué</td></tr>
</table>

<br/>

## 🛠️ Stack technique

<div align="center">

| Domaine | Choix |
|:---:|:---:|
| 🧠 Langage | Kotlin (100%) |
| 🎨 UI | Jetpack Compose + Material Design 3 |
| 🏗️ Architecture | MVVM + Clean Architecture |
| 💾 Base locale | Room |
| 🤖 IA | Gemini API — sortie JSON contrainte, jobs asynchrones (`GenerationJob`) |
| 🖼️ Images | Coil (`AsyncImage`) + stockage fichiers locaux |
| 📄 Export | `android.graphics.pdf.PdfDocument` + `FileProvider` |

</div>

> ⚠️ **Transparence** : la version actuelle intègre la clé API Gemini côté client (projet construit via Google AI Studio, mode Build — pas de backend disponible dans ce mode). Choix assumé pour le MVP. Une couche proxy sera ajoutée avant toute distribution publique.

<br/>

## 📂 Structure du projet

```text
com.example/
├── data/
│   ├── local/          # Room : DB, DAOs, Entités, TypeConverters
│   └── repository/     # CanonMemoryRepository, ProjectRepository, etc.
├── domain/
│   └── usecase/        # Cas d'usage métier & générateurs IA (Gemini)
└── ui/
    ├── chapter/         # Chapitres & validation canonique
    ├── dialogues/        # Phase 13
    ├── export/           # Phase 17
    ├── lettering/         # Phase 15
    ├── navigation/         # Routage (Screen.kt)
    ├── panels/              # Phase 14
    ├── project/               # Création/gestion de projet
    ├── reading/                 # Lecteur vertical — Phase 16
    └── theme/                    # Material 3
```

<br/>

## 🚀 Installation

```bash
1. Ouvre le projet dans Google AI Studio → Build
2. Renseigne ta clé API Gemini dans le panneau Secrets
3. Compile depuis l'environnement de build intégré
   — ou télécharge le ZIP et ouvre-le dans Android Studio via Gradle
```

<br/>

## 🗺️ Roadmap

- [x] Squelette & navigation
- [x] Modèles de données (Room)
- [x] Moteur CanonMemory
- [x] Pipeline narratif (idée → personnages → univers → histoire)
- [x] Génération visuelle des panels
- [x] Lettering & assemblage vertical
- [x] Export PDF
- [ ] Proxy backend pour sécuriser la clé API
- [ ] Lecteur web public
- [ ] Étapes artistiques intermédiaires (rough sketch, line art, colorisation)

<br/>

## 👤 Auteur

<div align="center">

### Développé par **Gandxo** — GANDXO TECH 🚀

[

![GitHub](https://img.shields.io/badge/GitHub-gandxo--tech-181717?style=for-the-badge&logo=github&logoColor=white)

](https://github.com/gandxo-tech)
[

![Instagram](https://img.shields.io/badge/Instagram-richx__land-E4405F?style=for-the-badge&logo=instagram&logoColor=white)

](https://www.instagram.com/richx_land)
[

![WhatsApp](https://img.shields.io/badge/WhatsApp-+229%2001%2096%2095%2097%2073-25D366?style=for-the-badge&logo=whatsapp&logoColor=white)

](https://wa.me/2290196959773)

*Pour toute collaboration, contrat freelance ou question sur le projet — écris-moi.*

</div>

<br/>

## 📄 Licence

<div align="center">

**Projet personnel — Tous droits réservés à Gandxo / GANDXO TECH**
*Sauf mention contraire écrite.*

<br/>

⭐ *Si ce projet t'inspire, une étoile sur GitHub fait toujours plaisir* ⭐

</div>
```

