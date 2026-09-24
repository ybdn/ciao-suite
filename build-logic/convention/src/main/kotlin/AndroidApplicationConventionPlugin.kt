import com.android.build.api.dsl.ApplicationExtension
import dev.ybdn.ciao.buildlogic.CiaoSdk
import dev.ybdn.ciao.buildlogic.configureKotlinAndroid
import dev.ybdn.ciao.buildlogic.configureNoInternetPermissionGuard
import dev.ybdn.ciao.buildlogic.configureReleaseSigning
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Application Android de la suite : SDK communs, release minifiée (R8), signature locale. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = CiaoSdk.TARGET

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }

                configureReleaseSigning(this)
            }

            configureNoInternetPermissionGuard()
        }
    }
}
