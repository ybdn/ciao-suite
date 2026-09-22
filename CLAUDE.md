# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## La suite C!ao

Monorepo de la suite **C!ao** : des applications Android natives (Kotlin + Jetpack Compose) pour
se passer des GAFAM — sans compte, sans cloud, sans pistage, gratuites, open source (MIT).
Principes non négociables dans `docs/vision.md`, décisions d'architecture dans `docs/adr/`.
Présentation, structure et commandes : `README.md`.

Chaque app a son propre `CLAUDE.md` dans `apps/<app>/` (règles métier, specs, conventions
propres) : **le lire avant de travailler sur une app**. Ce fichier-ci ne contient que ce qui est
commun à toute la suite.

Apps : `apps/galerie` (C!ao Galerie, en développement). À venir : clavier, messages (SMS, app
SMS par défaut), téléphone (app d'appel par défaut). Rien n'est encore publié sur le Play Store :
la publication se fera quand toutes les apps seront développées.

## Structure et règles d'architecture

- `apps/<app>/` : un module Gradle par app (`:apps:<app>`), publié séparément.
- `core/<module>/` : code partagé. **Ne créer un module `core/` que lorsqu'une deuxième app a
  besoin du code** — jamais « au cas où ». Candidat prévu : le design system néo-brutaliste
  (`Neo*`) de la Galerie.
- Dépendances : `apps/*` → `core/*`, jamais l'inverse ; jamais une app vers une autre app.
- `build-logic/` : plugins de convention (`ciao.android.application`, `ciao.android.library`,
  `ciao.android.compose`, `ciao.jvm.library`). Toute configuration commune (SDK, Java 17, R8,
  signature, opt-in Compose) va là, **pas** dans le `build.gradle.kts` d'un module.
- `gradle/libs.versions.toml` : unique source des versions ; jamais de version en dur dans un module.
- Aucune dépendance réseau/backend/analytics/publicité sans décision explicite (ADR).
- `namespace`/`applicationId` des nouvelles apps : `dev.ybdn.ciao.<app>`. Exception historique :
  la Galerie est encore en `dev.ybdn.ciaocloud` (voir son `CLAUDE.md`).

## Environnement de build (vérifié le 2026-09-22)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"

./gradlew :apps:galerie:assembleDebug        # build debug d'une app
./gradlew :apps:galerie:testDebugUnitTest    # tests unitaires d'une app
./gradlew :apps:galerie:assembleRelease      # release R8 (non signée sans keystore.properties)
```

- JDK 17 via `brew install openjdk@17`. Android SDK via `brew install --cask android-commandlinetools`
  (`platform-tools`, `platforms;android-35`, `build-tools;35.0.0`, licences acceptées).
- `local.properties` (non versionné, à la racine) : `sdk.dir=/opt/homebrew/share/android-commandlinetools`.
- `keystore.properties` (non versionné, à la racine) : clé d'importation release commune à toutes les apps.
- Émulateur : AVD `ciaocloud35` (Android 15, arm64), `$ANDROID_SDK_ROOT/emulator/emulator -avd ciaocloud35`.
- Appareil réel : Pixel 10 Pro (Android 17) via `adb`. Émulateur et Pixel peuvent être branchés
  ensemble : toujours cibler avec `adb -s <serial>` / `ANDROID_SERIAL`. Ne jamais effacer les
  données (`pm clear`) ni supprimer de médias sur le Pixel réel.
- Pas de ktlint/detekt pour l'instant.

## Conventions

- Code (classes, fonctions, variables) en anglais ; UI, messages utilisateur, docs et commits en français.
- **Git : suivre `docs/workflow-git.md`** (ADR 0002). Points à respecter systématiquement :
  - `main` est la seule branche permanente ; **ne jamais commiter sur `main` ni `release/*`**.
    Avant toute modification, créer une branche `<type>/<app>-<sujet>` (ex. `feat/galerie-albums`).
  - Messages de commit : `<type>(<portée>): <description>` sans point final, première ligne ≤ 72
    caractères ; portée = app ou module (`galerie`, `clavier`, `designsystem`…), omise si transverse.
  - Tant que le dépôt n'est pas sur GitHub : fusion locale `git merge --ff-only` après rebase sur
    `main` et build + tests verts. Ensuite : PR en squash.
  - Ne jamais créer, déplacer ou supprimer de tag, ni pousser, sans demande explicite.
  - Hooks actifs via `git config core.hooksPath .githooks` : ne pas les contourner (`--no-verify`)
    sans accord explicite.
- Versions par app : tag `<app>-v<semver>`, `versionCode` +1 à chaque version taguée,
  `apps/<app>/CHANGELOG.md` (section « Non publié » complétée pour tout changement visible).
- CI : un workflow par app (`.github/workflows/<app>.yml`, filtré par chemins) qui appelle
  `_android-app.yml` ; `pr-title.yml` vérifie les titres de PR. Ajouter `core/<module>/**` n'exige
  rien : `core/**` est déjà dans les filtres.
- Tests unitaires ciblés sur la logique pure et les use cases (faux repositories) ; pas de
  sur-investissement en tests UI/instrumentation.
- Nouvelle décision structurante (nouveau module `core/`, dépendance réseau, changement de stack,
  identifiants d'app) : l'écrire en ADR dans `docs/adr/` (modèle dans `docs/adr/README.md`).
