// build.gradle.kts (RAÍZ)
// Solo declara las versiones de los plugins. Cada módulo los activa por su cuenta.
//
// Versiones fijadas a propósito: son un conjunto probado que funciona junto.
// Se pueden subir más adelante, pero Gradle, AGP y Kotlin deben subir de forma compatible.

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
