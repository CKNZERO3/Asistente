// app/build.gradle.kts (MÓDULO APP)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Identificador del código. Debe coincidir con el "package" de los archivos .kt
    namespace = "com.miasistente.app"
    compileSdk = 35

    defaultConfig {
        // Identificador único de la app en el teléfono
        applicationId = "com.miasistente.app"
        minSdk = 26          // Android 8.0 en adelante
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Jetpack Compose: el BOM fija las versiones de todas las piezas de Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Base de Android y ciclo de vida
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Almacenamiento cifrado de la API Key de Gemini.
    // OJO: Google marcó esta librería como obsoleta en versiones posteriores (alpha07).
    // Se usa la alpha06 porque sigue funcionando; todo el acceso está aislado en
    // data/SecureStorage.kt, así que se podrá cambiar luego sin tocar el resto de la app.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
