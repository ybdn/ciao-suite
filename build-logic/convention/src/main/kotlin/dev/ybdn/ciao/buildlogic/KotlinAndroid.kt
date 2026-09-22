package dev.ybdn.ciao.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Niveaux d'API communs à toute la suite C!ao. */
object CiaoSdk {
    const val COMPILE = 35
    const val TARGET = 35
    const val MIN = 33
}

internal val CIAO_JAVA_VERSION = JavaVersion.VERSION_17
internal val CIAO_JVM_TARGET = JvmTarget.JVM_17

/** Configuration Android + Kotlin partagée par les apps et les bibliothèques. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    commonExtension.apply {
        compileSdk = CiaoSdk.COMPILE

        defaultConfig {
            minSdk = CiaoSdk.MIN
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = CIAO_JAVA_VERSION
            targetCompatibility = CIAO_JAVA_VERSION
        }

        // Sources Kotlin sous src/<sourceSet>/kotlin (convention de la suite).
        sourceSets {
            getByName("main") { java.srcDirs("src/main/kotlin") }
            getByName("test") { java.srcDirs("src/test/kotlin") }
            getByName("androidTest") { java.srcDirs("src/androidTest/kotlin") }
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
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
