# 0002 — Une seule branche permanente, versions par tags d'app

- Statut : Accepté
- Date : 2026-09-22

## Contexte

Le dépôt de la Galerie suivait un modèle `main` (stable) + `develop` (travail). Avec le monorepo
(ADR 0001), plusieurs apps cohabitent et sont publiées **indépendamment** sur le Play Store.

Le modèle envisagé au départ, une branche stable + une branche `develop` + une branche par version
taguée, pose deux problèmes dans ce contexte :

- **La stabilité se juge par app, pas par dépôt.** Fusionner `develop` dans la branche stable
  embarque toutes les apps à la fois : impossible de livrer la Galerie sans embarquer un Clavier à
  moitié fait.
- **Une branche par version fait doublon avec les tags.** Un tag désigne déjà, de façon immuable,
  le commit d'une version. Des branches par version (4 apps × N versions) ne servent que pour
  corriger une ancienne version, ce qui reste rare.

Le développeur est seul : chaque branche permanente supplémentaire est du travail de fusion sans
bénéfice, alors que la CI garantit déjà que la branche principale reste saine.

## Décision

1. **`main` est la seule branche permanente.** Elle est toujours buildable et testée. On n'y
   commite jamais directement : tout arrive par une branche courte fusionnée par PR (squash).
2. **Branches de travail courtes**, nommées `<type>/<app>-<sujet>` (ex. `feat/galerie-albums`),
   supprimées après fusion.
3. **Une version = un tag annoté `<app>-v<semver>`** (ex. `galerie-v1.2.0`) sur le commit de
   `main` qui fixe le numéro de version. L'AAB publié est construit depuis ce tag.
4. **Branches de maintenance à la demande** : `release/<app>-<majeur>.<mineur>`, créée depuis le
   tag uniquement pour corriger une version publiée alors que `main` contient déjà des changements
   non publiables. Le correctif est d'abord fait sur `main`, puis reporté par `cherry-pick -x`.
5. **La branche `develop` est supprimée.**
6. Les règles sont appliquées automatiquement : hooks Git locaux (`.githooks/`), vérification du
   titre des PR en CI, protection des branches sur GitHub.

Les règles détaillées et les procédures sont dans [`docs/workflow-git.md`](../workflow-git.md).

## Conséquences

- Un seul flux à suivre ; l'historique de `main` se lit comme un journal : un commit par
  fonctionnalité ou correctif (merge en squash).
- Du travail inachevé peut arriver sur `main` tant qu'il compile et que les tests passent : rien
  n'est publié sans tag. Une fonctionnalité non prête d'une app **déjà publiée** doit être masquée
  (drapeau `BuildConfig`) ou rester sur sa branche.
- Corriger une ancienne version coûte un cherry-pick, ce qui est acceptable vu la rareté du cas.
- Les commits antérieurs à cette décision (historique de la Galerie) ne suivent pas le format avec
  portée (`feat(galerie): …`) : ils ne sont pas réécrits.
