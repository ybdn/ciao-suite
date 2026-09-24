# Politique de confidentialité — C!ao Clavier

Dernière mise à jour : 24 septembre 2026.

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
| Mots appris, dictionnaire personnel | Un mot inconnu est retenu dès sa première utilisation (500 au plus, les plus anciens oubliés) et appris à la deuxième ; les mots choisis dans la barre de suggestions sont comptés. Stockés localement pour proposer des suggestions ; les mots appris sont consultables un par un, et tout s'efface depuis les réglages. |
| Historique du presse-papiers | Textes copiés (25 au plus), stockés localement 1 heure par défaut (24 heures ou 7 jours au choix), sauf ceux épinglés. Les copies marquées sensibles (gestionnaires de mots de passe) ne sont jamais enregistrées. Désactivable et effaçable depuis les réglages. |
| Emojis récents, couleurs de peau choisies | Stockés localement ; jamais enregistrés dans un champ mot de passe ni en navigation privée ; effaçables depuis les réglages. |
| Champs mot de passe, navigation privée | Aucune suggestion, aucune autocorrection, aucun apprentissage, aucun enregistrement. |

## Ce que l'application ne fait pas

- Pas de permission réseau, pas de télémétrie, pas d'analytics, pas de rapport de plantage tiers.
- Pas de compte utilisateur, pas d'authentification.
- Pas de synchronisation cloud, pas de sauvegarde à distance : les données du clavier sont exclues
  des sauvegardes Android et du transfert vers un nouvel appareil.
- Pas de publicité.
- Pas de partage de données avec des tiers.

## Suppression de données

Les données persistées (préférences, emojis récents, historique du presse-papiers, dictionnaire
personnel) sont stockées localement sur l'appareil et supprimées automatiquement en désinstallant
l'application. Les emojis récents, l'historique du presse-papiers et le dictionnaire personnel
s'effacent aussi depuis les réglages, section « Données » ; les mots appris se suppriment aussi un
par un depuis l'écran « Dictionnaire personnel ».

## Contact

Pour toute question relative à cette politique de confidentialité : **ybdn@pm.me**

## Code source

Le code source complet de l'application est public et consultable à l'adresse
[https://github.com/ybdn/ciao-suite](https://github.com/ybdn/ciao-suite), sous licence MIT.
