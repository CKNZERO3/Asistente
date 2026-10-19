package com.miasistente.app.data

import android.content.Context
import java.util.Locale

/**
 * Ajustes sencillos del usuario (no secretos), guardados en el teléfono.
 *
 * Por ahora solo la palabra clave con la que se activa el asistente.
 * Los datos secretos (API Key de Gemini) NO van aquí: están en SecureStorage.
 */
class Ajustes(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(NOMBRE_ARCHIVO, Context.MODE_PRIVATE)

    /** Palabra (o frase corta) que activa al asistente. Siempre devuelve algo válido. */
    var palabraClave: String
        get() = normalizar(prefs.getString(CLAVE_PALABRA, null) ?: "")
            .ifEmpty { PALABRA_POR_DEFECTO }
        set(valor) {
            prefs.edit().putString(CLAVE_PALABRA, normalizar(valor)).apply()
        }

    companion object {
        const val PALABRA_POR_DEFECTO = "asistente"

        private const val NOMBRE_ARCHIVO = "asistente_ajustes"
        private const val CLAVE_PALABRA = "palabra_clave"

        /**
         * Deja el texto listo para el reconocedor: minúsculas, solo letras y espacios
         * (se conservan acentos y la ñ), sin espacios repetidos.
         *
         *   "  Hola,  ASISTENTE! "  ->  "hola asistente"
         */
        fun normalizar(texto: String): String =
            texto.lowercase(Locale.forLanguageTag("es"))
                .replace(Regex("[^\\p{L} ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
