package dev.ybdn.ciao.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure

/**
 * Garde-fou de la suite (docs/vision.md) : le manifeste release fusionné ne doit jamais déclarer
 * la permission INTERNET, garantie la plus forte qu'on puisse donner à l'utilisateur.
 */
abstract class CheckNoInternetPermissionTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun check() {
        val manifestFile = mergedManifest.get().asFile
        if (manifestFile.readText().contains("android.permission.INTERNET")) {
            throw GradleException(
                "Permission INTERNET détectée dans le manifeste fusionné (${manifestFile.path}) : " +
                    "aucune app de la suite C!ao ne doit avoir d'accès réseau (docs/vision.md).",
            )
        }
    }
}

/**
 * Branche la vérification sur le variant release, avant `check`, `assembleRelease` et
 * `bundleRelease`. Le manifeste vient de l'API des variants d'AGP (pas d'un chemin interne).
 */
internal fun Project.configureNoInternetPermissionGuard() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withBuildType("release")) { variant ->
            val checkTask = tasks.register("checkNoInternetPermission", CheckNoInternetPermissionTask::class.java) {
                group = "verification"
                description = "Échoue si le manifeste release fusionné déclare la permission INTERNET"
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            }

            tasks.matching { it.name in setOf("check", "assembleRelease", "bundleRelease") }
                .configureEach { dependsOn(checkTask) }
        }
    }
}
