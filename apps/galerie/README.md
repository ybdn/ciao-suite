# C!ao Galerie

Application Android **sans backend, sans compte, sans cloud**, qui délestage manuellement les
photos et vidéos du stockage local d'un téléphone vers un SSD externe branché en USB-C (OTG), en
les rangeant par date (`DCIM/{année}/{mois}/{jour}`), puis supprime les originaux du téléphone
uniquement après vérification de la copie. Conçue pour un usage mono-utilisateur/mono-device :
chacun l'installe pour son propre usage, sans synchronisation entre appareils.

La spécification complète et faisant autorité du projet est [`prompt-initial.md`](prompt-initial.md)
(voir aussi [`docs/spec-v5-publication-publique.md`](docs/spec-v5-publication-publique.md) pour la
publication publique, qui prime en cas de contradiction). Licence [MIT](LICENSE) —
[politique de confidentialité](PRIVACY.md).

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

### Signature release

Le build `release` est minifié (R8) et non signé par défaut. Pour produire un APK/AAB signé,
créer un fichier `keystore.properties` à la racine du dépôt (gitignored, jamais commité) :

```properties
storeFile=/chemin/vers/ma-cle.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Générer la clé si besoin (à faire une seule fois, à conserver précieusement — sa perte empêche
toute mise à jour future de l'app publiée) :

```bash
keytool -genkeypair -v -keystore ma-cle.jks -alias ciao -keyalg RSA -keysize 2048 -validity 10000
```

Puis builder :

```bash
./gradlew :app:assembleRelease
# ou pour le Play Store :
./gradlew :app:bundleRelease
```

Sans `keystore.properties`, `assembleRelease` reste utilisable (build non signé, ex. CI).

## Installer l'APK en local

Pour un usage hors Play Store (dev, test) :

1. Récupérer l'APK (build local ci-dessus, ou artifact téléchargé depuis un run GitHub Actions —
   voir [CI/CD](#cicd)).
2. Transférer l'APK sur le téléphone (câble USB, ou `adb install app-debug.apk`).
3. Autoriser l'installation d'applications depuis une source inconnue si demandé par le système.
4. Ouvrir le fichier APK sur le téléphone pour lancer l'installation.

## Autoriser l'accès au SSD (première utilisation)

1. Brancher le SSD au téléphone via l'adaptateur USB-C OTG.
2. Ouvrir C!ao Galerie, puis appuyer sur **Choisir le dossier SSD** (écran d'accueil ou Paramètres).
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

Rangement : un dossier existant dont le nom ne diffère que par la casse (`dcim/`) est réutilisé ;
un fichier déjà présent à l'identique dans le dossier du jour n'est pas recopié (comptabilisé
« déjà présent sur le SSD ») ; les vidéos prennent le décalage horaire de la photo la plus proche
(≤ 12 h) pour être rangées au jour local de la prise de vue.

## Galerie

L'app s'ouvre sur l'onglet **Photos** : chronologie unifiée des médias du téléphone et du SSD.

- Badges : nuage barré = non sauvegardé, carte mémoire = uniquement sur le SSD.
- L'index du SSD est mis à jour à chaque transfert ; pour les fichiers copiés en dehors de l'app,
  **Réglages › Actualiser le SSD** (réindexation manuelle des dossiers `DCIM/aaaa/MM/jj`).
- SSD débranché : les médias archivés restent visibles grâce au cache de vignettes (taille
  réglable, 500 Mo par défaut) ; l'original demande de rebrancher le SSD.
- Sélection par appui long : partager, favori, supprimer. Suppression du téléphone = corbeille
  système (30 jours, écran **Corbeille**) ; suppression du SSD = définitive, double confirmation,
  et le média redevient « non transféré ».

## Édition des photos

Dans la visionneuse, le **crayon** ouvre l'éditeur (JPEG, PNG, WebP ; HEIC et AVIF en copie JPEG) :

- **Recadrer** : cadre à poignées, proportions (Libre, Original, 1:1, 4:3…), redressement ±45°,
  rotation et miroir. Une rotation ou un miroir seuls d'un JPEG sont enregistrés **sans perte**
  (balise `Orientation`), Ultra HDR et photo animée intacts.
- **Lumière**, **Couleur**, **Effets** : curseurs de −100 à +100 (double-appui = 0) ;
  **Filtres** avec intensité. Appui long sur l'aperçu : original. Annuler / Rétablir (50 étapes).
- **Enregistrer** : une copie `<nom>_edit` (par défaut, non sauvegardée) ou **Remplacer l'original**
  (confirmation, irréversible). Pour un média présent sur le téléphone et le SSD, les deux fichiers
  sont remplacés : SSD branché obligatoire. Une photo retouchée perd la vidéo d'une photo animée.

Dans le panneau **Infos**, **Modifier les infos** : date, heure et décalage UTC, position (saisie
décimale ou DMS, copier/coller entre photos), description, auteur, copyright, et
**Supprimer les données sensibles** (GPS, appareil, numéros de série, logiciel). Les pixels ne sont
jamais réencodés. Une photo du SSD dont le jour change est déplacée dans le bon dossier
`DCIM/aaaa/MM/jj`. En sélection multiple, l'icône **Modifier les infos** applique la même
modification à toutes les photos (décalage de date, fuseau, position, textes, nettoyage), après
un récapitulatif.

Chaque écriture passe par un fichier de travail vérifié et un journal de reprise : si l'app est
interrompue pendant un enregistrement, l'opération est terminée ou annulée au lancement suivant.
Un original du téléphone ne reste supprimable que si sa copie SSD est identique et vérifiée.
Édition indisponible pendant un transfert vers le SSD.

## Confidentialité : partager sans métadonnées

**Réglages › Confidentialité › Partager sans métadonnées** (désactivé par défaut). Activé, chaque
partage envoie des copies sans date, lieu, appareil, textes ni XMP : photos réencodées (orientation
appliquée, JPEG qualité 95, HEIC/AVIF convertis en JPEG), vidéos remultiplexées sans réencodage
(ni position ni date). Les originaux ne sont pas modifiés ; le nom du fichier est conservé. Un
média qui ne peut pas être nettoyé (GIF, piste vidéo refusée…) n'est jamais partagé tel quel :
l'app propose de partager les autres ou d'annuler.

### Définir C!ao Galerie comme visionneuse par défaut

C!ao Galerie répond à l'ouverture d'images/vidéos (`VIEW`) et au retour de l'appareil photo
(`REVIEW`, et `REVIEW_SECURE` écran verrouillé). Pour l'utiliser à la place de Google Photos :
Paramètres Android › Applis › Applis par défaut (ou, pour une app donnée, « Ouvrir par défaut »),
puis choisir C!ao Galerie au prochain choix d'app proposé.

À vérifier sur le Pixel : l'app Appareil photo Pixel peut ouvrir Google Photos en priorité quand
elle est installée ; procédure exacte à compléter après test sur l'appareil.

## Workflow Git

- `main` : branche stable/protégée. Jamais de commit direct — uniquement via merge/PR depuis
  `develop`.
- `develop` : branche de travail par défaut, tout le développement courant s'y fait.
- Convention de commits : `type: description` (ex. `feat: scan MediaStore photos et vidéos`).

Flux standard : travailler sur `develop` (ou une branche de fonctionnalité fusionnée dans
`develop`), ouvrir une pull request `develop` → `main` une fois stable, merger après revue et
passage de la CI.

## CI/CD

- **`build-main.yml`** : sur push/merge vers `main`, build l'APK (`assembleDebug`) et l'upload
  comme artifact du run GitHub Actions — récupérable manuellement sans build local. Pas de
  publication automatique sur le Play Store (`bundleRelease` signé se fait manuellement en local,
  voir [Signature release](#signature-release)).
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
- `DuplicateResolverTest` : détection des doublons déjà présents sur le SSD (taille, contenu,
  variantes suffixées, casse).
- `CaptureOffsetInferrerTest` : décalage horaire des vidéos déduit des photos voisines.
- `TimelineBuilderTest` : fusion téléphone + SSD, tri, groupement par jour, filtres.
- `Iso6709Test` : coordonnées GPS des vidéos.
- `TransferMediaUseCaseTest`, `DeleteGalleryItemsUseCaseTest` (avec faux repositories) :
  indexation, migration de la clé favori, cohérence de l'état de transfert après suppression.
- v3 (édition et partage) : `OrientationCodecTest` (8 orientations × rotations × miroir),
  `CropGeometryTest`, `FilterPresetsTest`, `EditHistoryTest`, `EditCapabilitiesPolicyTest`,
  `EditedFileNamerTest`, `GpsCoordinateParserTest`, `ExifWritePlanTest`, `CaptureDatesTest`,
  `MetadataStripPlanTest`, `Mp4TimestampsTest`, et avec faux repositories
  `SavePhotoEditUseCaseTest` (remplacement téléphone + SSD, reprise de journal),
  `EditMetadataUseCaseTest` (déplacement sur le SSD, doublon, modifications groupées),
  `PrepareShareUseCaseTest`.
- v4 (tri) : `TriageRulesTest` (pile, snooze, bilan, catégorisation des suppressions) et
  `TriageUseCasesTest` (décision, annulation, réinitialisation, suppression de la file).

Pas de sur-investissement en tests UI/instrumentation pour ce projet personnel.

## Tri de la pellicule

L'onglet **Trier** propose les médias un par un (téléphone et SSD, groupés par jour, du plus
récent au plus ancien) : swipe à droite = garder, à gauche = supprimer, vers le haut = revoir plus
tard (7 jours). Trois boutons équivalents sont affichés sous la carte ; l'icône d'annulation revient
sur la dernière décision. Les vidéos se lisent dans la carte, sur appui.

- Rien n'est supprimé par un swipe : les médias rejetés rejoignent une file d'attente, validée en
  bloc depuis le **Bilan** › *Voir les suppressions en attente* (téléphone vers la corbeille
  système, 30 jours ; SSD seul = suppression définitive, confirmée à part).
- La pile reprend toujours là où elle en était ; **Réglages › Tri › Réinitialiser le tri** y remet
  les médias gardés ou mis de côté (la file de suppression est conservée).

## Architecture

Clean Architecture allégée, séparation par package (pas de multi-module Gradle) :

- `domain/` — Kotlin pur, aucune dépendance Android.
- `data/` — implémentations concrètes (MediaStore, SAF/DocumentsContract, Room).
- `presentation/` — Compose + ViewModels.
- `service/` — `TransferForegroundService`.

Base Room versionnée avec migrations explicites (schémas exportés dans `app/schemas/`).

Voir [`CLAUDE.md`](CLAUDE.md), [`prompt-initial.md`](prompt-initial.md) et
[`docs/spec-v2-fiabilisation-visionneuse.md`](docs/spec-v2-fiabilisation-visionneuse.md) et
[`docs/spec-v3-edition-photos.md`](docs/spec-v3-edition-photos.md) et
[`docs/spec-v4-tri-pellicule.md`](docs/spec-v4-tri-pellicule.md) et
[`docs/spec-v5-publication-publique.md`](docs/spec-v5-publication-publique.md) pour le détail
complet.

## Licence et confidentialité

Code source sous licence [MIT](LICENSE). Voir la [politique de confidentialité](PRIVACY.md) pour
le détail des données locales accédées par l'application (aucune donnée n'est jamais collectée
ni transmise).
