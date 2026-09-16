# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## État du dépôt

Le scaffolding Android est en place : dépôt Git initialisé (branches `main`/`develop`), structure Gradle/Kotlin/Compose complète en Clean Architecture (`domain/data/presentation/service`), CI GitHub Actions, README et tests unitaires ciblés. Voir `README.md` pour le détail des commandes.

Le dépôt vit sur un volume externe **exFAT** (`/Volumes/PH4NT0M`) : macOS y génère des fichiers `._*` (AppleDouble) à chaque écriture. Ils sont ignorés par `.gitignore` — les supprimer avant tout `git status`/commit si `git status` en affiche (`find . -name '._*' -not -path './.git/*' -delete`).

**Important — répertoires `build/` hors exFAT.** Gradle/AGP ne peuvent pas écrire leurs sorties directement sur ce volume exFAT (`parseDebugLocalResources` échoue avec `'.../._drawable' is not a directory` : l'OS remplace un dossier de ressources par son sidecar AppleDouble en pleine écriture). `build/` (racine) et `app/build/` sont donc des **symlinks** vers `~/AndroidBuilds/CiaoCloud/{root-build,app-build}` sur le disque interne (APFS). Ils sont ignorés par `.gitignore` (entrée `build` sans slash, car un pattern `build/` ne matche pas un symlink). Si ces symlinks disparaissent (ex. `git clean`), les recréer avant de builder :

```bash
mkdir -p ~/AndroidBuilds/CiaoCloud/app-build ~/AndroidBuilds/CiaoCloud/root-build
ln -s ~/AndroidBuilds/CiaoCloud/app-build app/build
ln -s ~/AndroidBuilds/CiaoCloud/root-build build
```

**Environnement de build vérifié et fonctionnel** (installé le 2026-09-16) :

- JDK 17 via `brew install openjdk@17` (pas de cask/sudo nécessaire). `JAVA_HOME=/opt/homebrew/opt/openjdk@17`, ajouté au `PATH` dans `~/.zshrc`.
- Android SDK via `brew install --cask android-commandlinetools`, racine `/opt/homebrew/share/android-commandlinetools`. Composants installés : `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` (+ `build-tools;34.0.0` auto-résolu par AGP). Licences acceptées (`sdkmanager --licenses`).
- Émulateur : AVD `ciaocloud35` (Android 15, `system-images;android-35;google_apis;arm64-v8a`), lancement `$ANDROID_SDK_ROOT/emulator/emulator -avd ciaocloud35`.
- Appareil réel : Pixel 10 Pro (Android 17) via `adb`. Son unique port USB-C sert au câble du Mac : pour tester sans SSD, choisir un dossier du stockage interne (ex. `Documents/CiaoCloudTest`) comme destination.
- `local.properties` (non versionné, à la racine) doit contenir `sdk.dir=/opt/homebrew/share/android-commandlinetools`.

Commandes de référence :

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"
./gradlew :app:assembleDebug        # build debug — vérifié OK
./gradlew :app:testDebugUnitTest    # tests unitaires domain — vérifié OK (44 tests, 0 échec)
```

Pas de ktlint/detekt intégré pour l'instant (jugé non prioritaire, cf. README).

Note : `TopAppBar` (Material3) est une API expérimentale — opt-in global via `freeCompilerArgs` dans `app/build.gradle.kts` (`-opt-in=androidx.compose.material3.ExperimentalMaterial3Api`), plutôt que d'annoter chaque écran individuellement.

## Projet

C!ao (anciennement CiaoCloud ; package `dev.ybdn.ciaocloud`) est une application Android **strictement personnelle**, sans backend ni compte, qui délestage manuellement photos et vidéos du stockage local d'un téléphone vers un SSD externe branché en USB-C (OTG), en les rangeant par date, puis supprime les originaux du téléphone uniquement après vérification de la copie. La spécification complète et faisant autorité est `prompt-initial.md` — s'y référer pour tout détail non résumé ici (gestion des cas limites, format des chemins, workflow CI/CD, etc.). La v2 (fiabilisation du rangement : casse des dossiers, doublons, fuseau des vidéos ; visionneuse unifiée téléphone + SSD) est spécifiée dans `docs/spec-v2-fiabilisation-visionneuse.md`, qui prime sur `prompt-initial.md` en cas de contradiction.

## Stack technique

- Kotlin + Jetpack Compose (natif, pas de framework cross-platform)
- Coroutines + Flow pour le scan, le transfert et le reporting de progression
- Room pour persister l'état de transfert (éviter re-scan/doublons)
- DataStore (pas SharedPreferences) pour l'URI SAF persistée et les préférences
- `androidx.exifinterface` pour la lecture EXIF, `MediaMetadataRetriever` pour les métadonnées vidéo
- `minSdk`/`targetSdk` : dernière version stable Android (app mono-device, pas de contrainte de compatibilité descendante)
- Aucune dépendance réseau/backend/authentification

## Architecture : Clean Architecture allégée, séparation par package (pas de multi-module Gradle)

Dépendances orientées vers le domaine ; le domaine ne connaît rien d'Android.

- **domain/** — Kotlin pur, aucune dépendance Android. Modèles métier (`MediaFile`, `TransferStatus`), interfaces de repository (`MediaRepository`, `DestinationWriter`), use cases : `ScanLocalMediaUseCase`, `TransferMediaUseCase`, `VerifyTransferUseCase`, `DeleteVerifiedMediaUseCase`.
- **data/** — implémentations concrètes : `MediaStoreRepositoryImpl` (scan photos/vidéos via `MediaStore`), `SafDestinationWriter` (écriture/arborescence sur le SSD via `DocumentFile`/SAF), `TransferStateDao` + entités Room.
- **presentation/** — Compose + ViewModels (écrans Accueil/Scan, Progression, Confirmation suppression, Paramètres). Les ViewModels n'appellent que les use cases du domaine, jamais `data/` directement.
- **service/** — `TransferForegroundService`, orchestrateur technique qui invoque les use cases du domaine et expose la progression via `Flow`/notification.

Pas de DI framework lourd sauf s'il simplifie réellement l'injection dans ViewModels/Service (Hilt acceptable, sinon injection manuelle).

## Règles métier clés

- Arborescence de destination : `SSD/DCIM/{année}/{mois}/{jour}/nom_du_fichier.ext`, mois/jour toujours sur 2 chiffres. Photos et vidéos partagent la même arborescence.
- Date utilisée : EXIF `DateTimeOriginal` en priorité pour les photos (fallback date fichier MediaStore) ; métadonnées `MediaMetadataRetriever` pour les vidéos (fallback date fichier MediaStore).
- Collisions de nom à destination : suffixe (`_1`, `_2`...), jamais d'écrasement.
- Suppression des originaux uniquement après vérification de copie (taille + hash CRC32/MD5), via `MediaStore.createDeleteRequest()` (API 30+) plutôt que gestion manuelle de `RecoverableSecurityException`.
- Transfert exécuté dans un Foreground Service avec notification de progression (survie au passage en arrière-plan sur gros volumes).
- Reprise après interruption : l'état "transféré" (ID MediaStore + hash + statut + type) est persisté en Room pour ne jamais re-proposer un fichier déjà transféré et vérifié.
- Aucune tâche automatique/planifiée : déclenchement manuel exclusivement à chaque étape (scan, transfert, suppression).

## Conventions

- Nom affiché de l'app : **C!ao** (`app_name`). Les identifiants techniques gardent « ciaocloud »/« CiaoCloud » volontairement : `applicationId`/package `dev.ybdn.ciaocloud` (le changer créerait une nouvelle app et perdrait base Room, DataStore et permission SAF), fichier DataStore `ciaocloud_settings`, base `ciaocloud.db`, canal de notification `ciaocloud_transfer`, classes `CiaoCloud*`, thème `Theme.CiaoCloud`, dossiers de build et AVD.

- Code (noms de classes, fonctions, variables) en anglais technique standard, conventions Android/Kotlin.
- UI et messages utilisateur en français.
- Branches Git : `main` stable/protégée (jamais de commit direct, uniquement via merge/PR depuis `develop`) ; `develop` est la branche de travail par défaut.
- Commits au format `type: description` (ex: `feat: scan MediaStore photos et vidéos`).
- Tests unitaires ciblés sur la logique pure (chemins, collisions, doublons, fuseau, chronologie) et les use cases via faux repositories — pas de sur-investissement en tests UI/instrumentation.
- Base Room : migrations explicites uniquement (`data/local/Migrations.kt`), jamais de migration destructive ; schémas exportés dans `app/schemas/` (à commiter).
- La galerie v2 (lots 1 à 7 de la spec v2) est implémentée. La visionneuse suit la DA néo-brutaliste de l'app (barres `NeoTopBar`/`NeoActionBar`, fond de page du thème), pas un fond noir.

## Tests sur appareil

- L'émulateur et le Pixel peuvent être branchés en même temps : toujours cibler explicitement avec `adb -s emulator-5554` / `ANDROID_SERIAL`. Ne jamais effacer les données (`pm clear`) ni supprimer de médias sur le Pixel réel.
- Build debug interprété très lent sur l'émulateur : `adb shell cmd package compile -m speed -f dev.ybdn.ciaocloud` avant de juger les performances.
- `screencap` ne capture pas toujours la surface vidéo : utiliser `adb emu screenrecord screenshot <fichier>`.
