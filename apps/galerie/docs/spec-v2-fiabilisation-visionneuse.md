# CiaoCloud v2 — Fiabilisation du rangement et visionneuse

> Spécification rédigée le 2026-09-16. Elle **complète** `prompt-initial.md`, qui reste la référence pour tout ce qui n'est pas traité ici. En cas de contradiction, ce document fait foi (notamment : la galerie, exclue de la v1, entre dans le périmètre).

Décisions validées par l'utilisateur :

| Sujet | Décision |
|---|---|
| Doublons à destination | Comparer le contenu, ignorer la copie si identique |
| Vidéos filmées dans un autre fuseau | Reprendre le décalage horaire des photos voisines |
| Périmètre de la visionneuse | Chronologie unifiée téléphone + SSD |
| Fonctions de la visionneuse | Lecture vidéo, partager/supprimer, app par défaut, infos et favoris |

---

## Partie A — Fiabilisation du rangement sur le SSD

À livrer **avant** la visionneuse : ces corrections protègent les prochains transferts réels.

### A1. Dossiers existants insensibles à la casse

**Problème.** `SafDestinationWriter.ensureDirectory` utilise `DocumentFile.findFile(segment)`, qui compare les noms en respectant la casse. Si le SSD contient `dcim/` et que l'app cherche `DCIM`, elle tente de créer `DCIM` ; sur exFAT (insensible à la casse) le provider crée alors un dossier `DCIM (1)`.

**Règle.** Un segment de chemin (`DCIM`, année, mois, jour) est considéré comme existant si un **dossier** du même nom existe **sans tenir compte de la casse**. Le dossier existant est réutilisé tel quel (son nom n'est pas modifié).

**Hors périmètre.** Les dossiers au format différent (`2025/4/21`, `2025-04`, `Avril`…) ne sont pas reconnus : le format `DCIM/aaaa/MM/jj` reste strict, un dossier conforme est créé à côté. Pas de réorganisation automatique d'une arborescence existante.

### A2. Doublons : comparer puis ignorer

**Problème.** Si une photo a déjà été copiée sur le SSD en dehors de l'app (même nom, même dossier jour), l'app en crée une seconde copie `IMG_…_1.jpg`.

**Règle.** Lors du calcul du nom à destination, pour le nom souhaité **et chacune de ses variantes suffixées déjà présentes** (`IMG.jpg`, `IMG_1.jpg`, `IMG_2.jpg`…, comparaison insensible à la casse) :

1. Si la taille du fichier existant ≠ taille source → ce n'est pas un doublon, candidat suivant.
2. Si les tailles sont égales → **comparaison octet par octet** des deux flux (source MediaStore avec `setRequireOriginal`, fichier SSD via SAF), arrêtée au premier octet différent.
   - Comparaison exacte plutôt que hash : aucun faux positif possible, alors que la conséquence est de rendre l'original **supprimable** ; le coût (lecture des deux fichiers) est le même qu'un hash.
3. Si un fichier identique est trouvé → **aucune copie**. Le média est enregistré en Room comme `VERIFIED`, avec `destinationPath` = chemin du fichier existant et `checksum` = CRC32 calculé pendant la comparaison. Il devient éligible à la suppression du téléphone comme un fichier transféré.
4. Sinon → comportement actuel : premier nom libre avec suffixe `_n`, copie puis vérification.

**Progression et bilan.**
- La comparaison fait avancer la progression du fichier en cours (octets lus) pour ne pas sembler figée sur une vidéo lourde.
- Nouveau compteur dans le bilan de transfert et la notification : « X déjà présents sur le SSD » (distinct de « copiés et vérifiés »).
- Nouveau `TransferProgress` (ex. `FileAlreadyPresent`) plutôt qu'un `FileCompleted` ambigu.

**Limite assumée.** Seul le **dossier du jour calculé** est examiné, et seulement pour les noms en collision. Une copie manuelle renommée ou rangée ailleurs n'est pas détectée.

**Tests unitaires (domaine).** Décision de doublon extraite en logique pure : aucun candidat, taille différente, taille égale + contenu différent, doublon sur une variante `_2`, casse différente.

### A3. Fuseau horaire des vidéos : décalage des photos voisines

**Problème.**
- Photos : `DateTimeOriginal` est une heure locale du lieu de prise de vue. Aujourd'hui elle est interprétée dans le fuseau du téléphone puis reformatée dans ce même fuseau, ce qui redonne la bonne date (hors cas limite des changements d'heure).
- Vidéos : `METADATA_KEY_DATE` est un instant **UTC**, converti dans le fuseau **actuel** du téléphone. Une vidéo tournée à 23 h 30 à New York, triée une fois rentré à Paris, tombe dans le dossier du lendemain.

**Modèle.** `MediaFile` porte, en plus de l'instant, un décalage UTC optionnel :

```kotlin
val capturedAtEpochMillis: Long?        // instant UTC
val captureUtcOffsetMinutes: Int?       // décalage du lieu de prise de vue, si connu
```

`DestinationPathResolver` calcule le jour avec ce décalage s'il est connu, sinon avec le fuseau du téléphone (comportement actuel).

**Photos.**
- Lire `OffsetTimeOriginal` (EXIF 2.31, écrit par l'appareil photo Pixel) en plus de `DateTimeOriginal`. Si présent : instant = heure locale − décalage, décalage = valeur lue.
- Si absent : comportement actuel (fuseau du téléphone), décalage `null`.

**Vidéos — inférence depuis les photos voisines** (logique pure, domaine : `CaptureOffsetInferrer`) :

1. Candidats : photos **du même scan** ayant un décalage connu.
2. Retenir la photo dont l'instant est **le plus proche** de celui de la vidéo, à condition que l'écart soit **≤ 12 h**.
3. Si trouvée → la vidéo prend son décalage. Sinon → fuseau du téléphone.
4. En cas d'égalité d'écart, préférer la photo **antérieure** à la vidéo.

Les vidéos sans date de métadonnées (fallback `DATE_TAKEN`/`DATE_MODIFIED` MediaStore, aussi en UTC) suivent la même inférence.

**Limites assumées.**
- Une vidéo isolée (aucune photo avec décalage dans les 12 h) garde le fuseau du téléphone.
- Les photos déjà supprimées du téléphone ne servent pas de référence (seul le scan courant est utilisé).

**Tests unitaires.** Photo la plus proche retenue, écart > 12 h ignoré, égalité → photo antérieure, aucun candidat, résolution du jour avec décalage positif/négatif autour de minuit.

---

## Partie B — Visionneuse (remplacement de Google Photos)

### B1. Principes

- **Une seule chronologie** regroupant les médias du téléphone et ceux archivés sur le SSD.
- La visionneuse reste utilisable **SSD débranché** : les médias archivés restent visibles en vignettes (cache local), l'affichage en pleine résolution demande de brancher le SSD.
- Aucun réseau (pas de carte en ligne, pas de géocodage), aucune dépendance réseau dans les bibliothèques ajoutées.
- Règle « aucune tâche automatique » : elle vise le scan de délestage, le transfert et la suppression, qui restent manuels. L'actualisation de l'affichage de la galerie (observation de MediaStore) n'est pas une tâche ; l'indexation complète du SSD, elle, est **déclenchée manuellement** (voir B3).

### B2. Navigation

L'app s'ouvre désormais sur la galerie. Barre de navigation basse, style néo-brutaliste :

| Onglet | Contenu |
|---|---|
| **Photos** | Chronologie unifiée (nouvel écran principal) |
| **Délester** | Parcours actuel en étapes 01–04 (écrans existants inchangés) |
| **Réglages** | Écran Paramètres actuel + réglages galerie |

Écrans secondaires : visionneuse plein écran, panneau d'infos, favoris (filtre), corbeille.

### B3. Sources de données

**Téléphone.** MediaStore, volume interne uniquement (même périmètre que le scan, cf. mémoire projet : pas de `.nomedia`, pas de `MANAGE_EXTERNAL_STORAGE`). Requête des colonnes légères uniquement (id, dates, type, taille, dimensions, chemin relatif). Rafraîchissement via `ContentObserver`.

**SSD — index local.** Lister récursivement `DCIM/` via SAF est lent (une requête provider par dossier). Un **index Room** est donc maintenu :

- Table `ssd_media` : chemin relatif (clé), nom, taille, type (déduit de l'extension/MIME), date du jour déduite du chemin `DCIM/aaaa/MM/jj`, `lastModified`, dimensions si connues.
- Mise à jour **incrémentale automatique** lors de chaque transfert (le chemin écrit est connu : pas de listing).
- **Réindexation manuelle** « Actualiser le SSD » (Réglages et état vide de la galerie) : parcours complet avec progression, ajout/suppression des entrées. Seuls les dossiers conformes `aaaa/MM/jj` sont indexés.
- L'index est conservé SSD débranché.

**Unification.** Un élément de chronologie (`GalleryItem`) peut être :

| Présence | Badge | Déterminée par |
|---|---|---|
| Téléphone seulement, jamais transféré | « Non sauvegardé » | Pas d'enregistrement `VERIFIED` |
| Téléphone + SSD | « Sauvegardé » | Enregistrement `VERIFIED` dont `destinationPath` est dans l'index |
| SSD seulement | « SSD » | Entrée d'index sans média téléphone correspondant |

Le lien téléphone ↔ SSD repose sur l'enregistrement de transfert Room (`mediaStoreId` ↔ `destinationPath`) : pas de rapprochement heuristique par nom.

**Date et ordre.**
- Date d'un élément = date de prise de vue locale (règles de la partie A). Pour un élément SSD seul : jour issu du chemin ; heure = instant de l'enregistrement de transfert s'il existe, sinon ordre par nom de fichier (les noms d'appareils photo sont horodatés).
- Tri décroissant, groupement par jour.

### B4. Écran Photos (grille)

- `LazyVerticalGrid` : en-têtes par jour (« Mercredi 16 septembre 2026 »), pincement pour passer de 3 à 5 colonnes, barre de défilement rapide avec repère mois/année.
- Vignettes : fond neutre, pas de bordure par tuile (lisibilité et performance). Badges discrets : durée pour les vidéos, cœur pour les favoris, icône « non sauvegardé » / « SSD ».
- Élément SSD seul, SSD débranché : vignette depuis le cache si disponible (sinon tuile neutre), légèrement estompée.
- Filtres : Tout / Favoris / Non sauvegardés / Vidéos.
- Sélection multiple par appui long (puis appui simple) : barre d'actions Partager, Supprimer, Favori.
- Performance cible : première grille affichée en < 1 s pour 20 000 médias téléphone ; défilement fluide.

**Vignettes.**
- Téléphone : `ContentResolver.loadThumbnail` (vignettes système).
- SSD : décodage sous-échantillonné depuis l'URI SAF (image) ou extraction d'image (vidéo), mises en **cache disque LRU dans le stockage de l'app, 500 Mo par défaut**, taille réglable dans Réglages avec bouton « Vider le cache ».

### B5. Visionneuse plein écran

- `HorizontalPager` sur la liste filtrée courante ; fond noir quel que soit le thème ; barres système masquables d'un appui.
- **Photos** : zoom par pincement et double-appui, **décodage par tuiles** (sous-échantillonnage) pour les photos de 50 Mpx ; formats JPEG, HEIC, AVIF, PNG, WebP, DNG (aperçu).
- **Vidéos** : lecteur intégré — lecture/pause, barre de progression avec déplacement, son on/off, pause automatique en changeant de page ou en quittant l'écran. Lecture depuis l'URI MediaStore ou SAF.
- Élément SSD seul, SSD débranché : vignette agrandie + message « Branchez le SSD pour afficher l'original ».
- Actions : Partager, Supprimer, Favori, Infos.

### B6. Partager

- `ACTION_SEND` / `ACTION_SEND_MULTIPLE` avec `FLAG_GRANT_READ_URI_PERMISSION`.
- URI MediaStore : partagées directement.
- URI SAF (SSD) : partage direct de l'URI de document ; **si l'app destinataire ne peut pas la lire**, repli par copie temporaire dans le cache de l'app exposée via `FileProvider` (nettoyée au lancement suivant).

### B7. Supprimer

| Élément | Action proposée | Mécanisme | Réversible |
|---|---|---|---|
| Téléphone, sauvegardé | Supprimer du téléphone | `MediaStore.createTrashRequest` | Oui (corbeille système, 30 j) |
| Téléphone, **non sauvegardé** | Idem, avec avertissement « Ce média n'est pas sauvegardé sur le SSD » | `createTrashRequest` | Oui |
| SSD seul | Supprimer du SSD | `DocumentsContract.deleteDocument` | **Non** — confirmation explicite dans l'app |
| Téléphone + SSD | Choix : téléphone / SSD / partout | Combinaison des deux | Partiellement |

- La visionneuse utilise la **corbeille** système (contrairement au délestage, qui supprime définitivement des fichiers vérifiés).
- Écran **Corbeille** : liste des médias en corbeille (`IS_TRASHED`), restaurer ou supprimer définitivement (`createTrashRequest(…, false)` / `createDeleteRequest`).
- **Cohérence Room (obligatoire).** Supprimer la copie SSD d'un média encore sur le téléphone **invalide** son enregistrement `VERIFIED` (retour à un état non transféré) : il redevient proposé au transfert et ne peut plus être supprimé du téléphone comme « vérifié ». L'entrée d'index SSD est retirée.
- Supprimer la copie téléphone d'un média sauvegardé passe son enregistrement en `DELETED` (comportement existant).

### B8. Application par défaut

- Nouvelle activité `ViewerActivity` exportée avec filtres d'intentions :
  - `android.intent.action.VIEW` pour `image/*` et `video/*` (schémas `content` et `file`) ;
  - `android.provider.action.REVIEW` et `android.provider.action.REVIEW_SECURE` (retour depuis l'appareil photo).
- Ouverture d'une URI externe (ex. pièce jointe) : visionneuse **mono-élément**, partage autorisé, suppression seulement si l'URI appartient à MediaStore.
- **`REVIEW_SECURE`** (appareil verrouillé) : activité `showWhenLocked`, n'affiche **que** les médias transmis par l'appareil photo, aucune navigation vers la galerie, aucune suppression ni partage sans déverrouillage (`KeyguardManager.requestDismissKeyguard`).
- **À vérifier sur le Pixel 10 Pro** : l'app Appareil photo Pixel peut ouvrir Google Photos en priorité quand elle est installée. Le choix par défaut se règle dans Paramètres Android › Applis par défaut ; documenter la procédure dans le README après test.

### B9. Infos et favoris

**Panneau d'infos** (feuille glissante) :
- Date et heure de prise de vue avec décalage horaire, nom de fichier, taille, dimensions, durée (vidéo).
- Appareil, objectif, ISO, ouverture, vitesse (EXIF).
- Coordonnées GPS en texte + bouton « Ouvrir dans une app de cartes » (intent `geo:`), aucune carte intégrée. Requiert `ACCESS_MEDIA_LOCATION` (déjà déclarée).
- Emplacements : « Téléphone : Pictures/… », « SSD : DCIM/2025/04/21/… », statut de sauvegarde.

**Favoris** :
- Table Room `favorites`, clé stable : `ssd:<chemin relatif>` si le média est sur le SSD, sinon `phone:<mediaStoreId>`.
- **Migration de clé** : quand un média favori est transféré (ou reconnu comme doublon, A2), sa clé passe de `phone:` à `ssd:` dans la même transaction que l'enregistrement `VERIFIED`, pour que le favori survive à la suppression du téléphone.
- Filtre « Favoris » dans la grille ; action favori dans la grille (sélection) et la visionneuse.

### B10. Architecture

Respect de l'architecture existante (domaine pur Kotlin, ViewModels → use cases uniquement).

- **domain/**
  - Modèles : `GalleryItem`, `GalleryLocation` (téléphone / SSD / les deux), `MediaDetails`.
  - Interfaces : `PhoneGallerySource`, `SsdMediaIndex`, `FavoritesRepository`, `MediaTrash`.
  - Use cases : `ObserveTimelineUseCase` (fusion téléphone + index SSD + transferts + favoris), `RefreshSsdIndexUseCase`, `DeleteGalleryItemsUseCase`, `RestoreFromTrashUseCase`, `ToggleFavoriteUseCase`, `GetMediaDetailsUseCase`.
  - Logique pure testée : `CaptureOffsetInferrer`, décision de doublon, fusion de la chronologie, migration de clé favori, groupement par jour.
- **data/** : `MediaStoreGallerySource`, `SafSsdIndexer`, entités/DAO `ssd_media` et `favorites`, `ExifDetailsReader`, `MediaStoreTrash`.
- **presentation/** : `gallery/` (grille), `viewer/` (pager, image zoomable, lecteur vidéo), `trash/`, `ViewerActivity`, barre de navigation.

**Base Room.** Passage en version 2 avec une **`Migration` explicite** (jamais `fallbackToDestructiveMigration` : l'état de transfert doit survivre). Activer `exportSchema = true` et versionner les schémas.

**Dépendances ajoutées** (sans module réseau) :

| Besoin | Bibliothèque |
|---|---|
| Chargement et cache d'images, images de vidéos | Coil 3 (`coil-compose`, `coil-video`) |
| Zoom + décodage par tuiles | Telephoto (`zoomable-image-coil3`) |
| Lecture vidéo | Media3 (`media3-exoplayer`, `media3-ui-compose`) |

### B11. Tests

- Unitaires (domaine, comme aujourd'hui) : cas listés en A2, A3, B10.
- Pas de tests UI/instrumentation ; validation manuelle sur le Pixel pour chaque lot, en particulier : SSD débranché pendant la navigation, `REVIEW_SECURE` écran verrouillé, partage d'un média SSD vers une app tierce.

---

## Découpage en lots

Chaque lot est livrable et testable seul, sur `develop`.

| Lot | Contenu | Dépend de |
|---|---|---|
| 1 | A1 casse des dossiers, A2 doublons, A3 fuseau vidéo | — |
| 2 | Onglets, grille téléphone seule, visionneuse photo avec zoom | — |
| 3 | Index SSD, chronologie unifiée, badges, cache de vignettes | 2 |
| 4 | Lecteur vidéo | 2 |
| 5 | Sélection multiple, partager, supprimer, corbeille, cohérence Room | 3 |
| 6 | App par défaut (`VIEW`, `REVIEW`, `REVIEW_SECURE`) | 4, 5 |
| 7 | Panneau d'infos, favoris et migration de clé | 3 |

## Points ouverts

- Comportement réel de l'app Appareil photo Pixel vis-à-vis d'une galerie par défaut tierce (B8).
- Durée de réindexation d'un SSD volumineux via SAF : à mesurer au lot 3 ; si trop lente, envisager une réindexation par année.
- Taille par défaut du cache de vignettes (500 Mo) : à ajuster à l'usage.
