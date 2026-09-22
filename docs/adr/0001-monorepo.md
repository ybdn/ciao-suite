# 0001 — Monorepo Gradle unique avec plugins de convention

- Statut : Accepté
- Date : 2026-09-22

## Contexte

C!ao devient une suite de plusieurs apps Android (Galerie, puis Clavier, Messages, Téléphone),
développées par une seule personne. Ces apps partagent la même stack (Kotlin, Jetpack Compose),
la même identité visuelle et les mêmes principes (`docs/vision.md`), mais sont publiées
séparément sur le Play Store, chacune avec sa propre version.

Deux options ont été étudiées :

- **Plusieurs dépôts**, un par app : le code partagé (design system, utilitaires) doit alors être
  publié comme bibliothèque versionnée, les versions de dépendances et la CI sont dupliquées et
  divergent, et un changement transverse demande une PR par dépôt.
- **Un seul dépôt** : code partagé en modules Gradle, versions centralisées, un changement
  transverse en un commit, CI et conventions définies une fois.

Le seul vrai avantage du multi-dépôts (séparer les équipes et les droits d'accès) est sans objet
pour un développeur seul.

## Décision

1. Un seul dépôt `ciao-suite`, un seul build Gradle.
2. Une app = un module `apps/<app>` (`:apps:<app>`), avec son `applicationId`, son `versionCode`,
   sa fiche Play Store, ses specs (`docs/`), son `PRIVACY.md` et son `CLAUDE.md`.
3. Le code partagé vit dans `core/<module>`, créé **seulement quand une deuxième app en a besoin**.
   Dépendances autorisées : `apps/*` → `core/*`. Interdites : `core/*` → `apps/*`, app → app.
4. La configuration commune des modules est centralisée dans des plugins de convention
   (`build-logic/`, build inclus) ; les versions dans `gradle/libs.versions.toml`.
5. Chaque app garde un cycle de vie indépendant : tags `<app>-v<semver>`, un workflow CI par app
   filtré par chemins, qui appelle un workflow réutilisable commun.
6. L'historique Git de l'app existante (dépôt `ybdn/ciao-galery`) est conservé et réécrit sous
   `apps/galerie/`.

## Conséquences

- Ajouter une app coûte un `build.gradle.kts` d'une vingtaine de lignes, une ligne dans
  `settings.gradle.kts` et un workflow CI copié.
- Une modification de `core/` ou de `build-logic/` déclenche la CI de toutes les apps : c'est
  voulu (on voit immédiatement ce qu'elle casse).
- Toutes les apps avancent au même rythme de dépendances (AGP, Kotlin, Compose) : une montée de
  version doit être validée sur toutes les apps à la fois.
- Les commits antérieurs à la migration référencent des chemins à la racine (`app/…`) ; la
  commande `git log --follow` permet de suivre un fichier à travers le déplacement.
- L'ancien dépôt `ybdn/ciao-galery` est à archiver sur GitHub, avec un lien vers le nouveau.
