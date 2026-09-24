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
- Lot 2 (frappe AZERTY, issue #11) : fait, sauf le réglage de l'aperçu de touche (désactivable)
  et des icônes de touche Entrée par action, reportés au lot 8 avec l'écran de préférences.
- Lot 3 (règles françaises, issue #12) : fait.
- Lot 4 (dictionnaire et suggestions, issue #13) : fait. Source Leipzig Corpora Collection
  (CC BY 4.0), prédiction du mot suivant incluse (spec §7.2).
- Lot 6 (emojis, issue #15) : fait.
- Lot 7 (presse-papiers, issue #16) : fait.
- Lots 5, 8 : pas commencés.

## Architecture

Même découpage que la Galerie (Clean Architecture allégée, séparation par packages), plus
`core/designsystem` en dépendance (thème, palette, composants `Neo*`) :

```
dev.ybdn.ciao.clavier/
├── domain/
│   ├── layout/    touches, dispositions (AZERTY, symboles, pavés), variantes (Kotlin pur, testé)
│   ├── input/     majuscule, règles françaises, curseur, effacement par mot (Kotlin pur, testé)
│   ├── suggest/   dictionnaire (trie), moteur de suggestions, mot sous le curseur (Kotlin pur, testé)
│   ├── emoji/     lecture de emoji-test.txt, couleurs de peau, récents (Kotlin pur, testé)
│   └── clipboard/ règles de l'historique : durée, limite, épinglage, puce Coller (testé)
├── data/          DataStore partagé (réglages, emojis récents), catalogue emojis, dictionnaire,
│                  clipboard/ : base Room `clavier.db` (schémas dans apps/clavier/schemas)
├── ime/           InputMethodService, interface Compose du clavier
└── settings/      activité de réglages et de mise en route (Compose)
```

Le dictionnaire personnel (lot 5) rejoindra `data/`.

- **Compose dans l'`InputMethodService`** : le service n'est pas un `LifecycleOwner` /
  `ViewModelStoreOwner` / `SavedStateRegistryOwner` par défaut (contrairement à `ComponentActivity`) ;
  `ClavierInputMethodService` les implémente à la main pour que sa `ComposeView` fonctionne
  (`setViewTreeLifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`).
- **Aucune dépendance réseau** : vérifié automatiquement au build par la tâche
  `checkNoInternetPermission` (`build-logic`, lot 0).
- **Majuscule automatique** : c'est le champ qui décide, via `InputConnection.getCursorCapsMode`
  filtré par les drapeaux `TYPE_TEXT_FLAG_CAP_*` qu'il demande — le clavier ne relit jamais le
  texte saisi. L'état est réévalué dans `onUpdateSelection`. `ShiftState` mémorise le choix de
  l'utilisateur, `EffectiveShift` combine ce choix et la proposition du champ.
- **Zone système sous le clavier** : Android y dessine sa propre rangée (masquer le clavier,
  changer de clavier). L'encart de barre de navigation ne la couvre pas toujours, d'où la
  hauteur plancher `SystemKeyboardRowHeight`.
- **Gestes des touches** (`ime/KeyboardKey.kt`) : chaque touche est une `NeoKeyFace`
  (`core/designsystem`, apparence seule) dans un conteneur fixe qui suit le doigt
  (`awaitEachGesture`) de l'appui au relâcher : appui long à 300 ms, choix des variantes en
  glissant, curseur sur l'espace, répétition du retour arrière. `NeoKey` (`combinedClickable`) ne
  permet pas de suivre le glissement. Le texte est inséré **au relâcher**, pour pouvoir choisir
  une variante. Une action TalkBack (`semantics { onClick }`) double chaque touche.
- **Aperçu et variantes** (`ime/KeyOverlay.kt`) : dessinés dans la vue du clavier, par-dessus le
  bandeau, pas dans une `Popup` (fenêtre supplémentaire depuis celle de l'IME). Seul `KeyOverlay`
  lit leur état : un appui ne recompose que la touche et la couche flottante (spec §5.4).
- **Règles françaises** (§6.3) : appliquées par le service dans `commitText`, qui relit quelques
  caractères avant le curseur et remplace le texte en un `beginBatchEdit`. Seulement dans un champ
  de texte ordinaire : jamais mot de passe (`isPasswordField`), e-mail ni URL. Double espace :
  les deux espaces doivent être tapées à moins d'une seconde d'écart, sans autre action entre.
- **Dictionnaire** (§7.2) : `apps/clavier/dictionary/fr_*.tsv` (versionnés, générés depuis les
  corpus Leipzig par la tâche manuelle `prepareFrenchWordList`, voir `dictionary/README.md`), compilés
  au build par `compileFrenchDictionary` (`CompileDictionaryTask`, `build-logic`, qui documente le
  format) en `assets/dictionary/fr.dict`, ajouté aux assets générés de chaque variant. Les tests
  unitaires tournent sur ce vrai fichier (propriété système `clavier.dictionary`) avec le jeu de cas
  versionné `src/test/resources/suggest/cas-francais.tsv` : **tout réglage du moteur ou de la
  liste doit garder ce jeu vert**, et un cas nouveau s'y ajoute.
- **Suggestions** (§7.1) : `SuggestionEngine` parcourt le trie avec une distance d'édition pondérée
  (`TypingCosts` : accents, touches voisines AZERTY, inversions, lettres doublées, ligatures),
  élagué par la fréquence maximale de chaque sous-arbre. Le mot après une élision est cherché seul
  (`WordAtCursor`), la casse suit le mot tapé (`matchCasing`). Le service relit le mot avant le
  curseur à chaque `onUpdateSelection` (pas de texte en composition), calcule sur un dispatcher à
  un fil et annule le calcul précédent. Seule `SuggestionStrip` lit la barre (une frappe ne
  recompose pas le clavier). Autocorrection dans `commitText` avant un séparateur, annulée par
  un retour arrière immédiat (`AutocorrectionUndo`) ; les mots rétablis vont dans `rejectedWords`
  jusqu'au champ suivant. Après un remplacement de même longueur, Android ne rappelle pas
  `onUpdateSelection` : rafraîchir la barre à la main.
- **Réglages** : `TypingPreferences` (DataStore `clavier_settings`), lus en continu par le
  service (`lifecycleScope`) : un changement s'applique sans redémarrer le clavier.
- **Emojis** : `assets/emoji/emoji-test.txt` d'Unicode (Emoji 18.0, licence Unicode v3 dans
  `LICENSE-Unicode.txt`), lu une fois par processus en arrière-plan, puis filtré sur l'appareil par
  `Paint.hasGlyph` : ce que la police du téléphone ne sait pas dessiner n'est pas proposé. Pour
  passer à une nouvelle version d'Emoji, remplacer le fichier. Récents et couleurs de peau :
  `EmojiPreferences`, jamais enregistrés quand `incognito` (navigation privée
  `IME_FLAG_NO_PERSONALIZED_LEARNING` ou mot de passe).
- **Presse-papiers** : le service écoute `ClipboardManager` (autorisé en arrière-plan parce qu'il
  est le clavier actif), ignore les copies `EXTRA_IS_SENSITIVE` et les non-textes, et range dans
  Room (un même texte recopié remonte au lieu d'être dupliqué). Une horloge interne (`clock`,
  chaque minute, et une minute après chaque copie) fait expirer les éléments et la puce « Coller ».
  Les épinglés passent en tête de liste. Schémas Room versionnés : jamais de migration
  destructive.
- **Sauvegardes** : `allowBackup="false"` et `data_extraction_rules.xml` (tout exclu, cloud et
  transfert d'appareil, que `allowBackup` seul ne bloque plus depuis Android 12).
- **Claviers spécialisés** : `keyboardModeFor(inputType)` choisit le `KeyboardMode` ; pavé
  téléphone avec pause (`,`) et attente (`;`) par appui long sur `*` et `#`, `+` aussi sur `0`.
- Testé sur émulateur/Pixel : voir « Tests sur appareil » dans le `CLAUDE.md` racine — un clavier
  doit en plus être activé (réglages système) puis sélectionné (`showInputMethodPicker`) avant de
  pouvoir taper avec. Après une réinstallation ou un `am force-stop`, Android revient sur Gboard :
  resélectionner avec `adb shell ime set dev.ybdn.ciao.clavier/.ime.ClavierInputMethodService`.

## Conventions

- Code (classes, fonctions, variables) en anglais ; UI et messages utilisateur en français.
- `applicationId`/`namespace` : `dev.ybdn.ciao.clavier` (pas d'historique à préserver, contrairement
  à la Galerie).
- Commits : `<type>(clavier): <description>`, voir `docs/workflow-git.md` à la racine.
