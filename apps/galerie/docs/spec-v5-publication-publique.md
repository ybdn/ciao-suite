# Spec v5 — Publication publique (Play Store)

Cette spécification **prime sur `prompt-initial.md`** pour tout ce qui touche à la distribution,
la licence et la confidentialité — en particulier la clause de `prompt-initial.md:85` ("Pas de
publication Play Store (app strictement personnelle, distribution par APK uniquement)"), devenue
obsolète.

## Décision

C!ao Galerie est distribuée publiquement sur le **Google Play Store**, en plus du build/artifact
APK déjà proposé via GitHub Actions pour un usage développeur.

## Ce qui change

- **Licence** : MIT (`LICENSE` à la racine). Le dépôt GitHub devient public.
- **Politique de confidentialité** : `PRIVACY.md`, exigée par Play Console pour les permissions
  médias sensibles utilisées par l'application. Contact public : ybdn@pm.me.
- **Signature release** : un `signingConfigs.release` est ajouté dans `app/build.gradle.kts`,
  lisant un fichier local `keystore.properties` (non commité, gitignored). Sans ce fichier, le
  build `assembleRelease` reste non signé (ne casse pas la CI/les builds locaux sans clé).
- **Optimisation release** : `isMinifyEnabled = true` et `isShrinkResources = true` en
  `buildTypes.release`, avec les règles ProGuard nécessaires dans `app/proguard-rules.pro`.
- **Fiche Play Store** : brouillons dans `docs/store-listing/` (description, aide-mémoire pour le
  formulaire Data Safety de la console).

## Ce qui ne change PAS

- **Modèle d'usage mono-utilisateur/mono-device** : l'application reste conçue pour être installée
  et utilisée par une seule personne sur son propre téléphone, sans compte, sans cloud, sans
  synchronisation multi-appareil. La publication sur le Store ne fait qu'élargir le canal de
  distribution — chaque utilisateur installe sa propre instance indépendante de l'app, comme le
  développeur original.
- **Aucun backend, aucun réseau, aucune télémétrie/analytics/crash reporting.**
- **`android.hardware.usb.host` requis** (`required="true"`) : l'app n'a de sens que pour
  transférer vers un SSD USB — ce filtre Play Store exclut naturellement les appareils
  incompatibles, ce qui est le comportement voulu plutôt qu'un problème à corriger.
- **`minSdk = 33`** (Android 13) conservé : couvre déjà une large majorité du parc actif, évite un
  chantier de compatibilité descendante sur des API récentes déjà utilisées (permissions médias
  granulaires API 33+, `MediaStore.createDeleteRequest()`).
- **Langue de l'interface** : français uniquement pour cette première publication. Une
  internationalisation (anglais) est reportée à une v6 éventuelle si l'app trouve un public au-delà
  des utilisateurs francophones.

## Hors scope (actions manuelles, non automatisables par un agent de code)

- Génération et conservation sécurisée du vrai keystore de production.
- Création du compte développeur Google Play Console.
- Captures d'écran et visuels promotionnels de la fiche Store.
- Remplissage et soumission réelle du formulaire Data Safety sur la console (le fichier
  `docs/store-listing/data-safety.md` sert de brouillon, pas de source de vérité officielle).
- Choix du `versionCode`/`versionName` de la première release publique.
