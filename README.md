# C!ao

Suite d'applications Android pour se passer des services des GAFAM : **pas de compte, pas de
cloud, pas de pistage, pas de publicité**. Chaque app fonctionne en local sur le téléphone, est
gratuite et open source ([MIT](LICENSE)). Principes détaillés : [`docs/vision.md`](docs/vision.md).

## Applications

| App | Dossier | Rôle | État |
|---|---|---|---|
| C!ao Galerie | [`apps/galerie`](apps/galerie) | Galerie photo/vidéo, délestage vers un SSD, édition, tri | En développement |
| C!ao Clavier | `apps/clavier` | Clavier | À venir |
| C!ao Messages | `apps/messages` | SMS (app SMS par défaut) | À venir |
| C!ao Téléphone | `apps/telephone` | Téléphone (app d'appel par défaut) | À venir |

Les apps seront publiées sur le Play Store une fois toutes développées. Une version iOS viendra
plus tard.

## Organisation du dépôt

Monorepo Gradle unique (décision : [ADR 0001](docs/adr/0001-monorepo.md)).

```
ciao-suite/
├── apps/<app>/          une app = un module Gradle, publiée séparément sur le Play Store
│   ├── src/ …
│   ├── docs/            specs de l'app (versionnées : spec-v2…, spec-v3…)
│   ├── PRIVACY.md       politique de confidentialité de l'app
│   ├── CHANGELOG.md     journal des versions de l'app
│   ├── README.md
│   └── CLAUDE.md        contexte propre à l'app
├── core/<module>/       code partagé entre apps (créé à la demande, voir ci-dessous)
├── build-logic/         plugins de convention Gradle (configuration commune des modules)
├── gradle/libs.versions.toml   versions de toutes les dépendances, pour toute la suite
├── docs/                vision, workflow Git, décisions d'architecture (adr/)
├── .githooks/           hooks Git (règles de branches et de commits)
└── .github/workflows/   un workflow réutilisable + un workflow par app
```

**Règles de dépendance** : `apps/*` → `core/*`, jamais l'inverse, et jamais une app vers une
autre. Un module `core/` n'est créé que lorsqu'une **deuxième** app a besoin du code
(le premier candidat est le design system néo-brutaliste de la Galerie).

### Plugins de convention

Chaque module déclare ce qu'il est, la configuration vient de `build-logic/` :

| Plugin | Pour |
|---|---|
| `ciao.android.application` | une app : SDK communs (min 33, cible 35), Java 17, release R8, signature |
| `ciao.android.library` | un module `core/` Android |
| `ciao.android.compose` | ajoute Compose + Material 3 (après l'un des deux précédents) |
| `ciao.jvm.library` | un module `core/` en Kotlin pur |

## Commandes

Prérequis : JDK 17 et Android SDK (`local.properties` à la racine avec `sdk.dir=…`).

```bash
./gradlew :apps:galerie:assembleDebug       # APK debug d'une app
./gradlew :apps:galerie:testDebugUnitTest   # tests unitaires d'une app
./gradlew assembleDebug                     # toutes les apps
./gradlew :apps:galerie:bundleRelease       # AAB signé pour le Play Store
```

Signature release : fichier `keystore.properties` à la racine (jamais commité), une même clé
d'importation pour toutes les apps (Play App Signing). Détails dans le
[README de la Galerie](apps/galerie/README.md#signature-release).

## Workflow Git

Règles complètes et procédures (publier, corriger une version) : [`docs/workflow-git.md`](docs/workflow-git.md).

- `main` : **seule branche permanente**, toujours buildable ; jamais de commit direct.
- Branches de travail courtes `<type>/<app>-<sujet>` (ex. `feat/galerie-albums`), fusionnées
  dans `main` par PR en squash, puis supprimées.
- Commits et titres de PR : `<type>(<portée>): <description>` (ex. `fix(clavier): accents sur les majuscules`).
- Une version = un tag annoté `<app>-v<semver>` sur `main` (ex. `galerie-v1.2.0`), chaque app
  ayant sa propre version et son `CHANGELOG.md`.
- Branche `release/<app>-<majeur.mineur>` créée seulement pour corriger une version publiée.

Après un clone, activer les hooks qui vérifient ces règles : `git config core.hooksPath .githooks`.

## CI

Un seul workflow, `.github/workflows/ci.yml` (PR et push vers `main` ou `release/**`) :

- un job par app, lancé seulement si l'app, `core/`, `build-logic/` ou la configuration Gradle
  ont changé ; il appelle `_android-app.yml` (build debug + tests unitaires ; APK en artifact sur push) ;
- un job **CI OK** qui agrège les résultats : c'est le check exigé pour fusionner.

`pr-title.yml` (**Titre de PR**) vérifie le format du titre des PR. Pas de publication
automatique sur le Play Store.

## Ajouter une app

1. Créer `apps/<app>/build.gradle.kts` avec `ciao.android.application` (+ `ciao.android.compose`),
   `namespace`/`applicationId` = `dev.ybdn.ciao.<app>`.
2. `include(":apps:<app>")` dans `settings.gradle.kts`.
3. Dans `.github/workflows/ci.yml` : ajouter un filtre `<app>` (sur le modèle de `galerie`), sa
   sortie dans le job `changes`, un job `<app>` qui appelle `_android-app.yml`, et l'ajouter aux
   `needs` de `ci-ok`.
4. Créer `README.md`, `CLAUDE.md`, `PRIVACY.md`, `CHANGELOG.md` et `docs/` dans le dossier de
   l'app, et l'ajouter au tableau ci-dessus.
