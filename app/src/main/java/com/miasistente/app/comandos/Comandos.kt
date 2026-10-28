package com.miasistente.app.comandos

import java.text.Normalizer

sealed interface Comando {
    data class CrearNota(val texto: String) : Comando
    data class ReproducirMusica(val consulta: String) : Comando
    data class ConsultaIA(val pregunta: String) : Comando
}

data class ResultadoComando(val exito: Boolean, val mensaje: String)

object InterpreteComandos {

    private val PREFIJOS_NOTA = listOf(
        "crea una nota", "crear una nota", "crea nota", "nueva nota",
        "toma nota", "anota", "apunta", "guarda esta nota", "guarda nota", "guarda", "escribe una nota"
    )
    private val PREFIJOS_MUSICA = listOf(
        "reproduce", "reproducir", "pon musica", "pon la cancion", "pon el tema", "toca", "abre", "abrir"
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

    private fun empiezaCon(limpio: String, prefijo: String) =
        limpio.startsWith(prefijo) &&
            (limpio.length == prefijo.length || !limpio[prefijo.length].isLetter())

    private fun resto(original: String, limpio: String, prefijo: String): String =
        if (original.length == limpio.length) original.substring(prefijo.length)
        else limpio.substring(prefijo.length)

    private fun sinAcentos(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
