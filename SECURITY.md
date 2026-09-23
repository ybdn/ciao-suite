# Sécurité

## Signaler une faille

Ne publiez pas de faille dans une issue publique. Utilisez le signalement privé de GitHub :
<https://github.com/ybdn/ciao-suite/security/advisories/new>.

Décrivez l'app et la version concernées, les étapes pour reproduire et l'impact. Le suivi est
assuré par un mainteneur unique, sans délai garanti ; un correctif est publié avec une nouvelle
version de l'app.

## Périmètre

Les apps de la suite n'ont aucun compte, serveur ni réseau (principes dans `docs/vision.md`) : une
faille utile concerne donc surtout le traitement des fichiers, les permissions Android, ou la
chaîne de build et de release.

## Vérifier un APK téléchargé

Chaque GitHub Release joint l'APK, sa somme SHA-256 (`.apk.sha256`) et une attestation de
provenance :

```bash
sha256sum -c galerie-release.apk.sha256
gh attestation verify galerie-release.apk --repo ybdn/ciao-suite
```
