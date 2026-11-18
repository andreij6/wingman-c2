plugins {
    alias(libs.plugins.wingman.jvm.library)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.algorithms)
    implementation(projects.core.domain)
}
