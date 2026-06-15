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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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

    // Coil para carga de imágenes optimizada
    implementation(libs.coil.compose)

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

// Configuración de Dokka para una documentación técnica limpia y profesional
dokka {
    dokkaPublications.html {
        moduleName.set("Launcher Orbys Core")
        
        // Incluimos una descripción general del proyecto desde el README
        includes.from(project.layout.projectDirectory.file("../README.md"))
        
        // Evita mostrar archivos generados por Android/Kotlin en la documentación
        failOnWarning.set(false)
    }

    dokkaSourceSets.configureEach {
        // Nombre del conjunto de fuentes
        displayName.set("Android Main")
        
        // Suprimir archivos generados automáticamente (R, BuildKonfig, etc)
        suppressGeneratedFiles.set(true)
        
        // Documentar miembros privados si se desea una doc técnica completa interna
        // reportUndocumented.set(true) 
    }
}
