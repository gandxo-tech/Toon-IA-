# 📱 ToonIA — Studio de Création de Webtoons Assisté par IA

**ToonIA** est une application Android native (Kotlin, Jetpack Compose) conçue pour transformer une idée libre d'histoire en un webtoon professionnel, structuré, illustré et exportable en PDF, grâce à une assistance intelligente et un système de mémoire rigoureux (le **CanonMemory**).

---

## 🌟 Fonctionnalités Clés & Workflow Progressif

Le processus de création s'articule autour d'un flux guidé et incrémental :

1. **Bible & Univers (CanonMemory)** : Gestion versionnée et validée de l'univers, des personnages (Character Bible), des règles du monde, et de l'ADN d'humour.
2. **Scénario & Chapitres** : Rédaction et structuration d'arcs narratifs et de chapitres cohérents.
3. **Storyboard & Découpage en Panels** : Génération intelligente des cases avec descriptions visuelles et actions clés.
4. **Dialogues & Récitatifs (Phase 13)** : Génération par IA avec schéma JSON structuré (`responseSchema`), prise en charge des types de bulles (`SPEECH`, `THOUGHT`, `SHOUT`, `WHISPER`, `NARRATION`) et liaisons avec les personnages.
5. **Génération Visuelle Multimodale (Phase 14)** : Illustration des panels via l'API Gemini (images de référence canoniques des personnages, respect strict de l'absence de texte incrusté).
6. **Lettering & Bulles (Phase 15)** : Rendu natif Compose par-dessus les images, placement des bulles, styles différenciés et édition directe à la volée.
7. **Lecture Verticale (Phase 16)** : Assemblage en flux continu optimisé pour mobile (format webtoon).
8. **Export PDF & Partage Natif (Phase 17)** : Génération d'un document PDF complet (couverture, synopsis, chapitres, panels et dialogues) et partage direct via les applications tierces (WhatsApp, Drive, Email...).
9. **Validation de Chapitre & Canon (Phase 18)** : Clôture du chapitre, mise à jour définitive du canon et déblocage du chapitre suivant.

---

## 🛠️ Stack Technique

- **Langage** : 100% Kotlin
- **UI** : Jetpack Compose & Material Design 3 (M3)
- **Architecture** : MVVM (Model-View-ViewModel) + Clean Architecture pattern
- **Base de données** : Room (Persistance locale relationnelle)
- **Requêtes Réseau & IA** : Gemini API (Intégration directe client-side avec gestion des jobs asynchrones `GenerationJob`)
- **Imagerie & Affichage** : Coil (`AsyncImage`)
- **Export & Documents** : `android.graphics.pdf.PdfDocument` et `FileProvider`

---

## 📂 Structure du Projet

```text
com.example/
├── data/
│   ├── local/          # Base de données Room, Daos, Entités et TypeConverters
│   └── repository/     # Implémentations des repositories (CanonMemory, Projets, etc.)
├── domain/
│   └── usecase/        # Cas d'utilisation métier et générateurs IA (Gemini)
└── ui/
    ├── chapter/        # Gestion des chapitres et validation canonique
    ├── dialogues/      # Écran et gestion des dialogues (Phase 13)
    ├── export/         # Écran d'export PDF et partage (Phase 17)
    ├── lettering/      # Moteur de lettering et bulles (Phase 15)
    ├── navigation/     # Routage et écrans (Screen.kt)
    ├── panels/         # Gestion et illustration des panels (Phase 14)
    ├── project/        # Création et gestion des projets
    ├── reading/        # Lecteur vertical webtoon (Phase 16)
    ├── theme/          # Thème Material 3 et typographies
    └── ...
```

---

## 🚀 Installation & Compilation

Le projet se compile directement via Gradle (Kotlin DSL) dans l'environnement Android AI Studio :

1. Ouvrez le projet dans **AI Studio Build**.
2. Configurez votre clé API Gemini dans le panneau **Secrets**.
3. Lancez la compilation via l'outil de build (`compile_applet`).
