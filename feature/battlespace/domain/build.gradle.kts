plugins {
    alias(libs.plugins.wingman.feature.domain)
}

dependencies {
    implementation(projects.core.algorithms)
    implementation(projects.core.common)
}
