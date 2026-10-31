import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("ketchup.quality")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            namespace = androidNamespace()
        }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
