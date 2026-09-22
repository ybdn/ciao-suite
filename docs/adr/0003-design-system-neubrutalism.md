# 0003 — Design system néo-brutaliste commun, fondé sur neubrutalism.com

- Statut : Accepté
- Date : 2026-09-22

## Contexte

La Galerie avait son propre style néo-brutaliste (inspiré d'un site tiers) : coins arrondis de 6 à
12 dp, une seule ombre de 4 dp, six couleurs d'accent saturées sans rôle défini, utilisées aussi
bien pour des actions que pour décorer des étiquettes. Avec l'arrivée du clavier, deuxième app de
la suite, ce style devient un module partagé (ADR 0001) et doit être fixé pour toutes les apps.

La maquette du clavier a été validée après deux itérations : les accents d'origine étaient jugés
trop tranchants, et la direction retenue est celle du guide
[neubrutalism.com](https://neubrutalism.com/#anatomy) : ses jetons (bordure 3 px, ombres dures de
3/5/8 px, coins carrés, appui de 3 px), sa palette canonique, et ses règles d'usage (1 à 3 accents,
couleurs catégorielles, écrans d'usage courant plus calmes, accessibilité WCAG 2.2).

## Décision

1. Toutes les apps utilisent le module **`core/designsystem`** ; les règles sont écrites dans
   [`docs/design-system.md`](../design-system.md).
2. Anatomie : coins carrés ; bordure 3 dp (2 dp pour les éléments denses) ; ombres dures sans flou
   sur trois niveaux (3, 5, 8 dp) ; appui qui enfonce l'élément de 3 dp dans son ombre.
3. Couleurs : palette canonique du guide (fond `#FFFDF5`, encre noire, Yellow, Coral Pink, Sky,
   Soft Green, Orange, Lavender), chaque accent ayant **un rôle** exposé par `NeoTone` (`Primary`,
   `Danger`, `Selected`, `Success`, `Warning`, `Info`) ; trois accents au plus par écran.
4. Thème sombre propre à la suite (le guide n'en donne pas) : encre crème, bordures et ombres
   claires sur fond presque noir, accents inchangés.
5. Écarts assumés au guide : anneau de focus plus sombre que le Sky canonique (`#2E7BD6`) pour
   atteindre 3:1 ; appui instantané (retour en 100 ms) pour ne pas ralentir la frappe ; polices
   Archivo Black et DM Sans, toutes deux recommandées par le guide.

## Conséquences

- La Galerie change d'apparence : coins carrés, nouvelles couleurs, trois niveaux d'ombre. Ses
  couleurs ont été transposées mécaniquement vers la teinte canonique la plus proche (Coral et
  Brick → Coral Pink, Lime → Soft Green, Teal → Lavender) ; leur alignement sur les rôles se fait
  écran par écran dans un chantier séparé.
- Une app n'écrit plus de couleur, de bordure ou d'ombre en dur : tout besoin nouveau s'ajoute au
  design system, pour toutes les apps.
- Les composants `Neo*` ont changé de valeurs par défaut : `NeoButton` est `Primary` (Yellow) au
  lieu de Coral, `NeoNotice` est `Warning` (Orange) au lieu de Yellow.
