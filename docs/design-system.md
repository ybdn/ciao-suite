# Design system de la suite C!ao

Toutes les apps C!ao partagent un design **néo-brutaliste** fondé sur
[neubrutalism.com](https://neubrutalism.com/#anatomy) (décision : [ADR 0003](adr/0003-design-system-neubrutalism.md)).
Il est implémenté dans le module **`core/designsystem`** (package `dev.ybdn.ciao.designsystem`) :
une app n'invente jamais ses propres couleurs, bordures ou ombres, elle utilise ce module.

Le principe, repris du guide : **une base noir et blanc structurelle, ponctuée d'accents à plat**.
L'expression est franche, mais l'interaction reste un modèle Android classique : le néo-brutalisme
est une couche visuelle, pas une autre façon d'utiliser l'app.

## 1. Anatomie

| Jeton (`core/designsystem`) | Valeur | Usage |
|---|---|---|
| `BorderWidth` | 3 dp | Bordure canonique de tous les composants |
| `BorderThin` | 2 dp | Éléments denses ou secondaires (touches de clavier, séparateurs internes) |
| `ShadowSmall` | 3 dp | Étiquettes, puces, actions en ligne, touches |
| `ShadowMedium` | 5 dp | Cartes, boutons, panneaux, champ focalisé (niveau par défaut) |
| `ShadowLarge` | 8 dp | Éléments flottants : feuilles, dialogues, fenêtres surgissantes |
| `PressOffset` | 3 dp | Déplacement d'un élément appuyé |
| `PressReleaseMillis` | 100 ms | Retour d'un élément appuyé |

- **Coins carrés** (0 dp) partout ; les seules formes rondes admises sont fonctionnelles
  (pastilles de sélection sur une vignette, curseurs).
- **Ombres dures** : décalées vers le bas et la droite, **jamais floutées**, de la couleur `outline`
  du thème. Les trois niveaux donnent la profondeur : un élément n'a pas une ombre plus grande
  que son niveau, sinon la hiérarchie s'effondre.
- **Appui** : l'élément glisse de `PressOffset` dans le sens de son ombre et l'ombre disparaît
  (il est « enfoncé »). L'enfoncement est immédiat, le retour prend `PressReleaseMillis`.
- **Sélection persistante** (choix segmenté, filtre, onglet) : l'élément choisi reste enfoncé et
  prend la couleur `Selected`.
- **Champ de saisie** : petite ombre au repos ; au focus, il se soulève de 1 dp et prend l'ombre
  moyenne.
- **Surface** : `Modifier.neoSurface(color, outline, shadowOffset, borderWidth)` dessine l'aplat, la
  bordure et l'ombre ; les composants `Neo*` (bouton, carte, étiquette, statistique, message,
  choix segmenté, puces, barre de navigation, interrupteur, champ, barre de progression)
  l'appliquent déjà.

## 2. Couleurs

### Neutres (s'inversent entre clair et sombre)

| Rôle (`NeoPalette`) | Clair | Sombre |
|---|---|---|
| `page` : fond d'écran | `#FFFDF5` | `#141311` |
| `surface` : cartes, champs, touches | `#FFFFFF` | `#201E1B` |
| `surfaceMuted` : surfaces secondaires, touches de fonction | `#EFEBE0` | `#2C2823` |
| `content` : texte | `#000000` | `#F6EEDF` |
| `outline` : bordures et ombres | `#000000` | `#F6EEDF` |

En sombre, l'encre devient crème : bordures et ombres claires sur fond presque noir (le guide ne
donne pas de règle pour le sombre ; c'est le choix de la suite).

### Accents : palette canonique, un rôle chacun

Les accents sont identiques en clair et en sombre et portent **toujours du texte noir** (`Ink`).

| Couleur | Hex | Rôle (`NeoTone`) | Exemples |
|---|---|---|---|
| Yellow | `#FFD23F` | `Primary` : action principale | Bouton principal, touche Entrée |
| Sky | `#74B9FF` | `Selected` : sélection, état actif | Onglet, filtre, choix actif, interrupteur activé |
| Coral Pink | `#FF6B6B` | `Danger` : action destructive, erreur | Supprimer, « Tout effacer », message d'erreur |
| Soft Green | `#88D498` | `Success` : succès, confirmation | Autorisation accordée, élément sauvegardé, progression |
| Orange | `#FFA552` | `Warning` : avertissement | Message d'avertissement (`NeoNotice` par défaut) |
| Lavender | `#B8A9FA` | `Info` : information, catégorie secondaire | Étiquette d'information |

Règles :

1. **Trois accents au plus par écran.** Les écrans d'usage courant (clavier, listes, formulaires)
   restent calmes : surfaces neutres, accents réservés aux actions et aux états.
2. **Le rôle d'abord.** Pour une action, un état ou un message, on écrit le rôle
   (`NeoTone.Primary`, `NeoTone.Danger`…), jamais la teinte. Les teintes seules (`NeoTone.Sky`…)
   servent uniquement à des **catégories** de contenu (distinguer photos et vidéos, par exemple).
3. **Jamais la couleur seule** pour transmettre une information : une icône, un texte ou une forme
   (élément enfoncé, icône pleine) l'accompagne toujours.
4. **Pas de dégradé**, pas de transparence décorative : des aplats.

### Contraste (WCAG 2.2 AA)

| Paire | Contraste |
|---|---|
| Noir sur `page` clair | 20,6:1 |
| Noir sur Yellow / Sky / Coral Pink | 14,5:1 / 10,1:1 / 7,6:1 |
| Noir sur Soft Green / Orange / Lavender | 11,9:1 / 10,8:1 / 10,1:1 |

- Texte : au moins 4,5:1 (3:1 au-delà de 24 sp).
- Bordures et éléments d'interface : au moins 3:1. Le Sky canonique n'a que 2:1 sur le fond
  clair : tout anneau de focus utilise donc `FocusRing` (`#2E7BD6`, 4,2:1 en clair, 4,4:1 en
  sombre).
- Cibles tactiles d'au moins 48 dp : l'épaisseur des bordures ne compte pas comme zone cliquable.

## 3. Typographie

| Rôle | Police | Usage |
|---|---|---|
| Affichage | **Archivo Black** (une seule graisse) | Titres, grands chiffres |
| Texte | **DM Sans** (police variable, 400 à 700) | Texte courant, libellés, boutons (700) |
| Étiquettes techniques | Monospace gras, capitales, espacé | `NeoTag`, barre de navigation (`LabelMono`) |

Les deux polices sont embarquées dans `core/designsystem` (pas de téléchargement) et font partie
des polices recommandées par le guide. Le texte courant reste sobre (« boring on purpose ») :
l'impact vient des titres, de la taille et de la graisse.

## 4. Utiliser le design system dans une app

```kotlin
// build.gradle.kts de l'app
dependencies {
    implementation(project(":core:designsystem"))
}
```

```kotlin
CiaoTheme(darkTheme = isSystemInDarkTheme()) {
    NeoButton("Enregistrer", onClick = ::save)                        // Primary par défaut
    NeoButton("Supprimer", onClick = ::delete, tone = NeoTone.Danger)
    NeoNotice("Le SSD n'est pas branché.")                            // Warning par défaut
    NeoTag("Accordée", tone = NeoTone.Success)
}
```

- Le thème clair/sombre et le choix de l'utilisateur (Système, Clair, Sombre) sont gérés par
  l'app, qui passe `darkTheme` à `CiaoTheme`.
- Un besoin non couvert par les composants `Neo*` : l'ajouter à `core/designsystem` (pour
  toutes les apps), pas dans l'app.
- Toute évolution de ces règles passe par un ADR qui remplace l'ADR 0003.
