import org.gradle.api.Plugin
import org.gradle.api.Project

class FeaturePresentationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("wingman.android.library")
        pluginManager.apply("wingman.android.compose")
        pluginManager.apply("wingman.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        val feature = path.removePrefix(":feature:").substringBefore(':')
        dependencies.apply {
            add("implementation", project(":feature:$feature:domain"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:navigation"))
            add("implementation", catalog.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", catalog.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", catalog.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", catalog.lib("androidx-navigation3-runtime"))
            add("implementation", catalog.lib("kotlinx-coroutines-android"))
        }
    }
}
