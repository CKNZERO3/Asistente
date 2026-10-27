package com.miasistente.app.data

import android.content.Context
import java.util.Locale

/**
 * Ajustes sencillos del usuario (no secretos), guardados en el teléfono.
 *
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

    /**
     * Avatar elegido: "preset:0", "preset:1"… (predeterminados) o "custom" (imagen propia,
     * guardada por AvatarRepositorio).
     */
    var avatar: String
        get() = prefs.getString(CLAVE_AVATAR, null) ?: AVATAR_POR_DEFECTO
        set(valor) {
            prefs.edit().putString(CLAVE_AVATAR, valor).apply()
        }

    companion object {
        const val PALABRA_POR_DEFECTO = "asistente"
        const val AVATAR_POR_DEFECTO = "preset:0"
        const val AVATAR_PERSONALIZADO = "custom"

        private const val NOMBRE_ARCHIVO = "asistente_ajustes"
        private const val CLAVE_PALABRA = "palabra_clave"
        private const val CLAVE_AVATAR = "avatar"

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
