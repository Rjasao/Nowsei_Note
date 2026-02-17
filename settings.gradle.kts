pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // ADICIONE ESTA LINHA - ESSA É A CORREÇÃO PRINCIPAL
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ADICIONE ESTA LINHA TAMBÉM - ESSA É A CORREÇÃO PRINCIPAL
        maven("https://androidx.dev/storage/compose-compiler/repository/")
    }
}

rootProject.name = "Nowsei"
include(":app")
include(":domain")
include(":data")

