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
include(":apps:clavier")

// Modules partagés (docs/adr/0001) : créés quand une deuxième app en a besoin.
include(":core:designsystem")
