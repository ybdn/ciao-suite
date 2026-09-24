# Dictionnaire français de C!ao Clavier

Liste de mots des suggestions et de la correction (spec §7.2). Le build la compile en arbre de
préfixes binaire (`assets/dictionary/fr.dict`, environ 2,4 Mo dans l'APK) : l'app ne lit jamais
ces fichiers texte.

| Fichier | Contenu |
|---|---|
| `fr_words.tsv` | 200 000 mots et leur nombre d'occurrences, du plus au moins fréquent |
| `fr_bigrams.tsv` | Pour chaque mot, les trois mots qui le suivent le plus souvent (prédiction du mot suivant) |

Les deux fichiers sont **générés** : ne pas les modifier à la main.

## Source et licence

[Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/) (Université de Leipzig), sous
licence [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) : redistribution permise dans
une app MIT, à condition de citer la source et d'indiquer les modifications. L'attribution est
affichée dans les réglages du clavier (carte « Correction »).

Corpus utilisés (un million de phrases chacun) : `fra_news_2020_1M`, `fra_news_2022_1M`,
`fra_news_2023_1M`, `fra_news_2024_1M`, `fra_newscrawl-public_2019_1M`, `fra_wikipedia_2021_1M`,
`fra-fr_web_2013_1M`, `fra_mixed_2009_1M`.

> D. Goldhahn, T. Eckart, U. Quasthoff : *Building Large Monolingual Dictionaries at the Leipzig
> Corpora Collection: From 100 to 200 Languages*. LREC 2012.

Modifications apportées (`PrepareWordListTask`, `build-logic`) : élisions détachées (`l'école` →
`l'` + `école`), variantes de casse fusionnées, fautes courantes des corpus web écartées (`etre`,
`cest`, `beacoup`…), seuls les mots en alphabet latin gardés, puis les 200 000 plus fréquents.

Les listes de HeliBoard (dépôt `aosp-dictionaries`, GPL-3) et d'AOSP (licence jamais établie) ont
été écartées pour leur licence ambiguë.

## Régénérer

1. Télécharger les corpus (`https://downloads.wortschatz-leipzig.de/corpora/<corpus>.tar.gz`) et
   extraire de chacun `<corpus>-words.txt` et `<corpus>-co_n.txt` dans un dossier `<corpus>/`.
2. Depuis la racine de la suite :

   ```bash
   ./gradlew :apps:clavier:prepareFrenchWordList -Pleipzig=/chemin/vers/corpus
   ./gradlew :apps:clavier:testDebugUnitTest   # jeu de cas français sur le nouveau dictionnaire
   ```

Pour ajouter un corpus, le déposer dans le même dossier : tous les sous-dossiers sont lus.
