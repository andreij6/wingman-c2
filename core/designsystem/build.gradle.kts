plugins {
    alias(libs.plugins.wingman.android.library)
    alias(libs.plugins.wingman.android.compose)
}

dependencies {
    api(projects.core.model)
}
