# Politique de confidentialité — C!ao Clavier

Dernière mise à jour : 22 septembre 2026.

## Résumé

C!ao Clavier est une application 100 % locale. Elle ne dispose d'**aucun backend, aucun serveur,
aucun compte utilisateur, et aucune connexion réseau** : la permission `INTERNET` est absente du
manifeste et une vérification automatique au build fait échouer toute tentative de l'ajouter (voir
le code source, publié sous licence MIT dans ce dépôt). Aucune frappe, aucune donnée du
presse-papiers, aucun mot appris ne quitte l'appareil.

## Données auxquelles l'application accède, et pourquoi

Tout ce que le clavier traite reste **exclusivement sur l'appareil de l'utilisateur**.

| Donnée | Usage |
|---|---|
| Texte tapé | Affiché dans le champ actif via l'API standard des claviers Android (`InputConnection`) ; jamais journalisé ni transmis. |
| Mots appris, dictionnaire personnel | Stockés localement pour proposer des suggestions ; consultables et effaçables depuis les réglages (à partir du lot 5). |
| Historique du presse-papiers | Stocké localement, durée limitée, contenus sensibles exclus (à partir du lot 7). |
| Champs mot de passe, navigation privée | Aucune suggestion, aucune autocorrection, aucun apprentissage, aucun enregistrement (à partir du lot 5). |

## Ce que l'application ne fait pas

- Pas de permission réseau, pas de télémétrie, pas d'analytics, pas de rapport de plantage tiers.
- Pas de compte utilisateur, pas d'authentification.
- Pas de synchronisation cloud, pas de sauvegarde à distance.
- Pas de publicité.
- Pas de partage de données avec des tiers.

## Suppression de données

Les données persistées (préférences, dictionnaire personnel, historique du presse-papiers) sont
stockées localement sur l'appareil et supprimées automatiquement en désinstallant l'application ;
un effacement manuel depuis les réglages sera aussi proposé (lots 5 et 7).

## Contact

Pour toute question relative à cette politique de confidentialité : **ybdn@pm.me**

## Code source

Le code source complet de l'application est public et consultable à l'adresse
[https://github.com/ybdn/ciao-suite](https://github.com/ybdn/ciao-suite), sous licence MIT.
