plugins {
    `kotlin-dsl`
}

group = "com.wingmanc2.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") { id = "wingman.android.application"; implementationClass = "AndroidApplicationConventionPlugin" }
        register("androidLibrary") { id = "wingman.android.library"; implementationClass = "AndroidLibraryConventionPlugin" }
        register("androidCompose") { id = "wingman.android.compose"; implementationClass = "AndroidComposeConventionPlugin" }
        register("hilt") { id = "wingman.hilt"; implementationClass = "HiltConventionPlugin" }
        register("jvmLibrary") { id = "wingman.jvm.library"; implementationClass = "JvmLibraryConventionPlugin" }
        register("featureDomain") { id = "wingman.feature.domain"; implementationClass = "FeatureDomainConventionPlugin" }
        register("featurePresentation") { id = "wingman.feature.presentation"; implementationClass = "FeaturePresentationConventionPlugin" }
    }
}
