package dev.ybdn.ciao.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Niveaux d'API communs à toute la suite C!ao. */
object CiaoSdk {
    // Compose (BOM 2026.09) exige de compiler contre l'API 37 ; la cible reste un choix distinct.
    const val COMPILE = 37
    const val TARGET = 37
    const val MIN = 33
}

internal val CIAO_JAVA_VERSION = JavaVersion.VERSION_17
internal val CIAO_JVM_TARGET = JvmTarget.JVM_17

/**
 * Configuration Android + Kotlin partagée par les apps et les bibliothèques. Kotlin est intégré à
 * AGP depuis la 9.0 : les sources de src/<sourceSet>/kotlin sont prises en compte d'office.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.compileSdk = CiaoSdk.COMPILE

    commonExtension.defaultConfig.apply {
        minSdk = CiaoSdk.MIN
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    commonExtension.compileOptions.apply {
        sourceCompatibility = CIAO_JAVA_VERSION
        targetCompatibility = CIAO_JAVA_VERSION
    }

    commonExtension.packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(CIAO_JVM_TARGET)
        }
    }
}

/** Configuration Kotlin/JVM pure (modules sans dépendance Android). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        sourceCompatibility = CIAO_JAVA_VERSION
        targetCompatibility = CIAO_JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(CIAO_JVM_TARGET)
        }
    }
}
