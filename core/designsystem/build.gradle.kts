plugins {
    alias(libs.plugins.ciao.android.library)
    alias(libs.plugins.ciao.android.compose)
}

android {
    namespace = "dev.ybdn.ciao.designsystem"
}

dependencies {
    implementation(libs.androidx.material.icons.extended)
}
