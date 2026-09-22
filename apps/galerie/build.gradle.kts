plugins {
    alias(libs.plugins.ciao.android.application)
    alias(libs.plugins.ciao.android.compose)
    alias(libs.plugins.ksp)
}

android {
    // Identifiants historiques (CiaoCloud) conservés tant que le renommage n'est pas décidé :
    // changer l'applicationId crée une nouvelle app (base Room, DataStore et accès SAF perdus).
    namespace = "dev.ybdn.ciaocloud"

    defaultConfig {
        applicationId = "dev.ybdn.ciaocloud"
        versionCode = 1
        versionName = "0.1.0"
    }
}

ksp {
    // Schémas Room versionnés : base des migrations explicites (jamais de migration destructive).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)

    // Galerie : aucune de ces bibliothèques ne tire de module réseau.
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
