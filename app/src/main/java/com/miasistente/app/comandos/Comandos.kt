package com.miasistente.app.comandos

import java.text.Normalizer

/** Lo que el usuario puede pedir. Para añadir una capacidad nueva: un caso aquí + su ejecución. */
sealed interface Comando {
    /** Tarea local (sin internet): guardar una nota dentro de la app. */
    data class CrearNota(val texto: String) : Comando

    /** Tarea local (sin internet): reproducir música en Musicolet. */
    data class ReproducirMusica(val consulta: String) : Comando

    /** Todo lo demás: se manda a Gemini (necesita internet y API Key). */
    data class ConsultaIA(val pregunta: String) : Comando
}

/** Resultado de ejecutar un comando, listo para mostrar al usuario. */
data class ResultadoComando(val exito: Boolean, val mensaje: String)

/**
 * Convierte la frase del usuario en un [Comando], con reglas simples y sin internet.
 * Lo que no encaje con una regla local va a Gemini.
 */
object InterpreteComandos {

    private val PREFIJOS_NOTA = listOf(
        "crea una nota", "crear una nota", "crea nota", "nueva nota",
        "toma nota", "anota", "apunta"
    )
    private val PREFIJOS_MUSICA = listOf(
        "reproduce", "reproducir", "pon musica", "pon la cancion", "pon el tema", "toca"
    )

    private val RELLENO_NOTA = Regex("^(que|de|sobre)\\s+", RegexOption.IGNORE_CASE)
    private val RELLENO_MUSICA =
        Regex("^((m[uú]sica|la canci[oó]n|el tema|algo de|de)\\b\\s*)+", RegexOption.IGNORE_CASE)
    private val SUFIJO_MUSICOLET =
        Regex("\\s+(en|con|usando)\\s+musicolet\\s*$", RegexOption.IGNORE_CASE)

    fun interpretar(texto: String): Comando {
        val original = texto.trim()
        val limpio = sinAcentos(original).lowercase()

        for (prefijo in PREFIJOS_NOTA) {
            if (empiezaCon(limpio, prefijo)) {
                val resto = resto(original, limpio, prefijo)
                    .trimStart(':', ',', ' ')
                    .replace(RELLENO_NOTA, "")
                    .trim()
                return Comando.CrearNota(resto)
            }
        }

        for (prefijo in PREFIJOS_MUSICA) {
            if (empiezaCon(limpio, prefijo)) {
                val resto = resto(original, limpio, prefijo)
                    .trim()
                    .replace(SUFIJO_MUSICOLET, "")
                    .replace(RELLENO_MUSICA, "")
                    .trim()
                return Comando.ReproducirMusica(resto)
            }
        }

        return Comando.ConsultaIA(original)
    }

    /** true si el texto empieza con el prefijo como palabra completa ("anota" sí, "anotación" no). */
    private fun empiezaCon(limpio: String, prefijo: String) =
        limpio.startsWith(prefijo) &&
            (limpio.length == prefijo.length || !limpio[prefijo.length].isLetter())

    /** Texto que sigue al prefijo, conservando acentos y mayúsculas del original. */
    private fun resto(original: String, limpio: String, prefijo: String): String =
        if (original.length == limpio.length) original.substring(prefijo.length)
        else limpio.substring(prefijo.length)

    private fun sinAcentos(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
