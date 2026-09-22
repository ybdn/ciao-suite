# C!ao Clavier — Spécification v1

- Statut : **Proposé** (à valider avant le lot 1)
- Date : 2026-09-22
- App : `apps/clavier`, `applicationId`/`namespace` `dev.ybdn.ciao.clavier`

Ce document est la spécification de référence de la v1. Les versions suivantes feront l'objet de
specs `spec-v2-….md`, etc., qui priment sur celle-ci en cas de contradiction (même convention que
la Galerie).

## 1. Objectif

Un clavier Android français qui remplace Gboard **sans rien envoyer nulle part** : aucune frappe,
aucun mot appris, aucun élément du presse-papiers ne quitte le téléphone. Il doit être assez bon
pour servir de clavier principal au quotidien, notamment grâce à des suggestions et une correction
qui gèrent correctement le français (accents, apostrophes, typographie).

## 2. Périmètre

### Dans la v1

| Domaine | Contenu |
|---|---|
| Frappe | Disposition AZERTY, majuscules et verrouillage, accents par appui long, chiffres et symboles, claviers adaptés au champ (numérique, téléphone, e-mail, URL) |
| Français | Majuscule automatique, double espace → point, apostrophe, restitution des accents par les suggestions |
| Suggestions | Barre de suggestions, complétion, correction des fautes de frappe, autocorrection annulable |
| Apprentissage | Mots personnels appris localement, gérables et effaçables |
| Emojis | Panneau par catégories, récents, variantes de couleur de peau |
| Presse-papiers | Historique local limité dans le temps, épinglage, exclusion des contenus sensibles |
| Réglages | App de configuration : activation du clavier, préférences, thème, dictionnaire personnel |

### Hors v1 (candidats pour la suite)

Saisie par glissement, saisie vocale hors ligne, autres langues et dispositions (anglais, QWERTY,
BÉPO), prédiction du mot suivant (sauf si les données du dictionnaire le permettent sans surcoût,
voir §7.2), recherche d'emojis, GIF et stickers (impossibles sans service en ligne), mode une main,
clavier flottant, traduction.

## 3. Principes non négociables

Déclinaison de `docs/vision.md` pour un clavier, qui voit **tout** ce que l'utilisateur tape :

1. **Aucune permission réseau.** Le manifeste fusionné ne doit pas contenir `INTERNET`. C'est
   vérifié automatiquement au build (voir §10) : c'est la garantie la plus forte qu'on puisse donner
   à un utilisateur de clavier.
2. **Aucune collecte.** Ni journalisation des frappes, ni statistiques, ni rapport de plantage
   envoyé. Les logs de debug ne contiennent jamais de texte saisi.
3. **Champs sensibles respectés.** Dans les champs mot de passe (`TYPE_TEXT_VARIATION_PASSWORD`,
   `…VISIBLE_PASSWORD`, `…WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`) et quand l'app demande la
   navigation privée (`IME_FLAG_NO_PERSONALIZED_LEARNING`) : pas de suggestions, pas
   d'autocorrection, pas d'apprentissage, pas d'enregistrement dans les récents.
4. **Données locales, effaçables.** Tout ce que le clavier mémorise (mots appris, emojis récents,
   presse-papiers) est consultable et effaçable depuis les réglages, en un geste.
5. **Sauvegarde exclue.** Le dictionnaire personnel et l'historique du presse-papiers sont exclus
   des sauvegardes Android (`dataExtractionRules`), qui peuvent partir dans le cloud de Google.

## 4. Architecture

Même découpage que la Galerie (Clean Architecture allégée, séparation par packages dans un seul
module d'app), plus le premier module partagé de la suite :

```
core/designsystem/                  ← extrait de la Galerie (lot 0)
apps/clavier/src/main/kotlin/dev/ybdn/ciao/clavier/
├── domain/        Kotlin pur, testé unitairement
│   ├── layout/        modèle des touches et dispositions (AZERTY, symboles, numérique…)
│   ├── input/         machine d'états du clavier (shift, verrouillage, pages), règles françaises
│   ├── suggest/       moteur de suggestions (complétion, correction, accents, apprentissage)
│   └── clipboard/     règles de l'historique (durée, épinglage, exclusions)
├── data/          dictionnaire embarqué, dictionnaire personnel et presse-papiers (Room),
│                  préférences (DataStore), données emojis
├── ime/           InputMethodService, interface Compose du clavier, lien avec InputConnection
└── settings/      activité de réglages et d'accueil (Compose + design system C!ao)
```

- **Interface du clavier en Compose** dans l'`InputMethodService` : le service fournit lui-même
  `LifecycleOwner`, `ViewModelStoreOwner` et `SavedStateRegistryOwner` à la `ComposeView`.
- **Latence** : aucun travail lourd sur le fil principal pendant la frappe. Le calcul des
  suggestions tourne sur un dispatcher dédié et le résultat périmé d'une frappe précédente est
  annulé.
- **Design system** : le clavier et ses réglages utilisent `core/designsystem` (palette, polices,
  composants `Neo*`). Direction visuelle détaillée au §5.

## 5. Design : néo-brutalisme, thèmes clair et sombre

Le clavier applique le **design system de la suite** ([`docs/design-system.md`](../../../docs/design-system.md),
ADR 0003), lui-même fondé sur [neubrutalism.com](https://neubrutalism.com/#anatomy) : coins carrés,
bordures franches, ombres dures sans flou, palette canonique aux rôles fixes. Cette section ne
précise que ce qui est propre au clavier. La maquette interactive validée le 2026-09-22 (lettres,
symboles, emojis, presse-papiers, en clair et en sombre) sert de référence visuelle pour le lot 2.

### 5.1 Anatomie des éléments du clavier

| Élément | Bordure | Ombre dure | Justification |
|---|---|---|---|
| Touches | 2 px (fine) | 3 px (petite) | Une quarantaine de touches d'environ 33 dp de large : la bordure fine garde les libellés lisibles |
| Puces de la barre de suggestions, onglet emoji actif | 2 px | 3 px (petite) | Actions en ligne |
| Champ de test des réglages | 3 px | 5 px (moyenne), soulevé de 1 px au focus | Champ actif |
| Cartes du presse-papiers | 3 px | 5 px (moyenne) | Cartes |
| Aperçu de touche, fenêtre des accents et des couleurs de peau | 3 px | 8 px (grande) | Éléments flottants |
| Bord supérieur du clavier | 3 px | — | Séparateur de section |

- **Dimensions calées sur Gboard** (hauteur normale, téléphone en portrait) : bandeau de
  suggestions de 48 dp, toujours affiché (bouton presse-papiers, séparateur, puis suggestions) ; touches de 46 dp de haut, au pas de 56 dp (10 dp entre rangées) ; 6 dp
  entre deux touches ; 4 dp de marge latérale ; 8 dp au-dessus de la première rangée et sous la
  dernière. Soit environ 278 dp avec le bandeau, hors barre de navigation.
- **Coins carrés** partout (0 dp).
- **Appui** : la touche glisse de 3 dp dans le sens de son ombre, qui disparaît. Le passage à
  l'état appuyé est **instantané** (une animation se sentirait en frappe rapide) ; le retour dure
  100 ms, comme la transition du design system.
- **Polices** : DM Sans gras pour les libellés des touches ; Archivo Black pour le logo « C!ao » de la
  barre d'espace et les titres.

### 5.2 Couleurs

Le clavier est un écran d'usage courant : il reste **calme**. Les touches de saisie sont neutres et
il n'utilise que **trois accents**, avec leur rôle de la suite :

| Accent | Rôle | Dans le clavier |
|---|---|---|
| Yellow `#FFD23F` | Action principale | Touche Entrée, puce « Coller » |
| Sky `#74B9FF` | Sélection, état actif | Majuscule active et verrouillée, suggestion appliquée par l'autocorrection, variante présélectionnée, onglet emoji actif, élément épinglé |
| Coral Pink `#FF6B6B` | Action destructive | « Tout effacer » (presse-papiers, dictionnaire personnel) |

| Élément neutre | Couleur (clair) |
|---|---|
| Fond du clavier | `page` `#FFFDF5` |
| Touches de lettres, barre d'espace | `surface` `#FFFFFF` |
| Touches de fonction (⇧ inactive, ⌫, `?123`, emoji, virgule, point) | `surfaceMuted` `#EFEBE0` |

Aucun état n'est signalé par la couleur seule : majuscule = icône pleine (verrou + trait si
verrouillée) ; suggestion appliquée = étiquette bordée et gras ; épinglé = icône pleine.

### 5.3 Clair et sombre

- **Deux thèmes complets**, clair et sombre, selon les palettes du design system (en sombre : encre
  crème, bordures et ombres claires sur fond presque noir ; les accents ne changent pas).
- Réglage **Thème** : *Système* (par défaut), *Clair*, *Sombre*. Il s'applique au clavier **et** à
  l'app de réglages.
- En mode *Système*, le clavier suit le changement de thème du téléphone **en direct**, y compris
  quand il est affiché (passage automatique en sombre le soir), sans redémarrage.
- Les barres système de l'app de réglages et la barre de navigation sous le clavier prennent la
  couleur `page` du thème actif.

### 5.4 Contraintes

- **Contraste** : noir sur chaque accent de 7,6:1 à 14,5:1 ; libellés et suggestions au moins AA
  (4,5:1), vérifiés par des tests unitaires sur les paires de couleurs, dans les deux thèmes.
- **Performance** : bordures et ombres dessinées sans flou ni calque hors écran ; l'appui sur une
  touche ne doit recomposer que cette touche, jamais tout le clavier.

## 6. Frappe

### 6.1 Disposition AZERTY (page lettres)

```
 a  z  e  r  t  y  u  i  o  p
 q  s  d  f  g  h  j  k  l  m
 ⇧  w  x  c  v  b  n  '  ⌫
?123  ,  😊  [   espace   ]  .  ⏎
```

- **Majuscules** : un appui sur ⇧ = une majuscule ; double appui = verrouillage ; un appui long
  suivi d'une frappe = majuscule tant que ⇧ est maintenue.
- **Accents par appui long** (fenêtre de variantes, sélection en glissant) :
  `e` é è ê ë € · `a` à â æ ä · `c` ç · `u` ù û ü · `i` î ï · `o` ô œ ö · `y` ÿ · `n` ñ ·
  `'` ’ « » · `.` … ! ? · `,` ; :
- **Aperçu de touche** au-dessus du doigt, désactivable.
- **Retour arrière** : répétition à l'appui long, avec accélération (lettre, puis mot).
- **Barre d'espace** : glisser horizontalement déplace le curseur.
- **Touche Entrée** : son icône et son action suivent `imeOptions`
  (envoyer, rechercher, suivant, OK, retour à la ligne).
- **Retour** : vibration courte à chaque frappe (réglable, activée par défaut) ; son désactivé par
  défaut.

### 6.2 Autres pages

- **Symboles** (`?123`) : chiffres et ponctuation courante, puis une seconde page (`=\<`) pour les
  symboles plus rares (€ £ ¥ § ° © ® ™ ¿ ¡ etc.).
- **Numérique** (`TYPE_CLASS_NUMBER`, `…DATETIME`) : pavé numérique.
- **Téléphone** (`TYPE_CLASS_PHONE`) : pavé téléphonique (+ * # ,).
- **E-mail** : touche `@` à la place de la virgule. **URL** : touches `/` et `.fr`/`.com`
  (appui long) à la place de la virgule et de l'emoji.

### 6.3 Règles typographiques françaises

- **Majuscule automatique** en début de champ et après `. ! ?`, si le champ le demande
  (`TYPE_TEXT_FLAG_CAP_SENTENCES`, via `getCursorCapsMode`). Réglable.
- **Double espace → point** + espace. Réglable, activé par défaut.
- **Apostrophe** : touche dédiée ; l'apostrophe typographique ’ s'obtient par appui long. Les
  suggestions traitent `l'`, `d'`, `qu'`, `j'`… comme des élisions : le mot qui suit est cherché
  seul (`l'ecole` → `l'école`).
- **Espace insécable** avant `; : ! ?` : réglage désactivé par défaut (beaucoup d'apps et de
  messageries l'affichent mal).

## 7. Suggestions et correction

### 7.1 Comportement

- **Barre de suggestions** au-dessus des touches : trois propositions au maximum. Au centre, la
  meilleure proposition, qui sera appliquée par l'autocorrection.
- **Complétion** : `bonj` → `bonjour`.
- **Correction des fautes de frappe**, en tenant compte de la proximité des touches AZERTY :
  `bonjoir` → `bonjour`.
- **Restitution des accents** : `ecole` → `école`, `ou` → `où` quand c'est plus probable
  (la version sans accent reste proposée à côté).
- **Autocorrection** à l'espace ou à la ponctuation, seulement si la confiance est suffisante.
  Un **retour arrière immédiatement après** annule la correction et rétablit le mot tapé, qui
  n'est plus corrigé pour la suite de la saisie.
- **Pas de suggestions** dans les champs sensibles (§3) ni quand l'app les refuse
  (`TYPE_TEXT_FLAG_NO_SUGGESTIONS`, champs non textuels).
- Réglages : suggestions (activées), autocorrection (activée).

### 7.2 Dictionnaire embarqué

- Un dictionnaire français d'environ 150 000 à 200 000 formes fléchies avec leur fréquence,
  embarqué dans l'APK dans un format compact et rapide à charger (précompilé au build ; l'app
  ne lit jamais la liste brute). Budget indicatif : moins de 5 Mo dans l'APK, chargement en moins de
  300 ms.
- **Source à arrêter au lot 4, licence comprise** : elle doit être compatible avec une
  redistribution dans une app MIT, et l'attribution sera affichée dans les réglages. Pistes :
  - la liste de mots française d'AOSP / OpenBoard (`fr_wordlist.combined`, reprise par HeliBoard),
    avec des fréquences, mais sa licence exacte reste à confirmer ;
  - Lexique 3 (fréquences issues de corpus ; CC BY-SA 4.0, contrainte de partage à l'identique
    sur les données) ;
  - le dictionnaire Hunspell de Grammalecte (MPL 2.0 ; formes fléchies, sans fréquences, à
    combiner avec une autre source).
- Si la source retenue fournit des bigrammes, la prédiction du mot suivant pourra entrer en v1 ;
  sinon, elle est repoussée.

### 7.3 Apprentissage personnel

- Un mot inconnu tapé et **conservé** par l'utilisateur deux fois est ajouté au dictionnaire
  personnel. Il est alors suggéré, mais jamais corrigé.
- Les mots choisis dans la barre de suggestions voient leur fréquence personnelle augmenter.
- **Jamais d'apprentissage** dans les champs sensibles ni en navigation privée (§3).
- Réglages : liste des mots appris (recherche, suppression unitaire), ajout manuel, effacement total.

### 7.4 Objectifs de qualité

- Moins de 50 ms entre une frappe et l'affichage des suggestions sur le Pixel 10 Pro.
- Le moteur est testé unitairement sur un jeu de cas français versionné dans le dépôt : fautes de
  frappe courantes, accents, élisions, majuscules, noms propres.

## 8. Emojis

- Touche 😊 (appui long sur la virgule quand la touche est masquée) → panneau emojis à la place des
  touches.
- **Catégories** Unicode (smileys, personnes, animaux et nature, nourriture, activités, voyages,
  objets, symboles, drapeaux), plus un onglet **Récents** (30 derniers, locaux).
- **Couleur de peau** par appui long sur les emojis qui la supportent ; le dernier choix devient
  la valeur par défaut de cet emoji.
- Données : fichier `emoji-test.txt` d'Unicode (licence Unicode, permissive), filtré au build selon
  la version d'emoji affichable par la police système (`EmojiCompat` si nécessaire).
- Pas d'emojis récents enregistrés en navigation privée.

## 9. Presse-papiers

- L'historique s'affiche depuis une touche dédiée de la barre de suggestions (icône
  presse-papiers) : les derniers textes copiés, du plus récent au plus ancien ; un appui colle.
- **Puce « coller »** : juste après une copie, le texte copié est proposé dans la barre de
  suggestions pendant 1 minute.
- **Texte uniquement**, 25 éléments au maximum (les plus anciens non épinglés sont supprimés).
- **Durée de conservation** réglable : 1 heure (par défaut), 24 heures, 7 jours. Les éléments
  **épinglés** sont conservés sans limite.
- **Contenus sensibles exclus** : les copies marquées sensibles (`ClipDescription.EXTRA_IS_SENSITIVE`,
  gestionnaires de mots de passe) ne sont ni enregistrées ni proposées.
- Réglages : activer/désactiver l'historique (activé), durée, tout effacer.

## 10. Réglages et installation

**App de réglages** (icône C!ao Clavier dans le lanceur), en Compose avec le design system C!ao :

1. **Accueil / mise en route** : tant que le clavier n'est pas activé ou pas sélectionné, un
   parcours en deux étapes : activer C!ao Clavier dans les réglages système
   (`Settings.ACTION_INPUT_METHOD_SETTINGS`), puis le choisir (`showInputMethodPicker`). Suivi d'une
   zone de test pour essayer le clavier.
2. **Préférences** : frappe (vibration, son, aperçu, majuscule automatique, double espace, espace
   insécable), correction (suggestions, autocorrection), presse-papiers, apparence (thème
   système/clair/sombre, voir §5.3 ; hauteur du clavier en 3 tailles).
3. **Dictionnaire personnel** (§7.3) et **données** : tout effacer (mots, récents, presse-papiers).
4. **À propos** : version, licences (dictionnaire, données Unicode, polices), lien vers le code
   source et la politique de confidentialité.

**Garde-fou au build** : une tâche Gradle vérifie que le manifeste fusionné de `release` ne
déclare pas la permission `INTERNET` (le build échoue sinon). Elle est ajoutée aux plugins de
convention pour s'appliquer à toutes les apps de la suite qui n'en ont pas besoin.

## 11. Accessibilité

- Chaque touche a une description TalkBack (« e accent aigu », « Majuscule verrouillée »…) ;
  compatibilité avec l'exploration tactile du clavier par TalkBack.
- Contrastes vérifiés pour les libellés de touches, en clair et en sombre (§5.4).
- Respect de la taille de police système dans les réglages (le clavier garde des libellés à taille
  fixe, mais la hauteur est réglable).

## 12. Plan de réalisation

Chaque lot = une ou plusieurs PR, avec une issue dédiée dans le jalon « C!ao Clavier v1 ».

| Lot | Contenu | Dépend de |
|---|---|---|
| 0 | **`core/designsystem`** : extraction du thème et des composants `Neo*` de la Galerie, mis aux règles de la suite (ADR 0003) ; garde-fou `INTERNET` dans `build-logic` | — |
| 1 | **Squelette** : module `apps/clavier`, `InputMethodService` + Compose, app de réglages avec la mise en route, job CI, `PRIVACY.md`, `CHANGELOG.md`, `CLAUDE.md` | 0 |
| 2 | **Frappe** : touches néo-brutalistes en clair et sombre (§5, maquette validée), AZERTY, majuscules, accents, pages symboles, claviers spécialisés, touche Entrée, retour arrière, curseur sur l'espace, vibration, aperçu | 1 |
| 3 | **Règles françaises** : majuscule automatique, double espace, élisions, espace insécable | 2 |
| 4 | **Dictionnaire et suggestions** : choix de la source (licence), format compilé, moteur (complétion, proximité, accents), barre de suggestions, autocorrection et annulation | 3 |
| 5 | **Apprentissage et vie privée** : dictionnaire personnel, champs sensibles, navigation privée, exclusion des sauvegardes, écran de gestion | 4 |
| 6 | **Emojis** : panneau, catégories, récents, couleurs de peau | 2 |
| 7 | **Presse-papiers** : historique, puce coller, épinglage, durée, exclusions | 2 |
| 8 | **Finitions** : réglages complets, thèmes et hauteur, accessibilité, fiche Play Store, test d'usage réel sur le Pixel | 5, 6, 7 |

Les lots 6 et 7 sont indépendants des lots 3 à 5 et peuvent avancer en parallèle.

## 13. Points ouverts

1. **Source du dictionnaire** et sa licence (lot 4) : c'est le seul point qui peut remettre en cause
   la qualité des suggestions.
2. **Prédiction du mot suivant** : v1 ou v2, selon les données disponibles (lot 4).
3. **Rangée de chiffres permanente** au-dessus des lettres : option ou non (lot 8, selon l'usage).
4. **Icône et nom affiché** : « C!ao Clavier » dans le lanceur et dans la liste des claviers du
   système.
