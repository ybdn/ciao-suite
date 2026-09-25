# Fiche Play Store — C!ao Clavier

Brouillon de la fiche pour la Play Console (spec-v1.md §10). L'app n'est pas encore publiée
(voir le `CLAUDE.md` racine) : ce texte sert de base le jour où toutes les apps de la suite sont
prêtes. À adapter aux gabarits exacts de la Play Console au moment de la publication (longueurs
en caractères, captures d'écran requises pour chaque taille d'appareil, etc.).

## Titre

C!ao Clavier

## Description courte (80 caractères max)

Clavier français, sans compte ni connexion. Rien ne quitte votre téléphone.

## Description longue

C!ao Clavier est un clavier français pour Android, pensé pour la vie privée : **aucun compte,
aucun serveur, aucune connexion Internet**. Aucune frappe, aucune suggestion, aucune donnée du
presse-papiers ne quitte jamais votre appareil — l'app ne demande même pas la permission d'accéder
à Internet.

**Frappe soignée en français**
- Disposition AZERTY complète, accents accessibles par appui long (é è ê ë, à â, ç, ù û, etc.).
- Majuscule automatique, double espace pour un point, espace insécable avant « ; : ! ? ».
- Vibration ou son à la frappe, aperçu de la touche : tout est réglable.

**Suggestions qui comprennent le français**
- Complétion, correction des fautes de frappe, restitution des accents (« ecole » → « école »).
- Correction automatique annulable d'un simple retour arrière.
- Dictionnaire personnel : les mots que vous gardez sont appris, jamais transmis.

**Emojis et presse-papiers**
- Panneau emojis complet, couleurs de peau, emojis récents.
- Historique du presse-papiers local, avec épinglage et durée de conservation réglable.

**Vie privée par construction**
- Pas de compte, pas de cloud, pas de télémétrie, pas de publicité.
- Rien n'est retenu dans un champ mot de passe ni en navigation privée.
- Code source ouvert (licence MIT) : voir le lien ci-dessous.

**Accessibilité**
- Compatible avec TalkBack (chaque touche est décrite à voix haute).
- Contrastes vérifiés en thème clair et sombre.
- Thème clair, sombre ou automatique (suit le système) ; hauteur du clavier réglable.

## Catégorie

Outils (Tools)

## Mots-clés

clavier français, clavier azerty, clavier sans internet, vie privée, clavier open source, clavier
accessible

## Coordonnées et liens

- Code source : https://github.com/ybdn/ciao-suite
- Politique de confidentialité : `apps/clavier/PRIVACY.md` (publiée avec l'app, lien à ajouter à
  la fiche une fois le dépôt public et l'app publiée).
- Support : à définir avant publication (issue GitHub ou adresse dédiée).

## Captures d'écran et visuels

À produire à la publication, sur le Pixel 10 Pro et un émulateur de référence (thèmes clair et
sombre) : page lettres, page symboles, panneau emojis, historique du presse-papiers, écran de
réglages. Icône déjà en place (`mipmap/ic_launcher`).

## Classification du contenu

Tous publics ; aucune fonctionnalité réseau, aucun contenu généré par des tiers, aucun achat
intégré.
