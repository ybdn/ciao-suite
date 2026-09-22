# Politique de confidentialité — C!ao Galerie

Dernière mise à jour : 22 septembre 2026.

## Résumé

C!ao Galerie est une application 100 % locale. Elle ne dispose d'**aucun backend, aucun serveur, aucun
compte utilisateur, et aucune connexion réseau** (aucune dépendance réseau n'est présente dans
l'application — voir le code source, publié sous licence MIT dans ce dépôt). Aucune donnée n'est
collectée, transmise, vendue ou partagée avec qui que ce soit, y compris le développeur.

## Données auxquelles l'application accède, et pourquoi

Toutes ces données restent **exclusivement sur l'appareil de l'utilisateur et sur le SSD externe
qu'il branche lui-même** ; rien n'en sort jamais vers un service tiers.

| Permission | Usage |
|---|---|
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` | Lire les photos/vidéos du téléphone pour les scanner et les copier vers le SSD. |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Permettre à l'utilisateur de limiter l'accès à une sélection de médias plutôt qu'à toute la photothèque. |
| `ACCESS_MEDIA_LOCATION` | Conserver les métadonnées GPS EXIF d'origine lors de la copie, pour une copie fidèle à l'original. |
| Accès au dossier du SSD (Storage Access Framework) | Écrire les copies de médias dans l'arborescence `DCIM/année/mois/jour` du SSD choisi par l'utilisateur. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Faire tourner le transfert en arrière-plan avec une notification de progression, sans être interrompu par le système. |
| `POST_NOTIFICATIONS` | Afficher la notification de progression du transfert. |
| `WAKE_LOCK` | Empêcher l'appareil de s'endormir pendant un transfert long. |
| `android.hardware.usb.host` (fonctionnalité requise) | L'application nécessite un port USB-C compatible OTG pour brancher un SSD externe. |

## Ce que l'application ne fait pas

- Pas de télémétrie, pas d'analytics, pas de crash reporting tiers (aucune dépendance de ce type
  n'est intégrée à l'application).
- Pas de compte utilisateur, pas d'authentification.
- Pas de synchronisation cloud, pas de sauvegarde à distance.
- Pas de publicité.
- Pas de partage de données avec des tiers.

## Suppression de données

Les seules données persistées par l'application (état de transfert, préférences, favoris/tri)
sont stockées localement sur l'appareil (base Room et DataStore internes à l'application) et
supprimées automatiquement en désinstallant l'application.

## Contact

Pour toute question relative à cette politique de confidentialité :
**ybdn@pm.me**

## Code source

Le code source complet de l'application est public et consultable à l'adresse
[https://github.com/ybdn/ciao-galery](https://github.com/ybdn/ciao-galery), sous licence MIT.
