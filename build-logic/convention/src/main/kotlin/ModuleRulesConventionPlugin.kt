import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Internal

/** Rejestruje `verifyModuleDependencies` w projekcie głównym i podpina pod `check`. */
class ModuleRulesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val task = tasks.register("verifyModuleDependencies") {
            group = "verification"
            description = "Sprawdza reguły zależności modułów (KONSPEKT §3.2)."
            doLast {
                val graph = rootProject.subprojects.associate { p ->
                    val deps = p.configurations
                        .filter { it.name.endsWith("Implementation") || it.name.endsWith("Api") ||
                            it.name == "implementation" || it.name == "api" }
                        .flatMap { c -> c.dependencies.filterIsInstance<org.gradle.api.artifacts.ProjectDependency>() }
                        .map { it.path }
                        .toSet()
                    p.path to deps
                }
                val violations = ModuleRules.check(graph)
                if (violations.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "Naruszone reguły zależności modułów:\n" + violations.joinToString("\n") { " - $it" },
                    )
                }
            }
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(task) }
    }
}
