plugins {
    alias(libs.plugins.wingman.android.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.core)
}
