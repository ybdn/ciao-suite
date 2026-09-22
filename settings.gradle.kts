pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ciao-suite"

// Applications de la suite (une entrée par app publiée sur le Play Store).
include(":apps:galerie")

// Modules partagés : à créer au moment où une deuxième app en a besoin (voir docs/adr/0001).
