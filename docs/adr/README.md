# Décisions d'architecture (ADR)

Chaque décision structurante de la suite est consignée dans un fichier numéroté, qui n'est plus
modifié une fois accepté : une décision qui change fait l'objet d'un nouvel ADR, qui remplace
l'ancien (et l'ancien passe au statut « Remplacé par NNNN »).

| N° | Décision | Statut |
|---|---|---|
| [0001](0001-monorepo.md) | Monorepo Gradle unique avec plugins de convention | Accepté |
| [0002](0002-branches-et-versions.md) | Une seule branche permanente, versions par tags d'app | Accepté |
| [0003](0003-design-system-neubrutalism.md) | Design system néo-brutaliste commun, fondé sur neubrutalism.com | Accepté |

## Modèle

Copier dans `NNNN-titre-court.md` :

```markdown
# NNNN — Titre

- Statut : Proposé | Accepté | Remplacé par NNNN
- Date : AAAA-MM-JJ

## Contexte

Le problème, les contraintes, ce qui force à décider.

## Décision

Ce qui est décidé, formulé de façon vérifiable.

## Conséquences

Ce que ça implique, y compris les inconvénients acceptés.
```
