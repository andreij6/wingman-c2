plugins {
    alias(libs.plugins.wingman.android.library)
    alias(libs.plugins.wingman.android.compose)
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
    implementation(projects.core.algorithms)
}
