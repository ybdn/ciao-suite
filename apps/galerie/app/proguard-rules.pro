# Ajouter ici les règles ProGuard/R8 spécifiques au projet si la minification release est activée.

# Conserve les numéros de ligne dans les stacktraces (release minifiée), sans nuire à l'obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room (entités/DAO générés par KSP), Coil, Media3 et ExifInterface embarquent déjà leurs propres
# consumer-rules (via leurs AAR) : pas de règle supplémentaire nécessaire a priori. À compléter si
# `assembleRelease` échoue en R8 ou si un crash en release révèle une classe strippée à tort.
