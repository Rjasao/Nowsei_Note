pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Repositorio do compose compiler
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Repositorio do compose compiler
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }
}

rootProject.name = "Nowsei"
include(":app")
include(":domain")
include(":data")
