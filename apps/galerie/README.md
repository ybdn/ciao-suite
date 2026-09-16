# CiaoCloud

Application Android **strictement personnelle**, sans backend ni compte, qui délestage
manuellement les photos et vidéos du stockage local d'un téléphone vers un SSD externe branché
en USB-C (OTG), en les rangeant par date (`DCIM/{année}/{mois}/{jour}`), puis supprime les
originaux du téléphone uniquement après vérification de la copie.

La spécification complète et faisant autorité du projet est [`prompt-initial.md`](prompt-initial.md).

## Prérequis

- [Android Studio](https://developer.android.com/studio) (dernière version stable) ou un JDK 17
  en ligne de commande.
- Un appareil Android (ou émulateur) sous une version récente d'Android pour tester le transfert
  vers un SSD en OTG — l'émulation d'un périphérique de stockage externe est limitée, un test sur
  appareil physique est recommandé.

## Builder l'APK

Depuis la racine du projet :

```bash
./gradlew :app:assembleDebug
```

L'APK généré se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

Un build `release` nécessite une configuration de signature (`signingConfigs`) qui n'est pas
fournie dans ce dépôt (usage strictement personnel, distribution par APK debug ou signé
manuellement). Pour builder en release une fois une configuration de signature ajoutée :

```bash
./gradlew :app:assembleRelease
```

## Installer l'APK en local (pas de Play Store)

1. Récupérer l'APK (build local ci-dessus, ou artifact téléchargé depuis un run GitHub Actions —
   voir [CI/CD](#cicd)).
2. Transférer l'APK sur le téléphone (câble USB, ou `adb install app-debug.apk`).
3. Autoriser l'installation d'applications depuis une source inconnue si demandé par le système.
4. Ouvrir le fichier APK sur le téléphone pour lancer l'installation.

## Autoriser l'accès au SSD (première utilisation)

1. Brancher le SSD au téléphone via l'adaptateur USB-C OTG.
2. Ouvrir CiaoCloud, puis appuyer sur **Choisir le dossier SSD** (écran d'accueil ou Paramètres).
3. Dans le sélecteur système, naviguer jusqu'à la racine du SSD et confirmer.
4. L'autorisation est persistée (`takePersistableUriPermission`) : elle n'est pas redemandée aux
   lancements suivants, tant que le SSD reste accessible avec le même volume/chemin. Si la
   permission est révoquée (SSD reformaté, changé...), l'app re-proposera la sélection.

## Workflow d'utilisation

1. Brancher le SSD.
2. Scanner les médias locaux (bouton **Scanner les médias**).
3. Vérifier le résumé (nombre de photos/vidéos, taille totale) puis lancer le **Transfert**.
4. Le transfert tourne en Foreground Service avec notification de progression.
5. Une fois tous les fichiers vérifiés (taille + checksum), confirmer explicitement la
   **suppression** des originaux sur le téléphone (déclenchée via
   `MediaStore.createDeleteRequest()`, avec confirmation système).

Aucune tâche automatique/planifiée : chaque étape (scan, transfert, suppression) est déclenchée
manuellement par l'utilisateur.

## Workflow Git

- `main` : branche stable/protégée. Jamais de commit direct — uniquement via merge/PR depuis
  `develop`.
- `develop` : branche de travail par défaut, tout le développement courant s'y fait.
- Convention de commits : `type: description` (ex. `feat: scan MediaStore photos et vidéos`).

Flux standard : travailler sur `develop` (ou une branche de fonctionnalité fusionnée dans
`develop`), ouvrir une pull request `develop` → `main` une fois stable, merger après revue et
passage de la CI.

## CI/CD

- **`build-main.yml`** : sur push/merge vers `main`, build l'APK (`assembleDebug`, en attendant
  une configuration de signature pour `assembleRelease`) et l'upload comme artifact du run
  GitHub Actions — récupérable manuellement sans build local.
- **`pr-check.yml`** : sur pull request vers `develop`/`main`, build de vérification
  (`assembleDebug`) + tests unitaires, pour éviter de casser `main`.

Le lint (`ktlint`/`detekt`) n'est pas encore intégré à la CI — à ajouter ultérieurement si jugé
utile.

## Tests

Les tests unitaires ciblent la logique métier pure, sans dépendance Android :

```bash
./gradlew :app:testDebugUnitTest
```

- `DestinationPathResolverTest` : calcul du chemin `année/mois/jour` (padding à 2 chiffres,
  changement de fuseau horaire).
- `FileNameCollisionResolverTest` : résolution des collisions de nom à destination
  (suffixes `_1`, `_2`, ...).

Pas de sur-investissement en tests UI/instrumentation pour ce projet personnel.

## Architecture

Clean Architecture allégée, séparation par package (pas de multi-module Gradle) :

- `domain/` — Kotlin pur, aucune dépendance Android.
- `data/` — implémentations concrètes (MediaStore, SAF/DocumentFile, Room).
- `presentation/` — Compose + ViewModels.
- `service/` — `TransferForegroundService`.

Voir [`CLAUDE.md`](CLAUDE.md) et [`prompt-initial.md`](prompt-initial.md) pour le détail complet.
