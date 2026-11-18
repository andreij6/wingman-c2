import org.gradle.api.Plugin
import org.gradle.api.Project

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies.apply {
            add("implementation", catalog.lib("hilt-android"))
            add("ksp", catalog.lib("hilt-compiler"))
        }
    }
}
