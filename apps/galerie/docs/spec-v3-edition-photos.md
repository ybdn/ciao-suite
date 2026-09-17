# C!ao v3 — Édition des photos et partage confidentiel

> Spécification rédigée le 2026-09-16. Elle **complète** `prompt-initial.md` et `docs/spec-v2-fiabilisation-visionneuse.md`, qui restent la référence pour tout ce qui n'est pas traité ici. En cas de contradiction, ce document fait foi.

Décisions validées par l'utilisateur :

| Sujet | Décision |
|---|---|
| Enregistrement d'une photo retouchée | **Copie par défaut** ; « Remplacer l'original » proposé en second choix, avec confirmation |
| Retouches | Lumière, couleur, détails et effets, filtres prédéfinis |
| Métadonnées modifiables | Date et fuseau, position GPS, textes (description, auteur, copyright), nettoyage confidentialité |
| Date modifiée d'une photo rangée sur le SSD | Le fichier est **déplacé** dans le bon dossier `DCIM/aaaa/MM/jj` |
| Partage sans métadonnées | Réglage on/off : s'il est activé, les médias partagés sont des **copies sans EXIF** ; les originaux ne sont jamais modifiés |

Décisions de conception proposées dans ce document (à contester si besoin) :

| Sujet | Proposition | Raison |
|---|---|---|
| Modification des métadonnées | **En place**, sans copie, pixels jamais réencodés | Une copie ne différant que par la date dupliquerait la photo dans la chronologie |
| Média présent sur le téléphone **et** le SSD | Toute modification s'applique aux **deux** fichiers ; SSD branché obligatoire | Garantit que la copie SSD reste identique à l'original, condition de sa suppression du téléphone |
| Vidéos | Hors périmètre de l'édition (ni retouche ni métadonnées) | Demande limitée aux photos ; réencodage vidéo exclu depuis la v1 |
| Partage sans métadonnées : photos | **Réencodage** plutôt que retrait ciblé de balises | Aucune métadonnée oubliée (XMP, IPTC, MakerNote, vignette, vidéo de photo animée) par construction |
| Partage sans métadonnées : vidéos | Incluses, par **remultiplexage sans réencodage** | Les vidéos portent aussi lieu et date ; la qualité est préservée |
| Partage sans métadonnées : valeur par défaut | **Désactivé** | Comportement actuel du partage inchangé tant que l'utilisateur ne l'active pas |

---

## Partie A — Principes et invariants

### A1. Invariant de sécurité (non négociable)

La règle métier centrale reste : **un original du téléphone n'est supprimable que si sa copie SSD est vérifiée identique**. L'édition ne doit jamais la contourner.

- Tout changement du contenu d'un fichier du téléphone qui n'est **pas** reproduit à l'identique et vérifié sur le SSD **supprime** son enregistrement de transfert (le média redevient « Non sauvegardé » et sera reproposé au transfert).
- Quand les deux fichiers sont modifiés et vérifiés (CRC32 relu sur le SSD), l'enregistrement reste `VERIFIED` avec `checksum` et `sizeBytes` mis à jour.
- Aucune écriture ne laisse un fichier partiellement écrit à la place d'un original (voir A4).

### A2. Formats pris en charge

| Format source | Retouche (pixels) | Rotation sans perte | Métadonnées |
|---|---|---|---|
| JPEG (dont Ultra HDR, photo animée Pixel) | Oui → JPEG | Oui (balise `Orientation`) | Oui |
| PNG | Oui → PNG | Non (réencodage, sans perte) | Oui |
| WebP | Oui → WebP (qualité 95) | Non | Oui |
| HEIC, AVIF | **Copie seulement** → JPEG | Non | Lecture seule |
| DNG | Non (aperçu seulement) | Non | Lecture seule |
| GIF, photos > 200 Mpx | Non | Non | Non |

- `ExifInterface.saveAttributes` n'écrit que JPEG, PNG et WebP : d'où la lecture seule des autres formats (message « Métadonnées non modifiables pour ce format »).
- HEIC/AVIF : la copie change d'extension, « Remplacer l'original » est donc indisponible.
- JPEG réencodé en qualité 95. Espace colorimétrique de la source conservé (Display P3 sur Pixel), profil ICC intégré à l'encodage.

### A3. Où s'applique une modification

| Emplacement de l'élément | Copie retouchée créée… | « Remplacer » / métadonnées modifient… | SSD requis |
|---|---|---|---|
| Téléphone seul | Sur le téléphone, même dossier MediaStore | Le fichier du téléphone | Non |
| SSD seul | Sur le SSD, même dossier jour | Le fichier du SSD | Oui |
| Téléphone + SSD | Sur le téléphone (elle sera transférée ensuite) | **Les deux fichiers** | Oui |

- Élément externe hors MediaStore (pièce jointe), mode `REVIEW_SECURE` (appareil verrouillé), SSD requis mais débranché : actions d'édition masquées ou désactivées avec message (« Branchez le SSD pour modifier cette photo »).
- **Pendant un transfert en cours** (`TransferForegroundService` actif) : édition désactivée, pour ne pas modifier un fichier en cours de copie ou de vérification.

Cette table est une logique pure du domaine (`EditCapabilitiesPolicy`) : entrée = emplacement, format, SSD disponible, transfert actif, source externe/verrouillée ; sortie = actions disponibles et raison de chaque indisponibilité.

### A4. Écriture sûre des fichiers

Toute écriture passe par un **fichier de travail** complet dans le stockage de l'app, puis par un remplacement sûr.

**Copie (nouveau fichier).**
- Téléphone : `MediaStore.Images` insert dans le même `RELATIVE_PATH` avec `IS_PENDING = 1`, écriture, relecture CRC32, puis `IS_PENDING = 0`. Échec → ligne supprimée.
- SSD : création dans le même dossier jour, écriture, `fsync`, relecture CRC32. Échec → fichier supprimé. Entrée d'index `ssd_media` ajoutée, vignette ajoutée au cache.

**Remplacement sur le SSD** (pas de renommage atomique en SAF) :
1. Écrire `<nom>.ciao-new` dans le même dossier, vérifier son CRC32 en relecture.
2. Renommer l'original en `<nom>.ciao-old`.
3. Renommer `<nom>.ciao-new` en `<nom>`.
4. Supprimer `<nom>.ciao-old`.

**Remplacement sur le téléphone** (écriture `"wt"` non atomique) :
1. Obtenir le droit d'écriture par `MediaStore.createWriteRequest` (confirmation système, par lot).
2. Sauvegarder les octets de l'original dans `filesDir/edit-journal/` (pas le cache, que le système peut vider).
3. Écrire le nouveau contenu, relire et vérifier le CRC32.
4. Succès → supprimer la sauvegarde. Échec → réécrire l'original depuis la sauvegarde.

**Journal de reprise.** Chaque opération en cours est décrite dans `filesDir/edit-journal/<id>.json` (type, fichiers, étape). Au lancement de l'app et avant toute réindexation SSD :
- sauvegarde téléphone restante → réécriture de l'original, puis suppression ;
- `<nom>.ciao-old` sans `<nom>` → renommé en `<nom>` ; `<nom>.ciao-old` avec `<nom>` vérifié → supprimé ;
- `<nom>.ciao-new` orphelin → supprimé.
- La réindexation SSD ignore les fichiers `*.ciao-new` et `*.ciao-old`.

**Ordre pour un élément Téléphone + SSD :** droit d'écriture téléphone d'abord (un refus annule tout sans rien toucher), puis SSD, puis téléphone, puis mise à jour de l'enregistrement de transfert. Si l'écriture téléphone échoue après celle du SSD, l'enregistrement est supprimé (invariant A1).

---

## Partie B — Éditeur de retouche

### B1. Entrée et navigation

- Nouvelle action **Modifier** (icône crayon) dans la barre de la visionneuse, pour les photos éditables (A2, A3).
- Écran plein écran `PhotoEditorScreen`, DA néo-brutaliste : `NeoTopBar` avec **Annuler** (croix) et **Enregistrer**, aperçu au centre, outils en bas.
- Onglets d'outils : **Recadrer** · **Lumière** · **Couleur** · **Effets** · **Filtres**.
- Appui long sur l'aperçu : affiche l'original tant que le doigt reste posé (comparaison avant/après).
- Boutons **Annuler / Rétablir** (historique de 50 étapes) et **Tout réinitialiser**.
- Quitter avec des modifications non enregistrées → dialogue « Abandonner les modifications ? ».
- La recette en cours est conservée via `SavedStateHandle` (survie à la mort du processus).

### B2. Recadrer et pivoter

- **Rotation 90°** (sens anti-horaire, appuis successifs) et **miroir horizontal** (le miroir vertical s'obtient par miroir + 2 rotations).
- **Redressement** fin de −45° à +45° par pas de 0,1°, molette graduée, grille de repères affichée pendant le geste.
- **Cadre** : poignées d'angle et de bord, déplacement du cadre, pincement pour zoomer l'image sous le cadre.
- **Proportions** : Libre, Original, 1:1, 4:3, 3:4, 16:9, 9:16, 3:2, 2:3. Une rotation de 90° inverse la proportion (4:3 → 3:4).
- Avec un redressement non nul, le cadre est **contraint à rester entièrement dans l'image pivotée** (jamais de coins vides) ; il est réduit au plus grand rectangle inscrit si nécessaire.
- Taille minimale du cadre : 64 px de l'image source sur chaque côté.

**Rotation sans perte.** Si la recette ne contient **que** des rotations 90° et/ou un miroir (pas de recadrage, redressement, réglage ni filtre) et que la source est un JPEG, l'enregistrement **ne réencode pas** : seule la balise EXIF `Orientation` est réécrite (composition avec l'orientation existante, valeurs 1 à 8). Qualité, Ultra HDR et photo animée intacts.

### B3. Réglages

Tous les curseurs vont de −100 à +100 (0 = neutre) sauf mention. Double-appui sur un curseur → remise à 0. La valeur s'affiche pendant le geste.

| Onglet | Réglage | Effet attendu |
|---|---|---|
| Lumière | Luminosité | Décalage des tons moyens (courbe gamma), sans écrêter les extrêmes |
| | Contraste | Courbe en S autour du gris moyen |
| | Hautes lumières | Récupère (−) ou éclaircit (+) les zones claires uniquement |
| | Ombres | Débouche (+) ou assombrit (−) les zones sombres uniquement |
| | Blancs | Déplace le point blanc |
| | Noirs | Déplace le point noir |
| Couleur | Saturation | Intensité uniforme des couleurs (−100 = noir et blanc) |
| | Vibrance | Intensité renforcée sur les couleurs peu saturées, tons chair préservés |
| | Température | Froid (−) / chaud (+) |
| | Teinte | Vert (−) / magenta (+) |
| Effets | Netteté | 0 à 100, masque flou (rayon proportionnel à la taille de l'image) |
| | Vignettage | Assombrit (+) ou éclaircit (−) les bords, relatif au cadre recadré |

**Ordre d'application** (fixe) : géométrie (orientation → redressement → recadrage) → filtre → lumière → couleur → netteté → vignettage.

### B4. Filtres

- Bandeau de vignettes : **Original**, **N&B**, **N&B contrasté**, **Chaud**, **Froid**, **Vif**, **Doux**, **Fané**.
- Un filtre est un **jeu de valeurs des réglages B3** (plus un mélange de canaux pour les N&B), défini dans le domaine ; pas de LUT externe.
- Curseur **Intensité** 0–100 % (défaut 100 %) : valeurs du filtre × intensité.
- Les réglages manuels s'**ajoutent** au filtre : valeur effective = filtre × intensité + réglage manuel, bornée à [−100, +100]. Changer de filtre ne réinitialise pas les réglages manuels.

### B5. Rendu

**Aperçu (temps réel).**
- Image de travail sous-échantillonnée, grand côté ≤ 2560 px, orientation EXIF appliquée.
- Géométrie : transformation Compose sur l'aperçu.
- Réglages, filtre, netteté, vignettage : **un seul shader AGSL** (`RuntimeShader`, API 33 = `minSdk`) appliqué via `RenderEffect`.
- Cible : 60 i/s pendant le déplacement d'un curseur.

**Export (pleine résolution).**
1. Décodage complet (`ImageDecoder`, allocation logicielle), orientation appliquée.
2. Géométrie sur CPU (`Canvas` + `Matrix`, filtrage bilinéaire).
3. **Même shader AGSL** qu'à l'aperçu, rendu hors écran (`HardwareRenderer` + `ImageReader`) **par tuiles** de 4096 px avec recouvrement de 16 px (netteté), pour respecter la taille maximale de texture.
4. Encodage selon A2, puis métadonnées (B6), puis écriture sûre (A4).
- Les paramètres dépendant de la taille (rayon de netteté, vignettage) sont exprimés en fraction de la diagonale : l'aperçu correspond à l'export.
- Un seul export à la fois ; indicateur de progression bloquant avec étapes (« Calcul… », « Enregistrement… »).
- Cible : export d'une photo de 50 Mpx en moins de 5 s sur le Pixel 10 Pro.

**Ultra HDR (API 34+).** Si la source porte une carte de gain (`Bitmap.getGainMap()`), la même transformation géométrique lui est appliquée (à l'échelle de la carte) et elle est rattachée au résultat avant `compress` : la photo reste HDR. Les réglages de ton s'appliquent à l'image de base seulement.

**Photo animée Pixel.** Un réencodage supprime la vidéo intégrée : les balises XMP `MotionPhoto`/`GContainer` sont retirées du résultat. Avertissement dans l'éditeur : « La copie sera une photo fixe ». La rotation sans perte (B2) et la modification des métadonnées conservent la vidéo.

### B6. Métadonnées d'une photo retouchée

Le fichier encodé ne contient aucune métadonnée ; elles sont recopiées depuis l'original selon une **liste blanche** :

- Recopiées : dates et décalages (`DateTimeOriginal`, `DateTimeDigitized`, `OffsetTime*`, `SubSecTime*`), appareil, objectif, paramètres de prise de vue, GPS, textes, droits.
- Modifiées : `Orientation` = 1 (pixels déjà orientés), dimensions (`ImageWidth`, `ImageLength`, `PixelXDimension`, `PixelYDimension`), `Software` = « C!ao », `DateTime` = maintenant.
- Non recopiées : vignette EXIF (elle montrerait l'image non recadrée), `MakerNote`, XMP de photo animée.

### B7. Enregistrer

Appui sur **Enregistrer** → feuille avec :

1. **Enregistrer une copie** (action principale).
2. **Remplacer l'original** (secondaire, masqué si indisponible selon A2/A3) → dialogue de confirmation : « L'original sera définitivement remplacé. Cette action est irréversible. » ; pour un élément Téléphone + SSD, ajouter « sur le téléphone et sur le SSD ».

**Nom de la copie :** `<nom>_edit.<ext>` (`<ext>` = `jpg` pour une source HEIC/AVIF), puis `_edit_1`, `_edit_2`… en cas de collision, comparaison insensible à la casse (réutilise `FileNameCollisionResolver`).

**Après enregistrement :**
- Copie : la visionneuse s'ouvre sur la copie (elle apparaît à côté de l'original, même date de prise de vue), message « Copie enregistrée ». La copie téléphone est « Non sauvegardée ».
- Remplacement : retour à la visionneuse sur la photo mise à jour, message « Photo remplacée ».
- Caches : les vignettes téléphone se renouvellent déjà via `DATE_MODIFIED` dans la clé Coil ; la vignette SSD est retirée puis régénérée (`SsdThumbnailCache`). Index `ssd_media` : taille et `lastModified` mis à jour.
- Favori : une copie n'hérite pas du favori ; un remplacement le conserve (clé inchangée).

---

## Partie C — Modification des métadonnées

### C1. Entrée

- Panneau d'infos (`InfoSheet`) : bouton **Modifier** → feuille `MetadataEditorSheet`.
- Grille, sélection multiple : action **Modifier les infos** (menu de la barre de sélection) pour les modifications groupées (C6). Les éléments non éditables de la sélection sont ignorés et comptés dans le récapitulatif.
- Modification **en place** (voir décisions). Un original du téléphone passe par `createWriteRequest` (confirmation système) ; le processus d'écriture sûre (A4) s'applique : copie de travail, `ExifInterface.saveAttributes` sur la copie, vérification, remplacement.
- Pixels, Ultra HDR, photo animée et balises non concernées restent intacts.

### C2. Date et fuseau

- Champs : **date**, **heure** (secondes comprises), **décalage UTC** (liste de −12:00 à +14:00 par quarts d'heure, ou « Inconnu »).
- Écrit `DateTimeOriginal` et `DateTimeDigitized` (format `aaaa:MM:jj HH:mm:ss`), `OffsetTimeOriginal` et `OffsetTimeDigitized` (`±HH:mm`, ou balise supprimée si « Inconnu »). `SubSecTime*` et l'horodatage GPS (UTC du fix) ne sont pas modifiés.
- Aperçu sous les champs : « Rangée dans DCIM/2025/04/20 » (calcul de `DestinationPathResolver` avec le décalage saisi).

### C3. Position GPS

- **Saisie texte** acceptant : décimal (`48.8584, 2.2945`), degrés-minutes-secondes (`48°51'30"N 2°17'40"E`), URI `geo:48.8584,2.2945`. Altitude optionnelle (m).
- **Copier la position** (panneau d'infos d'une photo géolocalisée) puis **Coller la position** (éditeur) : presse-papiers de l'app, pratique pour géolocaliser une série. Aucun réseau, aucune carte intégrée.
- **Vérifier dans une app de cartes** : intent `geo:` (comme la v2).
- **Supprimer la position** : retire toutes les balises `GPS*`.
- Écriture : `GPSLatitude`/`Ref`, `GPSLongitude`/`Ref`, `GPSAltitude`/`Ref` si saisie ; les autres balises GPS existantes (direction, horodatage) sont retirées si la position change, car devenues incohérentes.
- Validation : latitude ∈ [−90, 90], longitude ∈ [−180, 180], sinon message « Coordonnées invalides ».

### C4. Textes

- **Description** (`ImageDescription`), **Auteur** (`Artist`), **Copyright** (`Copyright`) ; champ vidé = balise supprimée.
- Encodage UTF-8 (accents français), longueur max 2000 caractères.

### C5. Nettoyage confidentialité

- Action **Supprimer les données sensibles**, dialogue listant ce qui sera retiré :
  - position : toutes les balises `GPS*` ;
  - appareil : `Make`, `Model`, `LensMake`, `LensModel` ;
  - identifiants : `BodySerialNumber`, `LensSerialNumber`, `CameraOwnerName`, `ImageUniqueID` ;
  - logiciel : `Software`, `MakerNote`.
- Conservés : dates, paramètres de prise de vue, textes (modifiables en C4), XMP (nécessaire à la photo animée et à l'Ultra HDR).
- **Limite assumée :** les données éventuellement présentes dans le XMP ne sont pas nettoyées.

### C6. Modifications groupées (sélection multiple)

- **Décaler la date** de ±N jours/heures/minutes (corriger un appareil mal réglé) : chaque photo conserve son écart relatif.
- **Définir le fuseau** : fixe le décalage sans changer l'heure locale affichée.
- **Définir / coller / supprimer la position**, **définir les textes**, **nettoyage confidentialité** : même valeur pour toutes.
- Récapitulatif avant application : « 24 photos modifiées, 3 déplacées sur le SSD, 2 ignorées (format non pris en charge) ».
- Une seule `createWriteRequest` pour les originaux du téléphone (par lots de 500), puis traitement séquentiel avec progression ; un échec sur une photo n'arrête pas le lot (bilan final).

### C7. Déplacement sur le SSD après changement de date

Pour tout fichier SSD dont le jour calculé après modification (`DestinationPathResolver`, décalage saisi s'il est connu) diffère de son dossier actuel :

1. Assurer le dossier `DCIM/aaaa/MM/jj` (réutilisation insensible à la casse, v2 A1).
2. Appliquer la **décision de doublon** v2 A2 dans le dossier cible (`DuplicateResolver`) :
   - fichier identique déjà présent → supprimer le fichier déplacé, les références pointent vers l'existant ;
   - sinon → premier nom libre (`_1`, `_2`…).
3. Déplacer : `DocumentsContract.moveDocument` si le fournisseur le permet, sinon copie + vérification CRC32 + suppression de la source (journalisé, A4).
4. Dans **une transaction Room** : chemin et `captureEpochDay`/`capturedAtEpochMillis` de `ssd_media`, `destinationPath` de tous les enregistrements de transfert pointant vers l'ancien chemin (`getByDestinationPath`), clé favori `ssd:<ancien>` → `ssd:<nouveau>`.
5. Cache de vignettes : entrée renommée (ou retirée).

- Un dossier jour devenu vide **n'est pas supprimé** (pas de réorganisation implicite du SSD).
- Élément téléphone seul : pas de déplacement ; la chronologie suit `DATE_TAKEN` recalculé par MediaStore, et le prochain transfert rangera la photo selon la nouvelle date.

---

## Partie D — Partage sans métadonnées

Indépendante des parties B et C : livrable en premier si souhaité.

### D1. Réglage

- Écran **Réglages**, nouvelle carte **Confidentialité** : interrupteur **« Partager sans métadonnées »**.
- Texte d'aide : « Les photos et vidéos partagées sont envoyées sous forme de copies sans date, lieu, appareil ni autres métadonnées. Vos originaux ne sont pas modifiés. »
- Valeur persistée dans DataStore (`share_strip_metadata`, booléen, **désactivé** par défaut).
- Aucun choix au moment du partage : le réglage s'applique à tous les partages tant qu'il est activé.

### D2. Périmètre

S'applique à **tout** partage déclenché depuis l'app :
- grille, sélection multiple ;
- visionneuse de la chronologie ;
- visionneuse d'une URI externe (`VIEW`, `REVIEW`).

Réglage activé :
- **Tous les médias** passent par une copie nettoyée, y compris ceux du téléphone (aujourd'hui partagés directement par leur URI MediaStore). Aucune URI d'original n'est transmise.
- Les copies suivent le mécanisme existant de `FileProviderShareableMediaProvider` : dossier `cacheDir/shared/<lot>/<index>/`, exposées par `FileProvider`, effacées au lancement suivant.
- Elles ne sont **jamais** écrites dans MediaStore ni sur le SSD.

Réglage désactivé : comportement actuel inchangé (v2 B6).

### D3. Ce qui est retiré et conservé

| Retiré | Conservé |
|---|---|
| Tout l'EXIF : dates, décalages, GPS, appareil, objectif, numéros de série, paramètres de prise de vue, logiciel, textes, `MakerNote` | Pixels (réencodés en qualité 95 pour les formats avec perte) |
| XMP, IPTC, vignette EXIF, manifeste C2PA | Orientation, **appliquée aux pixels** (plus besoin de balise) |
| Vidéo intégrée d'une photo animée (la copie est une photo fixe) | Profil colorimétrique ICC (Display P3) : nécessaire au rendu, non personnel |
| Vidéo : lieu (`©xyz`), date de création, appareil, pistes de métadonnées capteurs (gyroscope…) | Carte de gain Ultra HDR (API 34+) |
| | Vidéo : pistes vidéo et audio sans réencodage, rotation d'affichage, informations HDR portées par le format de piste |

**Limite assumée :** le **nom du fichier** est conservé (ex. `PXL_20260916_101010123.jpg`), alors qu'il contient la date de prise de vue. Voir Points ouverts.

### D4. Traitement par format

| Source | Traitement | Copie partagée |
|---|---|---|
| JPEG | Décodage (`ImageDecoder`, orientation appliquée) → `Bitmap.compress` JPEG 95, carte de gain rattachée si présente | `.jpg` |
| PNG | Décodage → PNG | `.png` |
| WebP | Décodage → WebP 95 (sans perte si la source l'est) | `.webp` |
| HEIC, AVIF, DNG | Décodage → JPEG 95 | `.jpg` (extension changée) |
| GIF | **Non pris en charge** (animation perdue au réencodage) | — |
| Vidéo (MP4, MOV, 3GP, WebM) | `MediaExtractor` → `MediaMuxer` : copie des échantillons des seules pistes vidéo et audio, `setOrientationHint` repris de la source, aucune `setLocation` | Même conteneur (MP4 pour MOV) |

- Décodage pleine résolution : pas de réduction de taille (le but est la confidentialité, pas la compression).
- Un seul média traité à la fois (mémoire : ≈ 200 Mo pour une photo de 50 Mpx).

### D5. Contrôle après nettoyage (défense en profondeur)

Chaque copie est relue avant d'être partagée :
- photo : `ExifInterface` ne doit renvoyer **aucune** des balises de date, GPS, appareil, numéro de série, logiciel, texte, et aucun XMP ;
  - *Précision d'implémentation (2026-09-16) :* un XMP limité aux descripteurs techniques de la carte de gain (`hdrgm`, `Container`, `Item`) est admis, car l'encodeur Ultra HDR l'écrit et D3 conserve la carte de gain ; toute autre propriété XMP fait échouer le média.
- vidéo : `MediaMetadataRetriever` ne doit renvoyer ni `METADATA_KEY_LOCATION` ni `METADATA_KEY_DATE` exploitable (la date « epoch 0 » ou absente est acceptée).

Si une donnée subsiste, la copie est **supprimée et traitée comme un échec** (D6). Jamais de repli silencieux vers l'original.

### D6. Déroulement et erreurs

- Dialogue bloquant **« Retrait des métadonnées… (3/12) »** avec progression et bouton **Annuler** (supprime les copies déjà produites, aucun partage).
- **Espace insuffisant** : avant de commencer, estimation = somme des tailles sources × 1,2 comparée à l'espace libre du cache (`StatFs`) → message « Espace insuffisant pour préparer le partage (X Go nécessaires) ».
- **Échec sur un ou plusieurs médias** (format non pris en charge, décodage impossible, piste refusée par `MediaMuxer`, contrôle D5 en échec) → dialogue :
  - « 2 médias n'ont pas pu être nettoyés » (avec leurs noms) ;
  - **Partager les autres** (si au moins un a réussi) ou **Annuler**.
  - Pas d'option « partager l'original » : désactiver le réglage reste possible pour cela.
- SSD débranché pour un média SSD seul : comportement existant (`ShareSsdUnavailable`).
- `REVIEW_SECURE` : inchangé (partage après déverrouillage), puis nettoyage.

## Partie E — Architecture

Respect de l'architecture existante (domaine pur Kotlin, ViewModels → use cases uniquement). **Aucun changement de schéma Room** : les tables `transfer_state`, `ssd_media` et `favorites` suffisent.

- **domain/**
  - Modèles : `EditRecipe` (orientation, redressement, cadre normalisé, proportion, réglages, filtre + intensité ; `isIdentity`, `isOrientationOnly`), `CropAspect`, `FilterPreset`, `MetadataChanges` (champs `Keep` / `Set` / `Remove`), `DateShift`, `GeoPoint`, `EditCapabilities`, `SaveMode` (`COPY`, `REPLACE`).
  - Logique pure testée :
    - `OrientationCodec` : composition rotations/miroir ↔ valeur EXIF 1–8 ;
    - `CropGeometry` : proportion, rotation du cadre sur quart de tour, plus grand rectangle inscrit après redressement, taille minimale ;
    - `FilterPresets` : valeurs effectives filtre × intensité + manuel, bornage ;
    - `EditCapabilitiesPolicy` : table A2/A3 ;
    - `EditedFileNamer` : `_edit`, `_edit_n`, changement d'extension ;
    - `GpsCoordinateParser` : décimal, DMS, `geo:`, validation ;
    - `ExifWritePlan` : `MetadataChanges`/nettoyage → balises à écrire/supprimer (noms de balises en constantes texte, sans dépendance Android) ;
    - `CaptureDateRelocation` : nouveau dossier jour, décalage groupé, décision de déplacement.
  - Interfaces : `PhotoEditRenderer` (rendu pleine résolution vers un fichier de travail), `MetadataWriter` (applique un `ExifWritePlan` à un fichier de travail), `PhoneMediaWriter` (copie MediaStore, remplacement, droit d'écriture), `SsdMediaWriter` (création, remplacement sûr, déplacement), `EditJournal` (reprise).
  - Use cases : `GetEditCapabilitiesUseCase`, `SavePhotoEditUseCase(item, recipe, mode)`, `EditMetadataUseCase(items, changes)`, `RecoverInterruptedEditsUseCase`.
  - **Partage sans métadonnées (D)** :
    - interface `SharePreferences` (`observeStripMetadata()`, `setStripMetadata()`), implémentée par `SettingsDataStore` ;
    - logique pure `MetadataStripPlan` : type MIME → stratégie (`REENCODE_JPEG`, `REENCODE_PNG`, `REENCODE_WEBP`, `CONVERT_TO_JPEG`, `REMUX_VIDEO`, `UNSUPPORTED`), nom et type MIME de la copie, estimation d'espace ;
    - interface `MetadataStripper` (produit une copie nettoyée et contrôlée, ou un échec motivé) ;
    - `PrepareShareUseCase` lit le réglage et passe `stripMetadata` + progression à `ShareableMediaProvider.prepare`, qui renvoie un résultat détaillé (médias prêts, échecs, espace insuffisant, SSD indisponible) au lieu de `null`.
- **data/** : `AgslPhotoEditRenderer` (+ ressource shader `.agsl`), `ExifInterfaceMetadataWriter`, `MediaStorePhoneMediaWriter`, `SafSsdMediaWriter`, `FileEditJournal`, `ReencodingMetadataStripper` (photos : `ImageDecoder`/`compress` ; vidéos : `MediaExtractor`/`MediaMuxer` ; contrôle D5).
- **presentation/** :
  - `editor/` : `PhotoEditorScreen`, `PhotoEditorViewModel` (recette, historique, `SavedStateHandle`), `CropOverlay`, `StraightenDial`, `AdjustmentSlider` (style néo), `FilterStrip`, `SaveEditSheet` ;
  - `viewer/` : action Modifier, `MetadataEditorSheet`, copier/coller la position dans `InfoSheet` ;
  - `gallery/` : action groupée « Modifier les infos » ; `GalleryActions.share` gère la progression, l'annulation et le dialogue d'échecs (D6) ;
  - `settings/` : carte Confidentialité et interrupteur (D1).
- `RecoverInterruptedEditsUseCase` est appelé au démarrage (`CiaoCloudApplication`) et avant `RefreshSsdIndexUseCase`.

**Dépendances ajoutées :** aucune. AGSL, `HardwareRenderer`, `ImageDecoder`, `MediaExtractor`/`MediaMuxer` et `ExifInterface` (déjà présent) suffisent ; l'interface de recadrage est écrite en Compose.

## Partie F — Tests

- **Unitaires (domaine)** :
  - `OrientationCodec` : les 8 orientations × 4 rotations × miroir (table exhaustive) ;
  - `CropGeometry` : cadre contraint après redressement ±45°, proportion conservée, inversion au quart de tour, taille minimale ;
  - `FilterPresets` : intensité 0/50/100 %, addition et bornage ;
  - `EditCapabilitiesPolicy` : chaque ligne des tables A2/A3, SSD débranché, transfert actif, verrouillé ;
  - `EditedFileNamer`, `GpsCoordinateParser` (formats valides, hémisphères S/W, hors bornes) ;
  - `ExifWritePlan` : date + décalage, suppression de décalage, position (retrait direction/horodatage), nettoyage ;
  - `CaptureDateRelocation` : décalage groupé autour de minuit, fuseau négatif, dossier inchangé ;
  - `MetadataStripPlan` : chaque type MIME (dont casse et types inconnus), extension de la copie (HEIC → `.jpg`), estimation d'espace ;
  - `PrepareShareUseCase` : réglage désactivé (URI MediaStore directes, comme aujourd'hui), activé (aucune URI d'original), échecs partiels, espace insuffisant, annulation ;
  - use cases via faux repositories : remplacement Téléphone + SSD (succès, refus d'écriture, échec après SSD → enregistrement supprimé), déplacement avec doublon identique (références redirigées, favori renommé), reprise de journal (chaque étape interrompue).
- **Pas de tests UI/instrumentation.** Validation manuelle sur le Pixel pour chaque lot, notamment : photo 50 Mpx Ultra HDR (HDR conservé à l'affichage), photo animée (rotation sans perte → vidéo conservée ; retouche → photo fixe), coupure de l'app pendant un remplacement (reprise), SSD débranché pendant un déplacement, date modifiée puis transfert (bon dossier). Partage sans métadonnées : vérifier la copie reçue avec `exiftool` sur le Mac (photo JPEG/HEIC, photo animée, vidéo 4K HDR, média SSD seul), orientation correcte, HDR conservé.

---

## Découpage en lots

Chaque lot est livrable et testable seul, sur `develop`.

| Lot | Contenu | Dépend de |
|---|---|---|
| 1 | Fondations : `EditCapabilitiesPolicy`, écriture sûre (A4) et journal de reprise, writers téléphone/SSD, **rotation sans perte** depuis la visionneuse (copie/remplacer) | — |
| 2 | Éditeur : écran, recadrage, proportions, redressement, miroir, export géométrique, métadonnées B6, Ultra HDR, photo animée | 1 |
| 3 | Réglages lumière/couleur/effets : shader AGSL, aperçu temps réel, export par tuiles, avant/après, historique | 2 |
| 4 | Filtres prédéfinis et intensité | 3 |
| 5 | Métadonnées d'une photo : textes, position (saisie, copier/coller, suppression), nettoyage confidentialité | 1 |
| 6 | Date et fuseau, déplacement SSD (C7), modifications groupées (C6) | 5 |
| 7 | Partage sans métadonnées : réglage, nettoyage photos et vidéos, contrôle, progression et erreurs (partie D) | — |

## Points ouverts

- `ExifInterface.saveAttributes` conserve-t-il la carte de gain Ultra HDR (segment MPF, image secondaire) et la vidéo d'une photo animée ? À vérifier au lot 1 (`hasGainMap()` après écriture, lecture de la vidéo intégrée) ; sinon, écrire les segments EXIF sans réécrire le reste du fichier.
  - *Constat 2026-09-17 (émulateur, Android 15) :* sur un JPEG suivi de données après la fin d'image (comme la vidéo d'une photo animée), la rotation sans perte ne réécrit que le bloc EXIF : les octets à partir du marqueur SOS, données finales comprises, sont identiques ; GPS et autres balises conservés. **À vérifier sur le Pixel** : photo Ultra HDR (`hasGainMap()` après rotation ; le bloc EXIF grandit de quelques octets, les décalages MPF étant relatifs à l'en-tête MPF ils devraient rester valides) et photo animée (vidéo lisible dans Google Photos après rotation).
- MediaStore recalcule-t-il `DATE_TAKEN` et l'orientation après écriture via `ContentResolver` ? À vérifier au lot 1 ; sinon, mettre à jour les colonnes explicitement.
  - *Constat 2026-09-17 (émulateur) :* oui. Après remplacement via `ContentResolver` (`"wt"`), MediaStore rescanne le fichier : `orientation` et `date_modified` sont mis à jour, `datetaken` reste celui de l'EXIF. Une copie insérée avec `IS_PENDING` reçoit la même `datetaken` que l'original. Aucune mise à jour explicite des colonnes n'est nécessaire.
- Prise en charge de `moveDocument` par le fournisseur de stockage du SSD USB (repli copie + suppression prévu).
- Durée réelle d'export d'une photo de 50 Mpx et mémoire crête (≈ 200 Mo par copie ARGB) : à mesurer au lot 3.
  - *Constat 2026-09-17 (émulateur, GPU émulé) :* export d'une photo de 27 Mpx (6000 × 4500, 4 tuiles) avec réglages en ≈ 26 s ; aucune couture visible entre tuiles (écart entre colonnes au raccord inférieur à celui d'un bord de bloc JPEG) ; aperçu et export concordants. Mémoire crête théorique : source + copie de travail + résultat ARGB (≈ 3 × 4 octets/pixel). **À mesurer sur le Pixel** : durée pour 50 Mpx (cible < 5 s) et mémoire crête.
- Affichage de l'UTF-8 dans les balises EXIF texte par les autres apps (Google Photos, Windows, macOS) : à contrôler au lot 5.
  - *Constat 2026-09-17 :* `ExifInterface` encode les textes en US-ASCII (accents remplacés par « ? »). L'app écrit donc un gabarit ASCII de même longueur puis le remplace en place par l'UTF-8 (emplacement relu par `getAttributeRange`) ; le panneau d'infos décode l'UTF-8 (repli Latin-1). Contrôlé sur Mac : `exiftool` et `sips` (ImageIO, Aperçu) affichent « Tour Eiffel à Paris », « Anne Été ». **À vérifier** : Google Photos sur le Pixel et l'Explorateur Windows.
- Partage sans métadonnées : le **nom du fichier** révèle la date de prise de vue (`PXL_aaaaMMjj_…`). Ajouter une option « Renommer les fichiers partagés » (ex. `photo_1.jpg`) ?
- `MediaMuxer` accepte-t-il les pistes des vidéos Pixel (HEVC 10 bits HDR, Dolby Vision, audio multicanal) et conserve-t-il les métadonnées HDR dynamiques ? À mesurer au lot 7 ; les pistes refusées font échouer le média (D6), sans repli vers l'original.
  - *Constat 2026-09-16 :* non vérifiable sur l'émulateur (vidéos H.264 SDR uniquement). **À vérifier sur le Pixel** : partager une vidéo 4K HDR 10 bits et une vidéo Dolby Vision, contrôler avec `exiftool`/`mdls` que la copie se lit, reste HDR et ne porte ni lieu ni date.
- Date de création écrite par `MediaMuxer` dans l'en-tête MP4 : vérifier qu'elle ne reprend pas la date source (sinon, la remettre à zéro).
  - *Constat 2026-09-16 (émulateur, Android 15) :* les dates de création et de modification de `mvhd`, `tkhd` et `mdhd` sont désormais **remises à zéro systématiquement** après remultiplexage (`Mp4Timestamps`), quelle que soit la valeur écrite par `MediaMuxer` ; `exiftool` affiche `0000:00:00` et aucune position (`©xyz`) sur la copie. Seule balise restante : `AndroidVersion` (Keys), écrite par la plateforme, non personnelle. Rotation d'affichage conservée. MOV source → copie `.mp4` vérifiée.
