plugins {
    alias(libs.plugins.wingman.android.application)
    alias(libs.plugins.wingman.android.compose)
    alias(libs.plugins.wingman.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wingmanc2"
    defaultConfig {
        applicationId = "com.wingmanc2"
        versionCode = 1
        versionName = "1.0.0"
    }
    androidResources {
        localeFilters += "en"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.navigation)
    implementation(projects.feature.battlespace.presentation)
    implementation(projects.feature.intel.presentation)
    implementation(projects.feature.plan.presentation)
    implementation(projects.feature.mission.presentation)
    implementation(projects.feature.sync.presentation)

    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
}
