package dev.ybdn.ciao.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import java.util.Properties

/**
 * Signature release depuis `keystore.properties` à la racine de la suite (fichier local, jamais
 * commité — voir README). Une même clé d'importation sert à toutes les apps (Play App Signing).
 * Si le fichier est absent (CI, build local sans clé), le build release reste simplement non signé.
 */
internal fun Project.configureReleaseSigning(extension: ApplicationExtension) {
    val keystoreFile = rootProject.file("keystore.properties")
    if (!keystoreFile.exists()) return

    val keystore = Properties().apply { keystoreFile.inputStream().use { load(it) } }
    extension.apply {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
