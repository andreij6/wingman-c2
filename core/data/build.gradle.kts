plugins {
    alias(libs.plugins.wingman.android.library)
    alias(libs.plugins.wingman.hilt)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.algorithms)
    implementation(projects.core.database)
    implementation(projects.core.simulation)
    implementation(libs.objectbox.kotlin)
    implementation(libs.kotlinx.coroutines.android)
}
