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
    }
}

rootProject.name = "AIControlCentreAnroid"
rootProject.name = "AIControlCentreAndroid"

include(
    ":app",
    ":core-model",
    ":core-ui",
    ":data-storage",
    ":data-network",
    ":feature-setup",
    ":feature-projects",
    ":feature-chat",
    ":feature-compare",
    ":feature-settings"
)
 
