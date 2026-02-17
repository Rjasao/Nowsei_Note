// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Plugin de Aplicação Android
    alias(libs.plugins.android.application) version "8.5.0" apply false // Recomendo adicionar a versão aqui também

    // Plugin de Biblioteca Android
    alias(libs.plugins.android.library) version "8.5.0" apply false // Recomendo adicionar a versão aqui também

    // Plugin do Kotlin para Android - CORREÇÃO PRINCIPAL
    alias(libs.plugins.kotlin.android) version "1.9.24" apply false

    // Plugin do Kotlin para JVM (usado no :domain)
    alias(libs.plugins.jetbrains.kotlin.jvm) version "1.9.24" apply false

    // Plugin do Hilt (Dagger) - ESSENCIAL
    alias(libs.plugins.hilt) version "2.51.1" apply false

    // Plugin do KSP - ESSENCIAL - CORREÇÃO PRINCIPAL
    // A versão do KSP é atrelada à do Kotlin: [versão_kotlin]-[versão_ksp]
    alias(libs.plugins.ksp) version "1.9.24-1.0.20" apply false
}
