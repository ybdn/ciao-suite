# Aide-mémoire — formulaire "Sécurité des données" (Play Console)

Brouillon pour remplir le formulaire Data Safety officiel sur la Play Console. Ce fichier n'est
pas la source de vérité — la soumission se fait manuellement sur la console.

## Collecte et partage de données

**Réponse générale : l'application ne collecte ni ne partage aucune donnée utilisateur.**
Aucune dépendance réseau n'existe dans le code (voir `app/build.gradle.kts` : aucune bibliothèque
réseau/backend/analytics). Toutes les données restent sur l'appareil et sur le SSD externe choisi
par l'utilisateur.

## Types de données accédées localement (non collectées, non transmises)

| Catégorie Play Console | Donnée accédée | Transmise à un tiers ? | Pourquoi |
|---|---|---|---|
| Photos et vidéos | Photos et vidéos du stockage local | Non | Fonction principale : copier vers le SSD choisi par l'utilisateur |
| Localisation | Position GPS EXIF embarquée dans les photos | Non | Conservée telle quelle dans la copie, jamais lue/utilisée par l'app pour autre chose |
| Fichiers et documents | Accès au dossier du SSD (SAF) | Non | Écriture de l'arborescence de rangement |

## Sécurité

- Chiffrement en transit : sans objet (aucune transmission réseau).
- Suppression des données : automatique à la désinstallation de l'application (Room + DataStore
  locaux, aucune donnée hébergée ailleurs).
- Révision indépendante de la sécurité : non applicable (pas de traitement de données côté
  serveur).

## Permissions sensibles déclarées dans le manifest

`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`,
`ACCESS_MEDIA_LOCATION` — voir le détail des justifications dans [`PRIVACY.md`](../../PRIVACY.md).
