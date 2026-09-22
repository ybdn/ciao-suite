# Journal des versions — C!ao Clavier

Format : une section par version taguée (`clavier-vX.Y.Z`), la plus récente en haut ; une ligne
par changement visible par l'utilisateur. Règles : `docs/workflow-git.md` (section 4) à la racine
de la suite.

## Non publié

Version de développement `0.1.0`, pas encore publiée sur le Play Store. Fonctionnalités :

- Squelette de l'app : service de clavier (`InputMethodService`), écran de mise en route
  (activation et sélection du clavier dans les réglages système, zone de test).
- Page lettres du clavier AZERTY : majuscule (simple/verrouillage), retour arrière avec
  répétition à l'appui long, touche Entrée adaptée au champ actif, vibration à la frappe.
  Accents, pages symboles et claviers spécialisés à venir.
