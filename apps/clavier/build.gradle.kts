import dev.ybdn.ciao.buildlogic.dictionary.CompileDictionaryTask
import dev.ybdn.ciao.buildlogic.dictionary.PrepareWordListTask

plugins {
    alias(libs.plugins.ciao.android.application)
    alias(libs.plugins.ciao.android.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.ybdn.ciao.clavier"

    defaultConfig {
        applicationId = "dev.ybdn.ciao.clavier"
        versionCode = 1
        versionName = "0.1.0"
    }
}

ksp {
    // Schémas Room versionnés : base des migrations explicites (jamais de migration destructive).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Dictionnaire français (spec §7.2) : liste de mots versionnée dans dictionary/, compilée au build
// en arbre de préfixes binaire dans les assets. Voir dictionary/README.md.
val prepareFrenchWordList by tasks.registering(PrepareWordListTask::class) {
    group = "dictionary"
    description = "Régénère dictionary/fr_*.tsv depuis des corpus Leipzig (-Pleipzig=<dossier>)"
    providers.gradleProperty("leipzig").orNull?.let { corporaDirectory.set(file(it)) }
    maxWords.set(200_000)
    wordsFile.set(layout.projectDirectory.file("dictionary/fr_words.tsv"))
    bigramsFile.set(layout.projectDirectory.file("dictionary/fr_bigrams.tsv"))
}

val compileFrenchDictionary by tasks.registering(CompileDictionaryTask::class) {
    group = "dictionary"
    description = "Compile dictionary/fr_*.tsv en dictionnaire binaire (assets/dictionary/fr.dict)"
    wordsFile.set(layout.projectDirectory.file("dictionary/fr_words.tsv"))
    bigramsFile.set(layout.projectDirectory.file("dictionary/fr_bigrams.tsv"))
    assetPath.set("dictionary/fr.dict")
    outputDirectory.set(layout.buildDirectory.dir("generated/dictionary"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(compileFrenchDictionary, CompileDictionaryTask::outputDirectory)
    }
}

// Les tests du moteur de suggestions tournent sur le vrai dictionnaire compilé.
// AGP choisit le dossier de sortie (addGeneratedSourceDirectory) : il n'est connu qu'à l'exécution.
tasks.withType<Test>().configureEach {
    dependsOn(compileFrenchDictionary)
    val dictionary = compileFrenchDictionary.flatMap { it.outputDirectory.file("dictionary/fr.dict") }
    doFirst { systemProperty("clavier.dictionary", dictionary.get().asFile.path) }
}

dependencies {
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
