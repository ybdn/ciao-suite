# Workflow Git de la suite C!ao

Règles et procédures Git du monorepo. La décision et ses raisons sont dans
[l'ADR 0002](adr/0002-branches-et-versions.md). Ces règles sont **obligatoires** ; celles marquées
🔒 sont vérifiées automatiquement.

## En bref

```
main ──●──●──●──────●──●──●──────●──●──→   seule branche permanente, CI toujours verte
        \  /  │      \  /  │
 feat/galerie-albums  │   fix/clavier-accents        branches courtes, fusion par PR (squash)
                      │
               tag galerie-v1.2.0                     une version = un tag sur main
                      │
                      └──● release/galerie-1.2        créée seulement pour un correctif
                         │                            d'une version déjà publiée
                   tag galerie-v1.2.1
```

## 1. Branches

| Branche | Rôle | Durée de vie |
|---|---|---|
| `main` | Référence. Toujours buildable, tests verts. | Permanente |
| `<type>/<app>-<sujet>` | Travail en cours (une fonctionnalité, un correctif…) | Quelques jours, supprimée après fusion |
| `release/<app>-<majeur>.<mineur>` | Maintenance d'une version publiée | Tant que la version est maintenue |

- 🔒 **Aucun commit direct sur `main` ni sur `release/*`.** Tout passe par une branche de travail
  et une PR.
- 🔒 **Nom des branches de travail** : `<type>/<sujet>` en minuscules, chiffres, `-`, `.` ou `_`.
  Mettre l'app en tête du sujet quand le travail ne concerne qu'une app :
  `feat/galerie-albums`, `fix/clavier-accents`, `build/agp-8-8`, `docs/workflow-git`.
- Types de branche : les mêmes que les types de commit (voir ci-dessous).
- Une branche = un sujet. Une branche qui dure plus d'une semaine est à découper.
- Avant la PR, se mettre à jour par **rebase** sur `main` (`git rebase main`), pas par merge.

## 2. Commits

🔒 Format ([Conventional Commits](https://www.conventionalcommits.org/fr/)), en français :

```
<type>(<portée>): <description à l'infinitif ou nominale, sans point final>

<corps facultatif : le pourquoi, pas le comment>
```

| Type | Usage |
|---|---|
| `feat` | Nouvelle fonctionnalité visible par l'utilisateur |
| `fix` | Correction de bug |
| `refactor` | Restructuration sans changement de comportement |
| `perf` | Amélioration de performance |
| `test` | Ajout ou correction de tests |
| `docs` | Documentation uniquement |
| `style` | Mise en forme, sans changement de code |
| `build` | Gradle, dépendances, `build-logic/` |
| `ci` | Workflows GitHub Actions |
| `chore` | Maintenance (versions, fichiers de config…) |
| `revert` | Annulation d'un commit précédent |

- **Portée** = l'app ou le module touché : `galerie`, `clavier`, `messages`, `telephone`,
  `designsystem`, `build-logic`… Elle est omise quand le changement est transverse
  (`docs: …`, `build: …`).
- Changement cassant (migration de données non rétrocompatible, suppression d'une fonction) :
  `feat(galerie)!: …` et un paragraphe `BREAKING CHANGE: …` dans le corps.
- 🔒 Première ligne de 100 caractères maximum (viser 72).
- Exemples : `feat(galerie): albums personnalisés`, `fix(clavier): accents sur les majuscules`,
  `chore(galerie): version 1.2.0`, `build: AGP 8.8`.

## 3. Fusion (pull requests)

- Une PR par branche, vers `main` (ou vers `release/*` pour un correctif de maintenance).
- 🔒 **Titre de la PR au format de commit** : c'est lui qui devient le message du commit sur `main`
  (fusion en **squash**). Vérifié par la CI (`pr-title.yml`).
- 🔒 La CI doit être verte avant la fusion (check **CI OK**, qui couvre les apps touchées).
- Fusion en **squash** uniquement ; la branche est supprimée automatiquement après fusion.

**Tant que le dépôt n'est pas sur GitHub** (pas de PR possible), fusionner en local par
avance rapide, après avoir rebasé la branche et vérifié build et tests :

```bash
git switch <branche> && git rebase main
./gradlew :apps:<app>:assembleDebug :apps:<app>:testDebugUnitTest
git switch main && git merge --ff-only <branche> && git branch -d <branche>
```

## 4. Versions

Chaque app a sa propre version, indépendante des autres.

- **`versionName`** = [SemVer](https://semver.org/lang/fr/) `MAJEUR.MINEUR.CORRECTIF` :
  - `MAJEUR` : rupture pour l'utilisateur (refonte, données non récupérables par l'ancienne version) ;
  - `MINEUR` : nouvelle fonctionnalité ;
  - `CORRECTIF` : correction de bug uniquement.
  - Avant la première publication, les apps restent en `0.x.y`. La première version publiée sur le
    Play Store est la `1.0.0`.
- **`versionCode`** = entier incrémenté de 1 à **chaque** version taguée de l'app, correctifs de
  maintenance compris (le Play Store exige qu'il augmente). Le prochain numéro libre est
  `versionCode` du dernier tag de l'app + 1.
- 🔒 **Tag** : annoté, `<app>-v<versionName>`, ex. `galerie-v1.2.0`. Il n'est **jamais** déplacé
  ni supprimé une fois poussé.
- **Journal des versions** : `apps/<app>/CHANGELOG.md`. La section `Non publié` se remplit au fil
  des fusions (une ligne par changement visible) ; elle sert de base aux « Nouveautés » du Play Store.

## 5. Procédures

### Publier une version d'une app

```bash
# 1. Branche de préparation
git switch main && git pull
git switch -c chore/galerie-v1.2.0
#    - apps/galerie/build.gradle.kts : versionName = "1.2.0", versionCode = <dernier + 1>
#    - apps/galerie/CHANGELOG.md : « Non publié » devient « 1.2.0 — AAAA-MM-JJ »
git commit -am "chore(galerie): version 1.2.0"
#    → PR « chore(galerie): version 1.2.0 », CI verte, fusion en squash

# 2. Tag sur le commit fusionné dans main
git switch main && git pull
git tag -a galerie-v1.2.0 -m "C!ao Galerie 1.2.0"
git push origin galerie-v1.2.0
#    → déclenche .github/workflows/release.yml : vérifie que le tag est sur main ou release/*
#      et que versionName / versionCode sont cohérents, lance les tests, build l'APK release
#      signé avec la clé de la suite, puis publie une GitHub Release « galerie-v1.2.0 » avec
#      l'APK, sa somme SHA-256, une attestation de provenance et les notes tirées de la section
#      du CHANGELOG. Échoue si les secrets de signature manquent. Générique à toute la suite,
#      rien à adapter pour une nouvelle app.

# 3. Build de l'AAB depuis le tag, puis envoi sur la Play Console
git switch --detach galerie-v1.2.0
./gradlew :apps:galerie:bundleRelease
git switch main
```

### Corriger une version publiée

**Cas simple** (`main` ne contient rien de non publiable pour cette app depuis le tag) :
correctif sur une branche `fix/…`, fusion dans `main`, puis nouvelle version `1.2.1` par la
procédure ci-dessus.

**Cas maintenance** (`main` a déjà avancé avec des changements non publiables pour cette app) :

```bash
# 1. Corriger d'abord sur main (branche fix/…, PR, fusion) : le correctif ne doit jamais se perdre.

# 2. Créer la branche de maintenance depuis le tag, si elle n'existe pas encore
git switch -c release/galerie-1.2 galerie-v1.2.0
git push -u origin release/galerie-1.2

# 3. Reporter le correctif sur une branche dédiée, avec la montée de version
git switch -c fix/galerie-1.2-plantage-export release/galerie-1.2
git cherry-pick -x <sha-du-correctif-sur-main>
#    versionName = "1.2.1", versionCode = <dernier + 1>, CHANGELOG
git commit -am "chore(galerie): version 1.2.1"
#    → PR vers release/galerie-1.2, fusion

# 4. Tag sur la branche de maintenance, puis build comme pour une version normale
git switch release/galerie-1.2 && git pull
git tag -a galerie-v1.2.1 -m "C!ao Galerie 1.2.1"
git push origin galerie-v1.2.1
```

Reporter aussi la ligne du `CHANGELOG` et le `versionCode` consommé sur `main`, pour que la
version suivante parte du bon numéro.

### Travail inachevé

Il peut être fusionné dans `main` s'il compile et que les tests passent, puisque rien n'est publié
sans tag. Si l'app est **déjà publiée**, la fonction non prête doit être masquée
(drapeau `BuildConfig`, entrée de navigation non branchée) ou rester sur sa branche.

## 6. Mise en place

### Hooks Git locaux (à faire une fois par clone)

```bash
git config core.hooksPath .githooks
```

- `.githooks/pre-commit` : refuse les commits sur `main` et `release/*`, et les noms de branche
  hors convention.
- `.githooks/commit-msg` : refuse les messages hors format.

En cas de besoin réel et exceptionnel (réparation d'urgence), `git commit --no-verify` contourne
les hooks. À justifier dans le message du commit.

### Réglages GitHub (à la création du dépôt)

- Branche par défaut : `main`.
- Règles de protection (*Rulesets*) sur `main` et `release/*` :
  PR obligatoire, checks obligatoires **CI OK** et **Titre de PR**, historique linéaire,
  pas de force-push, pas de suppression.
- Protection des tags `*-v*` : ni suppression ni mise à jour.
- Environnement `release` (Settings > Environments) : déploiement limité aux tags `*-v*`
  (*Deployment branches and tags*), et secrets de signature `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` rattachés à cet
  environnement plutôt qu'au dépôt, pour qu'une branche ne puisse pas les lire.
- Fusion : **squash uniquement** (désactiver merge commit et rebase merge), message du squash =
  titre de la PR ; suppression automatique des branches après fusion.
