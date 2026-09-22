# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Place dans la suite

Cette app fait partie du monorepo **C!ao** : les règles communes (environnement de build, structure,
conventions Git, CI, plugins de convention `build-logic/`) sont dans le `CLAUDE.md` à la racine de
la suite — ce fichier ne contient que ce qui est propre au clavier.

**Spécification de référence : [`docs/spec-v1.md`](docs/spec-v1.md).** À lire avant toute
modification : objectif, principes non négociables (§3, notamment l'absence de permission
`INTERNET`), architecture, design néo-brutaliste (§5), règles de frappe françaises, suggestions,
emojis, presse-papiers, plan de réalisation en 9 lots (§12).

Commandes (depuis la racine de la suite) :

```bash
./gradlew :apps:clavier:assembleDebug        # build debug
./gradlew :apps:clavier:testDebugUnitTest    # tests unitaires
```

## Suivi de la réalisation

Jalon GitHub **« C!ao Clavier v1 »**, une issue par lot de la spec (§12).

- Lot 0 (`core/designsystem`, garde-fou `INTERNET`) : fait.
- Lot 1 (squelette) : fait. Module `apps/clavier`, `InputMethodService` + Compose, app de
  réglages avec la mise en route.
- Lot 2 (frappe AZERTY, issue #11) : en cours, par incréments successifs sur la même issue.
  Incrément 1 fait : page lettres (AZERTY, majuscule simple/verrouillage, retour arrière avec
  répétition, touche Entrée adaptée à `imeOptions`, vibration). Restent : accents par appui long,
  pages symboles, claviers spécialisés (numérique/téléphone/e-mail/URL), curseur sur la barre
  d'espace, aperçu de touche.
- Lots 3 à 8 : pas commencés.

## Architecture

Même découpage que la Galerie (Clean Architecture allégée, séparation par packages), plus
`core/designsystem` en dépendance (thème, palette, composants `Neo*`) :

```
dev.ybdn.ciao.clavier/
├── domain/
│   ├── layout/    modèle des touches et de la disposition AZERTY (Kotlin pur, testé)
│   └── input/     état de la touche Majuscule (Kotlin pur, testé)
├── ime/           InputMethodService, interface Compose du clavier
└── settings/      activité de réglages et de mise en route (Compose)
```

Le package `data/` (dictionnaire, presse-papiers, préférences) sera ajouté à partir du lot 4/5/7,
pas avant : pas de package vide « au cas où ».

- **Compose dans l'`InputMethodService`** : le service n'est pas un `LifecycleOwner` /
  `ViewModelStoreOwner` / `SavedStateRegistryOwner` par défaut (contrairement à `ComponentActivity`) ;
  `ClavierInputMethodService` les implémente à la main pour que sa `ComposeView` fonctionne
  (`setViewTreeLifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`).
- **Aucune dépendance réseau** : vérifié automatiquement au build par la tâche
  `checkNoInternetPermission` (`build-logic`, lot 0).
- **`NeoKey`** (`core/designsystem`) : touche neo-brutaliste (bordure fine, petite ombre, appui
  instantané/retour animé), avec appui long et double-appui. Le retour arrière n'utilise pas
  `NeoKey` : sa répétition à l'appui maintenu a besoin d'un geste (`pointerInput`/`detectTapGestures`)
  que `combinedClickable` ne permet pas d'observer en continu.
- Testé sur émulateur/Pixel : voir « Tests sur appareil » dans le `CLAUDE.md` racine — un clavier
  doit en plus être activé (réglages système) puis sélectionné (`showInputMethodPicker`) avant de
  pouvoir taper avec.

## Conventions

- Code (classes, fonctions, variables) en anglais ; UI et messages utilisateur en français.
- `applicationId`/`namespace` : `dev.ybdn.ciao.clavier` (pas d'historique à préserver, contrairement
  à la Galerie).
- Commits : `<type>(clavier): <description>`, voir `docs/workflow-git.md` à la racine.
