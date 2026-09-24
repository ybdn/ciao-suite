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
