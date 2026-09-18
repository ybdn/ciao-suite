# C!ao v4 — Tri de la pellicule (swipe)

> Spécification rédigée le 2026-09-18. Elle **complète** `prompt-initial.md` et les spécifications `spec-v2-fiabilisation-visionneuse.md` et `spec-v3-edition-photos.md`, qui restent la référence pour tout ce qui n'est pas traité ici. En cas de contradiction, ce document fait foi.

Nouvelle fonctionnalité : un écran de tri façon « Tinder » qui propose les médias un par un (photo ou vidéo) avec trois décisions possibles par swipe — **garder**, **supprimer**, **revoir plus tard**. Objectif : faciliter le nettoyage régulier de la pellicule, indépendamment du parcours de délestage vers le SSD.

Décisions validées par l'utilisateur :

| Sujet | Décision |
|---|---|
| Périmètre proposé au tri | Pellicule locale **et** médias déjà sur le SSD (chronologie unifiée, comme la galerie) |
| Effet de « garder » | Marque simplement le média comme revu ; aucun effet sur le transfert |
| Effet de « supprimer » | Ajoute à une file d'attente, validée en bloc sur un écran de confirmation dédié |
| Suppression d'un média téléphone non sauvegardé | Autorisée depuis cet écran, sans passer par un transfert préalable, avec confirmation explicite |
| Suppression d'un média déjà sur le SSD | Même file d'attente que le téléphone, confirmation groupée |
| Présentation vidéo | Vignette statique, lecture uniquement sur appui |
| Emplacement | Nouvel onglet dédié dans la barre de navigation, intitulé **Trier** |
| Annulation | Undo de la dernière décision uniquement |
| Ordre de présentation | Groupé par jour (comme la galerie), pour repérer facilement rafales et quasi-doublons |
| Reprise de session | Automatique : un média décidé ne réapparaît plus, la pile reprend toujours là où elle en était |
| Remise à zéro de « gardé »/« plus tard » | Possible depuis Réglages |
| Exclusions automatiques | Aucune — même périmètre que le scan/la galerie |
| Bilan de session | Écran récapitulatif (compteurs) avant validation des suppressions |

---

## 1. Principe et articulation avec l'existant

- Le tri **ne remplace ni le délestage ni la galerie** : c'est un troisième usage, autonome, qui partage leurs briques.
- La pile est construite à partir de la **même chronologie unifiée** que la galerie (`ObserveTimelineUseCase`, cf. B3/B10 de la spec v2) : chaque `GalleryItem` (téléphone, SSD, ou les deux) est un candidat.
- **Garder** ne fait qu'exclure l'élément des propositions futures : il ne déclenche ni transfert ni suppression. Le parcours de délestage (Exporter) reste le seul moyen de sauvegarder vers le SSD.
- **Supprimer** ne supprime rien immédiatement : l'élément est marqué « à supprimer » et rejoint une file d'attente, résolue plus tard sur un écran de confirmation dédié — jamais d'action irréversible déclenchée par un simple swipe.
- **Revoir plus tard** masque l'élément pour un délai, puis le fait réapparaître automatiquement dans la pile.

## 2. Modèle de données

### 2.1 Décision de tri

```kotlin
enum class TriageDecision { KEPT, QUEUED_FOR_DELETION, SNOOZED }
```

Table Room `triage_state` (nouvelle, `data/local/TriageStateEntity.kt`) :

```kotlin
@Entity(tableName = "triage_state")
data class TriageStateEntity(
    /** Même format que la clé de favori : `phone:<mediaStoreId>` ou `ssd:<chemin relatif>` (`FavoriteKeys`). */
    @PrimaryKey val key: String,
    val decision: TriageDecision,
    val decidedAtEpochMillis: Long,
    /** Renseigné uniquement pour `SNOOZED` : date de réapparition dans la pile. */
    val snoozeUntilEpochMillis: Long?,
)
```

Réutilisation délibérée de `FavoriteKeys` (déjà utilisé par `favorites`) : un média qui passe du téléphone au SSD (transfert, ou doublon reconnu en A2) doit voir sa décision de tri survivre au changement de clé, exactement comme un favori. La migration de clé lors du transfert (déjà en place pour les favoris, cf. B9) est étendue à `triage_state`.

**Pas de table de « session » ou de curseur.** La pile est recalculée à chaque ouverture de l'écran : chronologie unifiée filtrée des éléments ayant une entrée `KEPT`, `QUEUED_FOR_DELETION`, ou `SNOOZED` avec `snoozeUntilEpochMillis` dans le futur. Un média jamais tranché, ou dont le snooze a expiré, réapparaît naturellement — c'est ce mécanisme qui assure la reprise « là où on s'était arrêté » sans état de session dédié.

**Nouvel arrivage pendant une session.** Un média ajouté à la pellicule après le début du tri (nouvelle photo prise) apparaît simplement dans la pile lors du prochain recalcul (ex. réouverture de l'écran) ; aucune notion de pile figée à l'ouverture n'est nécessaire, la même règle de filtrage s'applique.

**Room.** Nouvelle migration explicite (version suivante), `exportSchema` déjà activé (cf. v2/B10).

### 2.2 File d'attente de suppression

Pas de table dédiée : un élément « à supprimer » est simplement une ligne `triage_state` avec `decision = QUEUED_FOR_DELETION`. L'écran de confirmation (§4) interroge cette table, exécute la suppression, puis **retire** les lignes traitées (contrairement à `KEPT`/`SNOOZED`, une fois exécutée la suppression n'a plus besoin d'être mémorisée : l'élément disparaît de la chronologie).

## 3. Écran de tri

### 3.1 Pile et ordre

- Source : `ObserveTimelineUseCase`, filtrée comme décrit en 2.1, aplatie en une pile unique triée par jour (le plus récent en premier, comme la grille galerie), puis par instant de prise de vue au sein d'un jour.
- Le regroupement par jour place naturellement les rafales et quasi-doublons côte à côte, ce qui facilite leur comparaison rapide pendant le tri.
- **Hors périmètre** : aucune détection automatique de quasi-doublons (flou, cadrage proche) — seul le regroupement chronologique aide visuellement ; la détection de doublons *exacts* à la destination reste celle de la spec v2 (A2), sans lien avec cet écran.

### 3.2 Carte et gestes

- Une carte = un `GalleryItem`, même traitement visuel que la vignette galerie agrandie (pas de fond noir, DA néo-brutaliste de l'app).
- **Photo** : image chargée en plein cadre (Coil, comme B4/B5).
- **Vidéo** : vignette statique (frame extraite, même mécanisme que B4) ; un appui lance la lecture inline (Media3, déjà intégré B10) ; pause automatique en quittant la carte (changement de swipe, retour, fermeture d'écran).
- Gestes : swipe gauche = supprimer, swipe droit = garder, swipe haut = revoir plus tard (mappage indicatif, à confirmer en maquette — trois boutons d'action équivalents sont aussi affichés sous la carte pour l'accessibilité et la précision tactile).
- Badge discret rappelant le statut de sauvegarde de l'élément (« Non sauvegardé » / « Sauvegardé » / « SSD »), comme en galerie (B3).

### 3.3 Undo

- Un bouton « Annuler » (visible tant qu'au moins une décision a été prise depuis l'ouverture de l'écran) revient sur la **dernière** décision uniquement : la ligne `triage_state` correspondante est supprimée (ou, pour un `SNOOZED`/`KEPT` qui remplaçait une décision antérieure — cas impossible en pratique puisqu'un élément décidé sort de la pile — simplement retirée) et la carte réapparaît en tête de pile.
- Pas de pile d'undo multi-niveaux : au-delà de la dernière décision, une correction passe par l'écran de confirmation (pour une suppression en attente) ou par un nouveau passage en tri (pour un « gardé »/« plus tard », via la réinitialisation en Réglages, §5).

### 3.4 Fin de pile et bilan

- Quand la pile est vide (ou sur demande, bouton « Bilan » toujours accessible pendant le tri), un écran récapitulatif affiche les compteurs de la session en cours : *X gardés*, *Y en attente de suppression (taille cumulée)*, *Z à revoir plus tard*.
- Depuis ce bilan : bouton **Voir les suppressions en attente** → écran de confirmation (§4) s'il y a au moins un élément `QUEUED_FOR_DELETION` (pas nécessairement ajouté pendant cette session — la file peut contenir des éléments de sessions précédentes non encore validés) ; bouton **Retour**.
- État vide (rien à trier) : message dédié, avec le nombre d'éléments actuellement en attente de suppression si non nul (invite à aller les valider).

## 4. Écran de confirmation des suppressions

Écran dédié (nouveau, `presentation/triageconfirm/`), **distinct** de `DeleteConfirmationScreen` existant (celui-ci reste propre au délestage : suppression définitive des originaux déjà vérifiés sur le SSD, cf. règle métier historique — non modifié par cette spec).

- Liste des éléments `QUEUED_FOR_DELETION`, groupés en deux catégories selon le mécanisme réellement appliqué :
  - **Téléphone** (sauvegardé ou non) : envoi à la corbeille système (`MediaTrash.moveToTrash`, réversible 30 jours), exactement le mécanisme déjà utilisé par la suppression depuis la galerie (B7/`DeleteGalleryItemsUseCase`, `DeleteTarget.EVERYWHERE` pour un élément présent des deux côtés). Un média **non sauvegardé** affiche l'avertissement déjà prévu en B7 : « Ce média n'est pas sauvegardé sur le SSD ».
  - **SSD seul** : suppression **définitive** (`DocumentsContract.deleteDocument`, non réversible), comme en B7 — bandeau d'avertissement propre à cette catégorie.
- Un élément présent **à la fois** sur le téléphone et le SSD, marqué à supprimer par swipe, est traité comme une suppression **partout** (`DeleteTarget.EVERYWHERE`) : le geste de tri est une décision de nettoyage définitive, pas une suppression partielle — cohérent avec l'intention du geste « swipe rejeté ».
- Validation groupée : réutilise directement `DeleteGalleryItemsUseCase` (aucune nouvelle logique de suppression n'est créée) avec la liste des `GalleryItem` correspondant aux clés en attente. En cas de refus partiel du système (annulation de la confirmation `IntentSender`), les lignes `triage_state` des éléments effectivement supprimés sont retirées, les autres restent en file d'attente.
- Un élément peut être retiré de la file individuellement depuis cet écran (avant validation), sans passer par l'écran de tri — repasse alors en `KEPT`… ou, plus simplement, la ligne `triage_state` est supprimée et l'élément redevient proposable au tri normal.

## 5. Réglages

- **Réinitialiser le tri** : supprime toutes les lignes `triage_state` de décision `KEPT` ou `SNOOZED` (la file d'attente de suppression `QUEUED_FOR_DELETION` n'est **jamais** effacée par cette action — une suppression en attente doit être validée ou retirée explicitement depuis l'écran de confirmation, §4). Confirmation demandée avant exécution (action large, irréversible sur l'historique de tri bien que sans effet sur les fichiers eux-mêmes).
- Durée du snooze (« revoir plus tard ») : valeur fixe raisonnable pour la v1 (7 jours) plutôt qu'un réglage — cf. Points ouverts.

## 6. Navigation

Nouvel onglet **Trier** dans `NeoBottomBar`, aux côtés de Photos / Exporter / Réglages (cf. B2 de la spec v2, désormais 4 onglets) :

| Onglet | Contenu |
|---|---|
| Photos | Chronologie unifiée (galerie, inchangé) |
| **Trier** | Nouvel écran de tri par swipe |
| Exporter | Parcours de délestage 01–04 (inchangé) |
| Réglages | Paramètres (inchangé, + réinitialisation du tri) |

Icône à choisir en maquette (ex. un pictogramme de tri/pile de cartes cohérent avec le style d'icônes déjà utilisé par `NeoNavItem`).

## 7. Architecture

Respect de l'architecture existante (domaine pur Kotlin, ViewModels → use cases uniquement).

- **domain/**
  - Modèle : `TriageDecision`.
  - Interface : `TriageRepository` (lecture/écriture de `triage_state`, par clé).
  - Use cases (nouveaux, `TriageUseCases.kt`) :
    - `ObserveTriagePileUseCase` — combine `ObserveTimelineUseCase` et `TriageRepository` pour exposer la pile filtrée, triée par jour puis par instant.
    - `RecordTriageDecisionUseCase(item, decision)` — écrit la ligne `triage_state` (avec date de snooze calculée pour `SNOOZED`).
    - `UndoLastTriageDecisionUseCase` — supprime la dernière ligne écrite (clé mémorisée en mémoire côté ViewModel, pas persistée).
    - `GetTriageSummaryUseCase` — compteurs pour l'écran de bilan.
    - `ResetTriageUseCase` — purge `KEPT`/`SNOOZED` (Réglages).
    - Suppression : **aucun nouveau use case d'exécution**, réutilisation de `DeleteGalleryItemsUseCase` existant (§4) ; un petit adaptateur convertit les clés `QUEUED_FOR_DELETION` en `List<GalleryItem>` via la chronologie courante.
  - Logique pure testée : filtrage de la pile (décision présente/absente, snooze expiré/à venir), tri par jour/instant, calcul de la date de snooze.
- **data/** : `TriageStateDao`, `TriageStateEntity`, `TriageRepositoryImpl` ; extension de la migration de clé favori existante (B9) pour couvrir aussi `triage_state`.
- **presentation/** : `triage/` (écran de tri, carte, gestes, bilan), `triageconfirm/` (écran de confirmation des suppressions, sur le modèle de `deleteconfirm/` existant mais avec sa propre logique de catégorisation §4).

**Base Room.** Nouvelle version avec `Migration` explicite (jamais `fallbackToDestructiveMigration`), schéma exporté et commité dans `app/schemas/`.

## 8. Tests

- Unitaires (domaine) : filtrage de la pile (élément sans décision, `KEPT`, `SNOOZED` expiré/non expiré, `QUEUED_FOR_DELETION`), tri par jour/instant, migration de clé favori étendue à `triage_state`, calcul de snooze, catégorisation téléphone/SSD pour la confirmation (y compris cas `BOTH` → `EVERYWHERE`).
- Pas de tests UI/instrumentation ; validation manuelle sur le Pixel : geste de swipe, lecture vidéo inline puis pause en changeant de carte, undo, reprise après fermeture de l'écran, confirmation avec SSD débranché (doit se comporter comme B7 : `SsdUnavailable`).

---

## Découpage en lots

| Lot | Contenu | Dépend de |
|---|---|---|
| 1 | Modèle `TriageDecision`, table `triage_state`, migration Room, `ObserveTriagePileUseCase` (logique pure testée) | Galerie v2 (B10) |
| 2 | Écran de tri : carte photo, gestes/boutons garder/supprimer/plus tard, undo | 1 |
| 3 | Vignette vidéo + lecture inline sur appui | 2 |
| 4 | Onglet **Trier** dans la navigation | 2 |
| 5 | Écran de bilan de session | 2 |
| 6 | Écran de confirmation des suppressions (réutilisation de `DeleteGalleryItemsUseCase`) | 1, 5 |
| 7 | Réinitialisation du tri dans Réglages | 1 |

## Points ouverts

- Mappage exact des directions de swipe (gauche/droite/haut) : à valider en maquette, potentiellement configurable si l'utilisateur le demande après usage.
- Durée du snooze fixée à 7 jours pour la v1 : à ajuster (voire rendre configurable) selon l'usage réel.
- Icône de l'onglet **Trier** à choisir.
- Faut-il un indicateur (badge sur l'onglet, compteur) signalant qu'une file de suppression est en attente de validation depuis une session précédente ? Non traité ici, ajoutable sans impact sur le modèle de données.
