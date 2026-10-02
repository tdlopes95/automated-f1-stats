pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MPAndroidChart is only published on JitPack
        maven("https://jitpack.io") {
            content { includeGroup("com.github.PhilJay") }
        }
    }
}

rootProject.name = "F1 Stats"
include(":app")
