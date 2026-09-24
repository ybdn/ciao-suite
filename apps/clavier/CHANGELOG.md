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
- Dimensions du clavier alignées sur Gboard, mesurées sur l'appareil : touches de 58 dp de haut,
  bandeau de 48 dp au-dessus des touches, barre d'espace plus large.
- Deux pages de symboles : chiffres et ponctuation, puis symboles rares.
- Icônes dessinées pour la majuscule, le verrouillage, le retour arrière, la touche Entrée et
  les emojis, à la place des caractères de remplacement.
- Majuscule automatique en début de champ et après un point, quand le champ la demande.
- Place réservée sous le clavier pour les boutons d'Android (masquer le clavier, changer de
  clavier).
- Accents et variantes par appui long, choisis en glissant le doigt (é è ê ë €, à â æ ä, ç, ù û
  ü, î ï, ô œ ö, ÿ, ñ, ’ « », … ! ?, ; :).
- Aperçu de la touche appuyée au-dessus du doigt.
- Claviers adaptés au champ : pavé numérique, pavé de date, pavé téléphonique ; « @ » pour les
  e-mails ; « / » et « .fr » (.com, .org… par appui long) pour les adresses web.
- Glisser sur la barre d'espace déplace le curseur.
- Retour arrière maintenu : caractère par caractère, puis mot par mot ; il efface la sélection
  entière et ne coupe plus un emoji en deux.
- Majuscule maintenue : les lettres tapées pendant l'appui sont en majuscules ; le double appui
  verrouille sans délai sur le premier appui.
- Double espace pour un point, en fin de mot.
- Espace insécable avant ; : ! ? (réglage désactivé par défaut), sauf dans une heure (« 10:30 »)
  ou une adresse web.
- Réglages de frappe dans l'app : majuscule automatique, double espace, espace insécable.
- Les règles typographiques ne s'appliquent jamais aux mots de passe, e-mails et adresses web.
- Panneau emojis : neuf catégories et les 30 derniers emojis utilisés ; couleur de peau par appui
  long, retenue pour chaque emoji. Seuls les emojis que le téléphone sait afficher sont proposés.
  Rien n'est retenu en navigation privée ni dans un mot de passe.
- Réglages : effacer les emojis récents.
- Historique du presse-papiers : les 25 derniers textes copiés, collés d'un appui ; épinglage ;
  suppression d'un élément. Conservation 1 heure par défaut (24 heures ou 7 jours au choix), sans
  limite pour les épinglés. Les copies sensibles (gestionnaires de mots de passe) sont ignorées.
- Puce « Coller » dans le bandeau pendant une minute après une copie.
- Réglages : activer l'historique, durée de conservation, effacer l'historique.
- Les données du clavier sont exclues des sauvegardes et du transfert vers un nouvel appareil.
- Suggestions de mots au-dessus des touches : complétion (« bonj » → « bonjour »), correction
  des fautes de frappe selon les touches voisines, accents restitués (« ecole » → « école »),
  apostrophe d'élision ajoutée (« jai » → « j'ai »).
- Correction automatique à l'espace ou à la ponctuation, annulée par un retour arrière ; le mot
  tapé reste proposé pour le garder.
- Prédiction du mot suivant après une espace.
- Réglages : suggestions et correction automatique activables séparément.
- Dictionnaire personnel : un mot inconnu tapé et gardé deux fois est appris, puis proposé et
  jamais corrigé ; les mots choisis dans la barre remontent dans les suggestions. Rien n'est
  appris dans un mot de passe ni en navigation privée.
- Réglages : liste des mots appris avec recherche, ajout et suppression d'un mot, effacement
  total (confirmé par un second appui).
