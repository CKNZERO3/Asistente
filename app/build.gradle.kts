plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.miasistente.app"
    compileSdk = 35

    // ---------------------------------------------------------------------------------
    // Firma fija: TODAS las compilaciones (debug y release) se firman con la misma clave.
    // Android solo deja actualizar una app encima de otra si la firma es idéntica.
    //
    // GitHub Actions guarda el keystore en un archivo temporal y exporta su ruta en
    // RELEASE_KEYSTORE_PATH. Si esa variable no existe (por ejemplo, en un fork), se usa
    // la clave debug normal y la compilación sigue funcionando igual que antes.
    // ---------------------------------------------------------------------------------
    val rutaKeystore: String? = System.getenv("RELEASE_KEYSTORE_PATH")
    val hayFirmaFija = !rutaKeystore.isNullOrBlank()

    signingConfigs {
        if (hayFirmaFija) {
            create("iris") {
                storeFile = file(rutaKeystore!!)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.miasistente.app"
        minSdk = 26
        targetSdk = 35

        // En CI: versionCode = número de commits (solo crece, nunca baja) y versionName
        // viene de la etiqueta de la release. Sin esas variables se usan estos valores.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 3
        versionName = System.getenv("VERSION_NAME") ?: "0.3.0"

        // Token de app para el Worker (lo pone GitHub Actions desde el secreto APP_TOKEN).
        // Solo letras y números. Sin la variable queda vacío y la app no envía la cabecera.
        buildConfigField(
            "String",
            "APP_TOKEN",
            "\"" + (System.getenv("APP_TOKEN") ?: "").filter { it.isLetterOrDigit() } + "\""
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        getByName("debug") {
            if (hayFirmaFija) signingConfig = signingConfigs.getByName("iris")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hayFirmaFija) signingConfig = signingConfigs.getByName("iris")
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
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
        buildConfig = true
    }
}

dependencies {

    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation("androidx.compose.material:material-icons-extended")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Android core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // IA local (modelo pequeño que corre en el teléfono). Versión fija verificada contra el código oficial.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Voice recognition
    implementation("com.alphacephei:vosk-android:0.3.47@aar") {
        isTransitive = true
    }
    implementation("net.java.dev.jna:jna:5.13.0@aar")


    // Unit testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.google.truth:truth:1.4.4")
}
