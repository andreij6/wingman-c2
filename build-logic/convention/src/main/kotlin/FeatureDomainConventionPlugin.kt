import org.gradle.api.Plugin
import org.gradle.api.Project

class FeatureDomainConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("wingman.jvm.library")
        dependencies.apply {
            add("api", project(":core:model"))
            add("api", project(":core:domain"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:algorithms"))
        }
    }
}
