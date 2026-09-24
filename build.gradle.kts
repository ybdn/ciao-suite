// Déclare les plugins une seule fois (versions du catalogue) ; les modules les appliquent via les
// plugins de convention de build-logic/. Kotlin est intégré à AGP 9 : `kotlin.android` n'est plus
// appliqué, mais le déclarer ici impose notre version de Kotlin plutôt que celle embarquée par AGP.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
