pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Onyx Boox pen SDK (low-latency e-ink ink)
        // (also mirrors a few old JCenter-only libraries the SDK depends on)
        maven("https://repo.boox.com/repository/maven-public/")
    }
}

rootProject.name = "PuzzleNook"
include(":app")
