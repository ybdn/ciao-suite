# Vision de la suite C!ao

C!ao (« ciao », au revoir) est une suite d'applications Android pour **dire au revoir aux GAFAM** :
remplacer, une par une, les applications du quotidien fournies par les grandes plateformes par des
applications qui respectent leurs utilisateurs.

L'idée est née de la première app, **C!ao Galerie**, qui permet de garder ses photos sans cloud.

## Principes (non négociables)

Chaque app de la suite doit les respecter. S'en écarter demande un ADR (`docs/adr/`) qui justifie
l'exception.

1. **Pas de compte.** L'app fonctionne dès l'installation, sans inscription ni identifiant.
2. **Pas de cloud ni de backend.** Les données restent sur le téléphone, ou sur un support que
   l'utilisateur branche lui-même. Aucune synchronisation vers un serveur.
3. **Pas de pistage.** Ni analytics, ni publicité, ni télémétrie, ni SDK tiers qui en embarque.
   Aucune dépendance réseau sans nécessité fonctionnelle démontrée.
4. **Gratuit et open source.** Licence MIT, code public.
5. **Local et maîtrisé.** Aucune action destructive (suppression, envoi) sans action explicite de
   l'utilisateur ; les données sont dans des formats ouverts et récupérables.
6. **Une identité commune.** Une même charte visuelle et une même façon de faire dans toutes les
   apps, pour qu'on reconnaisse une app C!ao.

## Périmètre

- **Plateforme** : Android d'abord (Kotlin, Jetpack Compose). iOS plus tard.
- **Distribution** : Google Play Store, gratuitement, une fiche par app. Publication une fois
  toutes les apps développées.
- **Langue** : interface en français.

## Apps prévues

| App | Remplace | Particularité technique |
|---|---|---|
| C!ao Galerie | Google Photos | Délestage vers un SSD en USB-C, édition, tri |
| C!ao Clavier | Gboard | Service de méthode de saisie (IME) |
| C!ao Messages | Google Messages | App SMS par défaut (rôle `ROLE_SMS`) |
| C!ao Téléphone | Téléphone Google | App d'appel par défaut (rôle `ROLE_DIALER`) |

Messages et Téléphone demandent des permissions sensibles (SMS, journal d'appels) que Google Play
n'accorde qu'aux apps ayant le rôle par défaut correspondant, sur déclaration : à prévoir dès la
conception de ces apps.
