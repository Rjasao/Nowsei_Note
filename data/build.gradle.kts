plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.rjasao.nowsei.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "/META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "/META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "/META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "/META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(project(":domain"))

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // AndroidX & Coroutines
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.core)

    // WorkManager - ✅ AÇÃO: Adicione esta linha para o WorkManager
    // A versão 2.9.0 é a versão estável mais recente e recomendada.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Google APIs & Auth
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:1.34.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")
    implementation("com.google.http-client:google-http-client-gson:1.44.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // Gson (Esta linha pode ser removida pois a de cima já a declara)
    // implementation(libs.gson)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
