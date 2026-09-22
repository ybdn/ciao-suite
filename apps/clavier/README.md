# C!ao Clavier

Clavier Android français, sans aucune connexion réseau : ni la frappe, ni le presse-papiers, ni
les mots appris ne quittent l'appareil. Fait partie de la suite [C!ao](../../README.md).

Spécification de référence : [`docs/spec-v1.md`](docs/spec-v1.md). Contexte pour Claude Code :
[`CLAUDE.md`](CLAUDE.md). Politique de confidentialité : [`PRIVACY.md`](PRIVACY.md).

## État

En développement (lot 1 du jalon « C!ao Clavier v1 ») : squelette de l'app (service de clavier,
écran de mise en route). La disposition AZERTY, les suggestions, les emojis et le presse-papiers
arrivent aux lots suivants — voir la spec, §12.

## Commandes

Depuis la racine de la suite :

```bash
./gradlew :apps:clavier:assembleDebug
./gradlew :apps:clavier:testDebugUnitTest
```
