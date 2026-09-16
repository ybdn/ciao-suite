# Projet : C!ao — Application Android personnelle de délestage photos/vidéos vers SSD

## Contexte

Application Android **strictement personnelle**, installée en APK unique sur un Google Pixel 10 Pro (dernier Android disponible). Pas de multi-utilisateur, pas de compte, pas de cloud, pas de backend. L'app tourne 100% en local sur l'appareil.

- **Nom de l'app** : C!ao (anciennement CiaoCloud)
- **Application ID** : `dev.ybdn.ciaocloud`

## Objectif fonctionnel

Transférer manuellement toutes les photos **et vidéos** du stockage local du téléphone vers un disque SSD externe connecté en USB-C (OTG), les ranger dans une arborescence par date, puis libérer l'espace du téléphone en supprimant les originaux — uniquement après vérification de la copie et confirmation explicite de l'utilisateur.

## Arborescence de rangement sur le SSD

```
SSD/DCIM/{année}/{mois}/{jour}/nom_du_fichier.ext
```

Exemple : `SSD/DCIM/2025/04/21/IMG_20250421_143022.jpg`

- Date utilisée : **date EXIF de prise de vue en priorité** (`DateTimeOriginal`) pour les photos, avec **fallback sur la date de fichier** (date de création/modification MediaStore) si l'EXIF est absent ou illisible.
- Pour les vidéos (pas d'EXIF standard) : utiliser les métadonnées disponibles (ex: `MediaMetadataRetriever` — date de création si présente) avec fallback sur la date de fichier MediaStore.
- Mois et jour toujours sur 2 chiffres (`04`, pas `4`).
- Photos et vidéos partagent la même arborescence par date (pas de séparation photos/vidéos dans les dossiers).
- Gestion des collisions de nom (même nom déjà présent à destination) : renommer en ajoutant un suffixe (ex: `_1`, `_2`) plutôt qu'écraser.

## Workflow utilisateur (déclenchement manuel uniquement, aucune tâche automatique/planifiée)

1. **Connexion du SSD** : l'utilisateur branche le SSD en USB-C OTG.
2. **Sélection du dossier racine** (première utilisation ou si la permission a été révoquée) : via Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), l'utilisateur pointe la racine du SSD. L'URI est persistée (`takePersistableUriPermission`) pour ne pas redemander à chaque lancement.
3. **Scan** : l'app liste, via `MediaStore` (collections `Images` et `Video`), toutes les photos et vidéos présentes sur le stockage local (dossier DCIM et éventuellement Pictures/Movies — scope exact à confirmer en v1, périmètre large par défaut).
4. **Écran de résumé pré-transfert** : nombre de fichiers détectés (photos / vidéos séparément), taille totale estimée, espace disponible sur le SSD (si calculable via `DocumentFile`/`StatFs`).
5. **Transfert** (bouton "Transférer") :
   - Copie fichier par fichier vers le chemin calculé (création des dossiers année/mois/jour si absents).
   - Vérification post-copie (comparaison taille + hash, ex: CRC32 ou MD5) avant de marquer le fichier comme "transféré avec succès".
   - Progression affichée en temps réel (nombre de fichiers traités / restants, Mo transférés). Les vidéos étant plus lourdes, prévoir un affichage de progression au niveau du fichier en cours (pas juste au niveau du lot).
   - Le transfert tourne dans un **Foreground Service avec notification de progression**, pour survivre à une mise en arrière-plan de l'app sur un gros volume de données.
6. **Écran de confirmation de suppression** : une fois tous les transferts vérifiés, afficher un résumé ("X fichiers vérifiés sur le SSD, Y Mo libérables") et demander une confirmation explicite avant toute suppression.
7. **Suppression** : uniquement les fichiers dont la copie est vérifiée. Utiliser `MediaStore.createDeleteRequest()` (API 30+) pour déclencher la demande de suppression groupée avec confirmation système, plutôt que de gérer manuellement les `RecoverableSecurityException`.

## Gestion des cas limites (à implémenter, pas en option)

- SSD débranché en cours de transfert → arrêt propre, état cohérent (rien de supprimé côté téléphone tant que non vérifié), message d'erreur clair.
- Espace insuffisant sur le SSD → détection avant transfert si possible, sinon interruption propre à l'échec d'écriture.
- Reprise après interruption : les fichiers déjà copiés+vérifiés lors d'une session précédente ne doivent pas être re-scannés/re-proposés (persistance locale d'un état "transféré", ex. table Room avec l'ID MediaStore + hash + statut + type photo/vidéo).
- Permission SAF révoquée entre deux lancements → re-proposer la sélection du dossier proprement, sans crash.
- Fichier illisible/corrompu → skip + log, ne bloque pas le reste du lot.
- Vidéos volumineuses : prévoir un timeout/gestion d'erreur robuste sur les copies longues, ne pas bloquer l'UI thread.

## Stack technique

- **Kotlin + Jetpack Compose** (natif, pas de framework cross-platform).
- **Coroutines + Flow** pour le scan, le transfert asynchrone et le reporting de progression.
- **Room** (léger) pour persister l'état de transfert (éviter les re-scans/doublons).
- **DataStore** (pas SharedPreferences) pour l'URI SAF persistée et les préférences simples.
- Aucune dépendance backend, aucun réseau, aucune authentification.
- `minSdk` et `targetSdk` : dernière version stable Android disponible (application mono-device, pas de contrainte de compatibilité descendante).
- Lecture EXIF : `androidx.exifinterface`.
- Métadonnées vidéo : `MediaMetadataRetriever` (API Android standard).

## Architecture recommandée : Clean Architecture allégée (KISS, pas de sur-ingénierie pour une app perso)

Séparation stricte en 3 couches, dépendances orientées vers le domaine (le domaine ne connaît rien d'Android) :

- **domain/** (pur Kotlin, aucune dépendance Android) : modèles métier (`MediaFile`, `TransferStatus`...), interfaces de repository (`MediaRepository`, `DestinationWriter`), use cases — `ScanLocalMediaUseCase`, `TransferMediaUseCase`, `VerifyTransferUseCase`, `DeleteVerifiedMediaUseCase`.
- **data/** (implémentations concrètes des interfaces du domaine) : `MediaStoreRepositoryImpl` (scan photos + vidéos locales), `SafDestinationWriter` (écriture/arborescence côté SSD via `DocumentFile`), `TransferStateDao` + entités Room.
- **presentation/** (Compose + ViewModels) : écran Accueil/Scan, écran Progression, écran Confirmation suppression, écran Paramètres (sélection dossier SSD). Les ViewModels n'appellent que les use cases du domaine, jamais directement `data/`.
- **service/** : `TransferForegroundService`, orchestrateur technique qui invoque les use cases du domaine, expose la progression via `Flow`/notification.
- Pas de multi-module Gradle pour une app de cette taille (sur-ingénierie inutile) — la séparation se fait par package, pas par module. Pas de DI framework lourd si évitable (Hilt acceptable si ça simplifie l'injection dans les ViewModels/Service, sinon injection manuelle assumée — trancher pour la simplicité).

## Gestion du dépôt Git

- Initialisation d'un dépôt **GitHub** dès le scaffolding (`git init`, premier commit, remote à configurer).
- Deux branches :
  - `main` : toujours stable/déployable, protégée (pas de commit direct — uniquement via merge/PR depuis `develop`).
  - `develop` : branche de travail par défaut, c'est ici que se fait tout le développement courant.
- `.gitignore` Android standard (build/, .gradle/, local.properties, \*.apk générés en local, fichiers de signature).
- Convention de commits simple et lisible (type: description, ex: `feat: scan MediaStore photos et vidéos`).

## CI/CD (GitHub Actions)

- Un workflow déclenché sur **merge/push vers `main`** : build de l'APK (assembleRelease ou assembleDebug selon signature disponible), upload de l'APK en tant qu'**artifact** du run GitHub Actions (`actions/upload-artifact`), pour récupération manuelle sans avoir à builder en local.
- Un workflow léger déclenché sur les **pull requests vers `develop`/`main`** : lint (`ktlint`/`detekt` si simple à intégrer) + build de vérification (`assembleDebug`) + tests unitaires, pour éviter de casser `main`.
- Pas de publication Play Store (app strictement personnelle, distribution par APK uniquement).
- Optionnel, à évaluer : création automatique d'une release GitHub taguée avec l'APK attaché lors d'un merge sur `main`.

## Ce qu'on NE veut PAS en v1

- Pas de sauvegarde automatique/planifiée (déclenchement manuel exclusivement).
- Pas de cloud, pas de sync multi-appareil, pas de compte utilisateur.
- Pas de thumbnails/galerie riche en v1 (juste des compteurs et une liste simple si besoin de debug). **Levé en v2** : voir `docs/spec-v2-fiabilisation-visionneuse.md` (visionneuse, doublons, fuseaux horaires).
- Pas de transcodage/compression vidéo — copie brute uniquement.

## Livrables attendus de Claude Code

1. Initialisation du dépôt Git (branches `main` et `develop`), `.gitignore` Android, premier commit sur `develop`.
2. Scaffolding complet du projet Android (Gradle, structure de package `dev.ybdn.ciaocloud` en Clean Architecture domain/data/presentation/service, manifest avec les permissions nécessaires : accès médias — `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` —, `android.hardware.usb.host`).
3. Implémentation des couches domain/data/presentation/service décrites ci-dessus.
4. Workflows GitHub Actions : build + artifact APK sur merge vers `main`, lint/tests sur PR.
5. README en français expliquant : comment builder l'APK, comment l'installer en local (pas de Play Store), comment autoriser l'accès au SSD la première fois, et le workflow Git (`develop` → PR → `main`).
6. Tests unitaires ciblés sur la logique pure (calcul du chemin année/mois/jour à partir d'EXIF/métadonnées vidéo/fallback, résolution des collisions de nom) — pas de sur-investissement en tests UI/instrumentation pour un projet perso.
7. Nommage du code en anglais technique standard (conventions Android/Kotlin), UI et messages utilisateur en français.
