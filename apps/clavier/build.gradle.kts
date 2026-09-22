plugins {
    alias(libs.plugins.ciao.android.application)
    alias(libs.plugins.ciao.android.compose)
}

android {
    namespace = "dev.ybdn.ciao.clavier"

    defaultConfig {
        applicationId = "dev.ybdn.ciao.clavier"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.savedstate)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
