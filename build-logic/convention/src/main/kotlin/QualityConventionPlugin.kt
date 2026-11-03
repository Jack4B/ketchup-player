import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Detekt (zakaz pustych catch, §11) + Spotless/ktlint. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("com.diffplug.spotless")
        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = false
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            parallel = true
        }
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(libs.version("ktlint"))
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(libs.version("ktlint"))
            }
        }
    }
}
