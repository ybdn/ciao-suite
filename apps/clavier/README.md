# C!ao Clavier

Clavier Android français, sans aucune connexion réseau : ni la frappe, ni le presse-papiers, ni
les mots appris ne quittent l'appareil. Fait partie de la suite [C!ao](../../README.md).

Spécification de référence : [`docs/spec-v1.md`](docs/spec-v1.md). Contexte pour Claude Code :
[`CLAUDE.md`](CLAUDE.md). Politique de confidentialité : [`PRIVACY.md`](PRIVACY.md).

## État

En développement (jalon « C!ao Clavier v1 ») : frappe AZERTY, règles typographiques françaises,
suggestions et correction, emojis et presse-papiers sont faits ; l'apprentissage personnel et les
finitions arrivent aux lots suivants — voir la spec, §12.

Dictionnaire : [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/), CC BY 4.0 — voir
[`dictionary/README.md`](dictionary/README.md).

## Commandes

Depuis la racine de la suite :

```bash
./gradlew :apps:clavier:assembleDebug
./gradlew :apps:clavier:testDebugUnitTest
```
