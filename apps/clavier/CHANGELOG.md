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
  Accents et claviers spécialisés à venir.
- Dimensions du clavier alignées sur Gboard, mesurées sur l'appareil : touches de 58 dp de haut,
  bandeau de 48 dp au-dessus des touches, barre d'espace plus large.
- Deux pages de symboles : chiffres et ponctuation, puis symboles rares.
- Icônes dessinées pour la majuscule, le verrouillage, le retour arrière, la touche Entrée et
  les emojis, à la place des caractères de remplacement.
- Majuscule automatique en début de champ et après un point, quand le champ la demande.
- Place réservée sous le clavier pour les boutons d'Android (masquer le clavier, changer de
  clavier).
