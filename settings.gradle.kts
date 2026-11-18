pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "wingman-c2"

include(":app")
include(
    ":core:model", ":core:common", ":core:algorithms", ":core:domain", ":core:simulation",
    ":core:database", ":core:data", ":core:designsystem", ":core:ui", ":core:navigation",
)
listOf("battlespace", "intel", "plan", "mission", "sync").forEach { feature ->
    include(":feature:$feature:domain", ":feature:$feature:presentation")
}
