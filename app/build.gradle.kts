plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
}

android {
    namespace = "com.example.launcherorbys"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.launcherorbys"
        minSdk = 24 // Compatible desde Android 7.0 Nougat
        targetSdk = 35
        versionCode = 5
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
//        debug{
//            isMinifyEnabled = true
//            isShrinkResources = true
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
//        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core y ciclo de vida
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.runtime)
    
    // UI tradicional y utilidades
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Pruebas
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Dokka
    dokkaPlugin(libs.dokka.android.documentation)
}

dokka {
    dokkaPublications.html {
        moduleName.set("LauncherOrbys")
        // Incluimos una descripción general del proyecto desde el README
        includes.from(project.layout.projectDirectory.file("../README.md"))
        
        // Configuramos para que no falle si falta alguna documentación, pero avise
        failOnWarning.set(false)
    }
    dokkaSourceSets.configureEach {
        suppressGeneratedFiles.set(true)
        
        // Dokka 2.2.0 ya configura automáticamente links para SDK y Kotlin.
        // Si tuvieras bibliotecas extra, las registrarías en dokkaPublications.html
    }
}
