# Journal des versions — C!ao Galerie

Format : une section par version taguée (`galerie-vX.Y.Z`), la plus récente en haut ; une ligne
par changement visible par l'utilisateur. Règles : `docs/workflow-git.md` (section 4) à la racine
de la suite.

## Non publié

Rien pour l'instant.

## 0.2.0 — 2026-09-25

Version de développement (beta), pas encore publiée sur le Play Store.

- Identifiant de l'app (`applicationId`) aligné sur la convention de la suite :
  `dev.ybdn.ciaocloud` → `dev.ybdn.ciao.galerie`. L'app renommée s'installe à côté de l'ancienne ;
  elle ne reprend ni les transferts en cours, ni les réglages et l'accès au SSD, ni les favoris et
  décisions de tri — désinstaller l'ancienne app et reconfigurer celle-ci.
- Correction : hauteur égale entre les éléments d'une même rangée (stats, boutons, cartes de
  tri) quand leurs libellés tiennent sur un nombre de lignes différent.
- Couleurs alignées sur les rôles du design system (principale, destructive, succès,
  avertissement, information, sélection) plutôt que sur les teintes transposées mécaniquement ;
  seules les catégories de contenu (photos/vidéos) gardent une teinte fixe.

## 0.1.1 — 2026-09-23

Version de développement, pas encore publiée sur le Play Store. Sert aussi de premier essai du
workflow de release (APK signé, somme SHA-256, attestation de provenance). Fonctionnalités :

- Délestage des photos et vidéos vers un SSD en USB-C, rangées par date, avec vérification
  avant suppression du téléphone.
- Galerie unifiée téléphone + SSD, visionneuse photo et vidéo.
- Édition des photos (recadrage, rotation, réglages, filtres), modification des métadonnées et
  partage sans métadonnées.
- Tri de la pellicule par swipe (garder, supprimer, revoir plus tard).
