package dev.ybdn.ciao.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

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

/** Branche la vérification sur le variant release, avant `check`, `assembleRelease` et `bundleRelease`. */
internal fun Project.configureNoInternetPermissionGuard() {
    val checkTask = tasks.register("checkNoInternetPermission", CheckNoInternetPermissionTask::class.java) {
        group = "verification"
        description = "Échoue si le manifeste release fusionné déclare la permission INTERNET"
        mergedManifest.set(
            layout.buildDirectory.file(
                "intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml",
            ),
        )
        dependsOn("processReleaseMainManifest")
    }

    tasks.matching { it.name in setOf("check", "assembleRelease", "bundleRelease") }
        .configureEach { dependsOn(checkTask) }
}
