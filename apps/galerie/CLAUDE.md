# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Place dans la suite

Cette app fait partie du monorepo **C!ao** : les règles communes (environnement de build, structure,
conventions Git, CI, plugins de convention `build-logic/`) sont dans le `CLAUDE.md` à la racine de
la suite — ce fichier ne contient que ce qui est propre à la Galerie. Historique Git importé depuis
l'ancien dépôt `ybdn/ciao-galery` (les anciens commits référencent des chemins à la racine).

Commandes (depuis la racine de la suite) :

```bash
./gradlew :apps:galerie:assembleDebug        # build debug
./gradlew :apps:galerie:testDebugUnitTest    # tests unitaires (191 tests)
```

`TopAppBar` (Material3) est une API expérimentale : l'opt-in `ExperimentalMaterial3Api` est global,
posé par le plugin de convention `ciao.android.compose` — pas d'annotation écran par écran.

Interface : thème, palette, polices et composants `Neo*` viennent de `core/designsystem` (règles
dans `docs/design-system.md`, ADR 0003) ; ne rien redéfinir dans l'app. Les couleurs de la Galerie
ont été transposées mécaniquement vers la palette canonique (teintes `NeoTone.Pink`, `.Green`…) ;
leur passage aux rôles (`Primary`, `Danger`…) est suivi dans l'issue #5 : tout nouvel écran utilise
directement les rôles.

## Projet

C!ao (anciennement CiaoCloud ; package `dev.ybdn.ciaocloud`) est une application Android **sans backend ni compte, sans cloud ni synchronisation multi-appareil** — chaque utilisateur l'installe pour son propre usage, en local — qui délestage manuellement photos et vidéos du stockage local d'un téléphone vers un SSD externe branché en USB-C (OTG), en les rangeant par date, puis supprime les originaux du téléphone uniquement après vérification de la copie. Depuis la v5, l'application est **distribuée publiquement sur le Play Store** (voir `docs/spec-v5-publication-publique.md`) ; le modèle d'usage mono-utilisateur/mono-device reste inchangé, seul le canal de distribution s'élargit. La spécification complète et faisant autorité est `prompt-initial.md` — s'y référer pour tout détail non résumé ici (gestion des cas limites, format des chemins, workflow CI/CD, etc.). La v2 (fiabilisation du rangement : casse des dossiers, doublons, fuseau des vidéos ; visionneuse unifiée téléphone + SSD) est spécifiée dans `docs/spec-v2-fiabilisation-visionneuse.md`, qui prime sur `prompt-initial.md` en cas de contradiction. La v3 (édition des photos : recadrage, rotation, retouches, filtres, modification des EXIF ; partage sans métadonnées) est spécifiée dans `docs/spec-v3-edition-photos.md`, qui prime sur les deux précédents en cas de contradiction. La v4 (tri de la pellicule par swipe : garder/supprimer/revoir plus tard) est spécifiée dans `docs/spec-v4-tri-pellicule.md`, qui prime sur les trois précédents en cas de contradiction. La v5 (publication publique : licence MIT, politique de confidentialité, signature release, fiche Play Store) est spécifiée dans `docs/spec-v5-publication-publique.md`, qui prime sur les quatre précédentes en cas de contradiction — notamment sur la clause de non-publication de `prompt-initial.md`.

## Stack technique

- Kotlin + Jetpack Compose (natif, pas de framework cross-platform)
- Coroutines + Flow pour le scan, le transfert et le reporting de progression
- Room pour persister l'état de transfert (éviter re-scan/doublons)
- DataStore (pas SharedPreferences) pour l'URI SAF persistée et les préférences
- `androidx.exifinterface` pour la lecture EXIF, `MediaMetadataRetriever` pour les métadonnées vidéo
- `minSdk` 33, `compileSdk` 37, `targetSdk` 35 : communs à la suite (`CiaoSdk` dans `build-logic`) ; montée de `targetSdk` suivie dans #47
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

- Nom affiché de l'app : **C!ao Galerie** (`app_name`) — C!ao est le nom de la suite d'applications (Galerie, et d'autres à venir), Galerie identifie cette app-ci au sein de la suite. Les identifiants techniques gardent « ciaocloud »/« CiaoCloud » volontairement : `applicationId`/package `dev.ybdn.ciaocloud` (le changer créerait une nouvelle app et perdrait base Room, DataStore et permission SAF), fichier DataStore `ciaocloud_settings`, base `ciaocloud.db`, canal de notification `ciaocloud_transfer`, classes `CiaoCloud*`, thème `Theme.CiaoCloud`, dossiers de build et AVD.

- Code (noms de classes, fonctions, variables) en anglais technique standard, conventions Android/Kotlin.
- UI et messages utilisateur en français.
- Commits au format `type: description` (ex: `feat: scan MediaStore photos et vidéos`).
- Tests unitaires ciblés sur la logique pure (chemins, collisions, doublons, fuseau, chronologie) et les use cases via faux repositories — pas de sur-investissement en tests UI/instrumentation.
- Base Room : migrations explicites uniquement (`data/local/Migrations.kt`), jamais de migration destructive ; schémas exportés dans `schemas/` (à commiter).
- La galerie v2 (lots 1 à 7 de la spec v2) est implémentée. La visionneuse suit la DA néo-brutaliste de l'app (barres `NeoTopBar`/`NeoActionBar`, fond de page du thème), pas un fond noir.
- La v3 (lots 1 à 7 de la spec v3 : édition des photos, métadonnées, partage sans métadonnées) est implémentée. Points à vérifier sur le Pixel listés dans la section « Points ouverts » de la spec (Ultra HDR, photo animée, vidéos HDR/Dolby Vision au partage, durée d'export 50 Mpx, `moveDocument` sur le SSD USB).
- La v4 (lots 1 à 7 de la spec v4 : onglet **Trier**, bilan, confirmation des suppressions, réinitialisation) est implémentée. Choix des points ouverts : gauche = supprimer, droite = garder, haut = plus tard ; snooze fixe de 7 jours (`TriageRules`) ; icône `Style` ; pas de badge de file en attente sur l'onglet.
- Décision de tri (`triage_state`) : même clé que le favori (`GalleryItem.triageKey`). Tout endroit qui renomme ou oublie une clé de favori (transfert, déplacement SSD de `SafeFileEditor`, suppression galerie) doit faire de même sur `TriageRepository`.
- Toute écriture d'un média existant passe par `SafeFileEditor` (domaine) : fichier de travail vérifié, journal de reprise (`filesDir/edit-journal/`), enregistrements de transfert retirés puis restaurés ou mis à jour d'après l'état relu des fichiers (invariant A1). Ne jamais écrire un original du téléphone ou du SSD en dehors de ce chemin.
- `ExifInterface` écrit les textes en US-ASCII : les textes accentués passent par `ExifInterfaceMetadataWriter` (gabarit puis remplacement en place par l'UTF-8) et se lisent avec `ExifText.decode`.
- Réglages et filtres : un seul shader AGSL (`res/raw/photo_adjustments.agsl`) pour l'aperçu (`RenderEffect`) et l'export par tuiles (`HardwareRenderer`) ; toute modification doit garder les deux rendus identiques.

## Tests sur appareil

- L'émulateur et le Pixel peuvent être branchés en même temps : toujours cibler explicitement avec `adb -s emulator-5554` / `ANDROID_SERIAL`. Ne jamais effacer les données (`pm clear`) ni supprimer de médias sur le Pixel réel.
- Build debug interprété très lent sur l'émulateur : `adb shell cmd package compile -m speed -f dev.ybdn.ciaocloud` avant de juger les performances.
- `screencap` ne capture pas toujours la surface vidéo : utiliser `adb emu screenrecord screenshot <fichier>`.
