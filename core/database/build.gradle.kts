plugins {
    alias(libs.plugins.wingman.android.library)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.objectbox)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.algorithms)
    implementation(libs.objectbox.kotlin)
    kapt(libs.objectbox.processor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.javax.inject)
}

tasks.matching { it.name.startsWith("objectbox") }.configureEach {
    notCompatibleWithConfigurationCache("ObjectBox Gradle plugin captures Project")
}
