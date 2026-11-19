# Audit complet — ToonIA (GANDXO TECH)

**Date :** 19 novembre 2026
**Périmètre :** application Android native Kotlin / Jetpack Compose du dépôt `gandxo-tech/Toon-IA-` (~150 fichiers, 18 entités Room, 11 générateurs Gemini, 16 écrans).
**Livrable associé :** portage web **HTML / CSS / JS** dans `web/` (voir §10).

---

## 1. Synthèse exécutive

ToonIA est un projet **ambitieux et bien structuré sur le plan conceptuel** : la promesse « moteur narratif à canon versionné » est réelle, pas un habillage marketing. Le découpage en phases, les bibles (personnages, univers, histoire, visuelle, humour), le `CanonMemory` et le détecteur de contradictions sont des choix d'architecture supérieurs à la moyenne des projets « générateur d'images ».

En revanche, l'application n'est **pas encore distribuable** : la clé Gemini est embarquée côté client, `applicationId = "com.example"`, les migrations Room sont destructives, la génération échoue en bloc (pas de reprise), plusieurs écrans du workflow sont encore des squelettes, et la moitié du pipeline n'est pas encore réellement branchée sur des images.

### Note par domaine

| Domaine | Note | Commentaire |
|---|---|---|
| Vision produit & modèle narratif | **9 / 10** | Canon, bibles, détection de contradictions : positionnement fort |
| Architecture logicielle | **7,5 / 10** | MVVM + Clean lisible, mais injection manuelle et duplication de modèles |
| Sécurité | **2 / 10** | Clé API dans l'APK + en clair dans les SharedPreferences |
| Persistance & données | **5 / 10** | 18 entités solides, mais migration destructive et schéma non exporté |
| Résilience IA | **4 / 10** | Rate-limit global, pas de timeout/retry par appel, pas de reprise de job |
| UI / UX | **6 / 10** | Direction visuelle Material 3 correcte, workflow non gardé, accessibilité faible |
| Qualité de code | **6 / 10** | Beaucoup de code mort, fichiers perdus à la racine, tests domaine corrects |
| Build / CI / Release | **3 / 10** | `com.example`, minify off, signature fragile, CI qui ne teste rien |
| Tests | **6 / 10** | Bonne couverture des use cases, zéro test des ViewModels et des UI |

**Verdict :** très bon prototype de R&D, **MVP non publiable en l'état**. Trois chantiers bloquants (sécurité, identité, migrations) et un chantier produit (finaliser les étapes 3 à 8 + génération d'images réelle).

---

## 2. Ce qui fonctionne bien (à préserver)

1. **Le canon comme source de vérité.** `CanonSnapshot` agrège personnages, lieux, objets, chapitres, scènes, panels, dialogues, timeline, mémoires, bibles et couverture. C'est exactement la bonne granularité pour contraindre un LLM.
2. **La règle « on ne corrige jamais en silence ».** `CanonContradictionDetector` retourne une liste vide plutôt que de deviner : c'est un choix éthique et technique juste.
3. **La séparation brouillon / canon.** `isCanon: Boolean` sur chapitre, scène, panel, dialogue, couverture permet un vrai cycle de validation.
4. **La contrainte de sortie JSON** via `responseSchema` Gemini plutôt que du texte libre parsé à la main.
5. **La règle « aucune image avec du texte incrusté »**, vérifiée jusque dans le détecteur de contradictions (`checkCoverContradictions`). Le lettering natif est la bonne décision.
6. **Les tests de use cases** (9 fichiers) : rares dans ce type de projet, et ils verrouillent les prompts/schemas.
7. **Le refus du base64 en base** (chemins de fichiers locaux uniquement) : bonne pratique, documentée dans les entités.

---

## 3. Sécurité — findings critiques

### S1. Clé API Gemini embarquée dans l'APK — **CRITIQUE**
- `app/build.gradle.kts` active le plugin `secrets` avec `.env` / `.env.example`, ce qui génère `BuildConfig.GEMINI_API_KEY`.
- `ApiKeyManager.getApiKey()` retourne cette valeur par défaut.
- Conséquence : `strings`/decompilation de l'APK extrait la clé. Le README le reconnaît (« choix assumé pour le MVP »), mais tout APK distribué, même en test interne, doit être considéré comme une fuite.
- **Correctif :** proxy serveur (Cloudflare Worker / fonction serverless) qui détient la clé, avec authentification, quota par utilisateur et liste de modèles autorisés. Le client n'appelle plus `generativelanguage.googleapis.com` directement.
- **Correctif immédiat :** révoquer et régénérer la clé si un APK a circulé.

### S2. Stockage de la clé en clair — **ÉLEVÉ**
- `SharedPreferences` non chiffré (`toonia_prefs`). Sur appareil rooté ou via backup, la clé fuite.
- `android:allowBackup` est à vérifier dans le manifeste ; `backup_rules.xml` existe mais n'exclut pas explicitement `toonia_prefs`.
- **Correctif :** `EncryptedSharedPreferences` (Jetpack Security) et exclusion explicite des préférences dans les règles de backup.

### S3. Absence de validation de la clé saisie — **MOYEN**
- `ApiKeyScreen` enregistre n'importe quelle chaîne, sans test réseau ni format. L'utilisateur ne découvre l'erreur qu'au milieu d'une génération.
- **Correctif :** bouton « Tester la clé » (`GET /v1beta/models`), feedback immédiat, masquage par défaut (déjà fait), effacement sécurisé.

### S4. Surface réseau non maîtrisée — **MOYEN**
- 11 générateurs instancient chacun leur `BASE_URL`, leur client et leur parsing. Pas de politique commune (timeouts, taille de réponse, redirections, logs).
- **Correctif :** un `GeminiClient` unique, `OkHttp` avec timeouts explicites et `HttpLoggingInterceptor` **désactivé en release** (aujourd'hui `logging-interceptor` est en `implementation` globale).

### S5. Signature et secrets de build — **MOYEN**
- `signingConfigs.release` lit `System.getenv("STORE_PASSWORD")` / `KEY_PASSWORD` sans fallback : build release cassé en local, ou signature invalide en CI.
- Le keystore de debug a des mots de passe codés en dur (`android`), acceptable en debug, mais le workflow CI génère ce keystore à chaque run.
- `isMinifyEnabled = false` : code et chaînes (prompts, endpoints) exposés sans obfuscation.

---

## 4. Données & persistance

### D1. `fallbackToDestructiveMigration()` — **ÉLEVÉ**
`ToonDatabase.getInstance()` détruit et recrée la base à chaque changement de version. Version actuelle : 2. Pour une application où le canon est la valeur centrale, **c'est une perte de données silencieuse** au premier schéma modifié.
**Correctif :** migrations explicites (`Migration(1, 2)`), `exportSchema = true` + dossier `schemas/` versionné, tests de migration (`MigrationTestHelper`).

### D2. Pas de schéma exporté, pas de test de migration
`exportSchema = false` prive l'équipe de la référence SQL et empêche tout test de migration. À inverser immédiatement.

### D3. Duplication modèle domaine ↔ entité Room
`CharacterBibleEntry`, `WorldBibleResult`, `StoryBibleResult`, `PanelDraft`, `DialogueEntity`, `CharacterEntity`… décrivent les mêmes concepts avec des noms de champs différents (`name`/`nom`, `atmosphere`/`ambiance`, `premise`/`premisse`). Chaque `fromJson` compense à la main.
**Risque :** dérive silencieuse entre ce que l'IA renvoie et ce qui est persisté.
**Correctif :** un seul modèle de domaine sérialisable, des mappers testés, et un schema JSON contractuel partagé entre prompts, parsing et Room.

### D4. Pas de transaction de validation canonique
`updateCanon(projectId, element)` écrit l'élément et les mémoires ; en cas d'échec partiel, le canon peut être à moitié mis à jour.
**Correctif :** `@Transaction` sur l'opération de validation complète.

### D5. Absence de quota / taille
Aucune limite sur le nombre de panels, chapitres ou mémoires par projet. La timeline, les dialogues et les prompts visuels peuvent croître sans borne et saturer la fenêtre de contexte envoyée à Gemini.

---

## 5. Résilience IA & coûts

| # | Finding | Gravité | Détail |
|---|---|---|---|
| A1 | Backoff **global** et définitif | Élevé | `ApiRateLimitManager` bloque toute l'app (`rateLimitCountdown`) et `backoffFactor` n'est jamais remis à 1 sauf appel explicite inexistant. Une seule 429 fait passer les attentes à 65 → 130 → … → 1040 s. |
| A2 | `GlobalScope` non structuré | Moyen | Le compteur survit aux écrans, ne peut pas être annulé, fuite de coroutine potentielle. Utiliser un scope applicatif injecté. |
| A3 | Message d'erreur brut affiché | Moyen | `lastErrorMessage` remonte le message du serveur (parfois volumineux) dans un bandeau. Filtrer et mapper vers des messages utilisateur. |
| A4 | Pas de timeout ni de retry par appel | Moyen | Un appel réseau qui pend bloque le job `RUNNING` indéfiniment. |
| A5 | Jobs non reprenables | Moyen | `GenerationJobEntity` existe (statuts `QUEUED/RUNNING/SUCCESS/FAILED`, `retryCount`) mais un job interrompu (process tué) reste `RUNNING` à jamais. Ajouter un watchdog au démarrage. |
| A6 | Pas d'annulation | Faible | Impossible d'interrompre une génération lancée. |
| A7 | Coût non borné | Moyen | Pas de compteur de tokens ni de plafond par projet. En mode clé personnelle, l'utilisateur peut vider son quota sans avertissement. |

---

## 6. Qualité de code & dette

1. **Code mort / fichiers parasites à la racine :**
   - `patch.kt` : un extrait de `ToonApp.kt` posé à la racine du dépôt, sans package ni compilation.
   - `app/src/main/java/com/example/ui/screens/WorkflowScreens.kt` : redéfinit `UniverseScreen`, `StoryScreen`, `DialoguesScreen`, `CoverScreen`, `VerticalReaderScreen`, `ExportScreen` en squelettes, alors que les vrais écrans existent dans leurs packages (`ui/universe`, `ui/story`, …). Ce fichier est inutilisé et trompeur.
   - `HomeScreen.kt` construit une liste `studioModules` (avec icônes et descriptions) **jamais rendue** : le rendu utilise `Screen.workflowScreens`. Le travail de contenu est perdu et la liste est morte.
   - Imports inutilisés signalés par le compilateur (`Brush`, `Color`, `background`, `clickable`…) et `@OptIn(ExperimentalMaterial3Api::class)` superflus.
2. **Deux sources de vérité pour le thème :** `ui/theme/Theme.kt` active la couleur dynamique Android 12+, ce qui rend la direction artistique de l'app dépendante du fond d'écran de l'utilisateur. Pour une app de création visuelle, c'est un contresens : désactiver `dynamicColor` par défaut.
3. **Pas d'injection de dépendances.** Chaque ViewModel instancie `ToonDatabase.getInstance(application)` et ses use cases. Cela rend les tests d'écran coûteux et a conduit à des constructeurs `@JvmOverloads` avec `useCaseOverride`. Hilt (ou un conteneur manuel) réglerait cela.
4. **`ApiKeyManager` en singleton statique avec contexte stocké** : risque de fuite mémoire si un contexte d'Activity était passé (aujourd'hui `applicationContext`, donc latent).
5. **Écrans « skeleton » encore exposés.** `SimpleSkeletonScreen` affiche « Écran initialisé (Phase 0 — Squelette) » : si l'utilisateur y accède en production, l'image produit est mauvaise. Ces routes doivent être masquées tant qu'elles ne sont pas implémentées.
6. **Pas de gestion d'état vide homogène.** Certains écrans gèrent l'empty state (accueil), d'autres non.
7. **Chaînes en dur, 100 % français.** Aucun `strings.xml` exploité pour les textes Compose : toute internationalisation demandera une passe complète.
8. **Typographie quasi par défaut** (`Typography` ne surcharge que `bodyLarge`) : la direction artistique « studio de webtoon » ne se lit pas dans l'UI.

---

## 7. UX / UI

| # | Finding | Impact |
|---|---|---|
| U1 | **Workflow non gardé** : depuis l'accueil, on peut ouvrir Panels sans avoir validé Personnages/Univers/Histoire | L'utilisateur génère des panels sans canon → incohérences, puis contradictions |
| U2 | Projet actif = « premier projet de la liste », pas de sélection explicite | Avec plusieurs projets, on édite le mauvais sans s'en rendre compte |
| U3 | Aucun indicateur de progression global du webtoon | Perte de repère dans un pipeline à 9 étapes |
| U4 | `enableEdgeToEdge()` sans `WindowInsets` explicites sur les écrans à `Scaffold` maison | Contenu sous la barre d'état sur certains appareils |
| U5 | Icônes décoratives avec `contentDescription = null` (correct) **mais** boutons sans `contentDescription` utile, `testTag` utilisé comme substitut d'accessibilité | TalkBack dégradé |
| U6 | Erreurs affichées en bandeau global + carte d'erreur locale : duplication | Bruit visuel |
| U7 | Pas de mode sombre/clair maîtrisé (couleur dynamique) | Identité visuelle instable |
| U8 | Export PDF : mise en page naïve (pas de césure, panels tronqués si `cYPos > 700`, pas de pagination des textes longs) | PDF non professionnel |

---

## 8. Build, CI & release

1. **`applicationId = "com.example"`** et `namespace = "com.example"` : interdit de publier sur le Play Store et signale un projet non finalisé. À remplacer par `com.gandxotech.toonia` **avant** toute première signature (le changer après publication est impossible).
2. **CI (`.github/workflows/build.yml`)** : `gradle wrapper` puis `assembleDebug`, aucune exécution de tests, pas de cache Gradle, pas de `./gradlew check`, pas de lint. Le workflow ne protège donc pas la branche `main`.
3. **Pas de `gradle/wrapper/gradle-wrapper.jar`** : `gradle wrapper` doit être exécuté à chaque build, ce qui suppose un Gradle préinstallé sur le runner.
4. **`minifyEnabled = false`** en release + `isCrunchPngs = false` : APK plus lourd, code lisible.
5. **`dependenciesInfo { includeInBundle = true }`** alors qu'aucun bundle n'est produit en CI.
6. **Firebase (BOM, AppCheck, AI) déclaré** alors que Gemini est appelé en REST direct : dépendances lourdes probablement inutiles. À auditer et retirer si non utilisées.
7. **Aucune signature de version** : `versionCode`/`versionName` fixes (`1`/`1.0`), pas de stratégie de release.

---

## 9. Tests

**Forces :** 9 fichiers de tests de use cases (analyse d'idée, personnages, univers, histoire, chapitre, storyboard, panels, humour, couverture, canon), tests Room, tests Robolectric + Roborazzi.

**Manques :**
- Aucun test de ViewModel (états `Running/Success/Failed`, validation, édition des chips).
- Aucun test de migration de base.
- Aucun test d'écran de bout en bout (workflow complet).
- Aucun test du détecteur de contradictions sur les cas **négatifs** (ne doit pas signaler de faux positif).
- Aucun test de `ApiRateLimitManager` (le backoff global mérite d'être verrouillé).
- Les tests ne tournent pas en CI.

---

## 10. Décision de portage : HTML / CSS / JS

Le portage web livré dans `web/` reprend **le modèle narratif et le parcours complet**, pas le code Android :

| Module Android | Écran web | État |
|---|---|---|
| `Accueil` | Tableau de bord, pipeline, modules | ✅ |
| `NouveauProjet` | Idée, presets, détails avancés, analyse éditable, validation canon | ✅ |
| `Personnages` | Character Bible (12 champs), validation canon | ✅ |
| `Univers` | World Bible + lieux + objets | ✅ |
| `Histoire` | Story Bible + timeline | ✅ |
| `DirectionArtistique` | Visual Bible + Humor DNA (onglets) | ✅ |
| `Chapitre` | Chapitres + scènes + beats | ✅ |
| `Storyboard` | Enrichissement de scène (rythme, arc émotionnel) | ✅ |
| `Panels` | Découpage complet (caméra, composition, prompt visuel) + visuel généré | ✅ |
| `Dialogues` | Bulles par panel (type, émotion, placement) | ✅ |
| `Lettering` | Rendu des bulles sur panel, édition en place | ✅ |
| `Couverture` | Titres, synopsis, tagline, couverture composée en CSS (sans texte dans l'image) | ✅ |
| `CanonMemory` | Vue d'ensemble, personnages, lieux, tests de contradiction | ✅ |
| `LectureVerticale` | Flux continu + bulles | ✅ |
| `Export` | JSON, Markdown, impression PDF | ✅ |
| `ApiKey` | Clé locale + test + mode démo | ✅ |
| Génération d'images Gemini | Visuels procéduraux (SVG) en démode, prompts exportables | ⚠️ non appelé |

**Différences assumées :**
- **Zéro backend** : l'état vit dans `localStorage`. C'est un choix de démonstration, pas de production.
- **Moteur de démo déterministe** : sans clé API, l'app génère du contenu structuré à partir de l'idée (profils de genre, templates). Cela permet de tester le parcours complet hors ligne.
- **Même avertissement de sécurité** : dans la version web, la clé saisie reste dans le navigateur et part directement à Google. Un vrai produit doit passer par un proxy.
- **Pas de portage de l'export PDF natif** : l'impression navigateur produit un PDF propre à partir d'une feuille de style dédiée.

**Ce que le portage n'est pas :** un remplacement de l'app Android. C'est une maquette fonctionnelle haute fidélité, utile pour tester le parcours, la structure du canon et la direction visuelle avant de réinvestir dans Kotlin.

---

## 11. Plan d'action priorisé

### P0 — bloquant avant toute distribution (1 à 2 semaines)
1. Proxy serveur pour Gemini + révocation de la clé exposée (S1).
2. Renommer `applicationId` en `com.gandxotech.toonia` (B1).
3. Migrations Room explicites + `exportSchema = true` (D1, D2).
4. Chiffrer la clé locale et l'exclure des backups (S2).
5. Masquer les écrans squelette et garder le workflow (U1, U3).

### P1 — qualité perçue (2 à 4 semaines)
6. Sélection explicite du projet actif + indicateur de progression (U2, U3).
7. Client Gemini unique : timeouts, retry borné, annulation, messages d'erreur mappés (A1–A6).
8. `@Transaction` sur la validation canonique (D4).
9. CI : `./gradlew test lint assembleDebug`, cache Gradle, tests de migration et de ViewModels.
10. Export PDF paginé proprement (U8).
11. Nettoyage : `patch.kt`, `WorkflowScreens.kt`, `studioModules`, imports morts (Q1).
12. Désactiver la couleur dynamique, vraie échelle typographique (Q2, Q8).

### P2 — produit (au-delà)
13. Génération d'images réelle par panel avec image de référence canonique.
14. Étapes artistiques intermédiaires (rough, line art, couleur) — déjà dans la roadmap.
15. Lecteur web public (le portage `web/` en est la première brique).
16. Compteur de tokens / plafond de coût par projet (A7).
17. Internationalisation via `strings.xml` (Q7).

---

## 12. Conclusion

Le socle intellectuel de ToonIA est sa vraie valeur : **un canon explicite, versionné et conflictuel** plutôt qu'une boîte noire générative. Ce socle mérite d'être protégé par une vraie couche serveur, des migrations non destructives et un workflow verrouillé.

Le prototype Android est à ~70 % du chemin fonctionnel et à ~30 % du chemin « publiable ». Le portage web `web/` permet de valider immédiatement l'expérience complète — y compris les tests de contradiction — sans dépendre d'un build Android ni d'une clé API.
