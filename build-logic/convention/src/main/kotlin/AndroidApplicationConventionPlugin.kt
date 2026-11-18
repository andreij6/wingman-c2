import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = Sdk.COMPILE
            defaultConfig {
                minSdk = Sdk.MIN
                targetSdk = Sdk.TARGET
            }
            compileOptions {
                sourceCompatibility = Sdk.JAVA
                targetCompatibility = Sdk.JAVA
            }
            buildFeatures {
                buildConfig = false
                aidl = false
                shaders = false
                resValues = false
            }
            packaging {
                resources.excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/*.version",
                    "/META-INF/**/LICENSE*",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                )
            }
        }
        configureKotlin()
    }
}
