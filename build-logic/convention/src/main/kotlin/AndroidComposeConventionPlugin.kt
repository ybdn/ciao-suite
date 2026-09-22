import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * Jetpack Compose + Material 3. À appliquer après `ciao.android.application` ou
 * `ciao.android.library`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val android = extensions.getByType(CommonExtension::class.java)
            android.buildFeatures.compose = true

            // TopAppBar & co. sont expérimentaux : opt-in global plutôt qu'écran par écran.
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            fun lib(alias: String) = libs.findLibrary(alias).get()

            dependencies {
                val bom = platform(lib("androidx-compose-bom"))
                add("implementation", bom)
                add("implementation", lib("androidx-ui"))
                add("implementation", lib("androidx-ui-graphics"))
                add("implementation", lib("androidx-ui-tooling-preview"))
                add("implementation", lib("androidx-material3"))
                add("debugImplementation", lib("androidx-ui-tooling"))
                add("debugImplementation", lib("androidx-ui-test-manifest"))
                add("androidTestImplementation", bom)
                add("androidTestImplementation", lib("androidx-ui-test-junit4"))
            }
        }
    }
}
