package com.miasistente.app.comandos

import java.text.Normalizer

sealed interface Intencion {
    data class CrearNota(val contenido: String) : Intencion
    data class AbrirAplicacion(val nombreApp: String) : Intencion
    data class ReproducirMusica(val consulta: String) : Intencion
    data class ConsultaIA(val pregunta: String) : Intencion
}

/** Convierte una frase en una [Intencion]. Ignora acentos y mayúsculas; lo que no encaje va a Gemini. */
class IntentParser {

    private val reglas: List<Pair<Regex, (String) -> Intencion>> = listOf(
        Regex("""^(?:(?:crea|crear|nueva|toma) (?:una )?nota|(?:guarda|guardar|anota|anotar|apunta|escribe|recuerdame)(?: una nota| nota)?)\b[:, ]*(?:(?:que|de|sobre) )?(.*)$""")
            to { Intencion.CrearNota(it) },
        Regex("""^(?:abre|abrir|ejecuta|ejecutar)\b\s*(?:(?:la|el)\s+)?(?:(?:aplicacion|app)\s+)?(?:de\s+)?(.+)$""")
            to { Intencion.AbrirAplicacion(it) },
        Regex("""^(?:pon|poner|reproduce|reproducir|escucha|escuchar)\b\s*(?:(?:(?:la |el )?(?:musica|cancion|tema)|algo)\b\s*)?(?:de\s+)?(.*?)(?:\s+(?:en|con|usando)\s+musicolet)?\s*$""")
            to { Intencion.ReproducirMusica(it) }
    )

    fun parsear(texto: String): Intencion {
        val original = texto.trim()
        val norm = Normalizer.normalize(original, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase()
        // Se extrae del texto original para conservar acentos y mayúsculas ("cámara", "Soda Stereo")
        val base = if (original.length == norm.length) original else norm

        for ((regla, crear) in reglas) {
            val grupo = regla.find(norm)?.groups?.get(1)?.range ?: continue
            return crear(base.substring(grupo.first, grupo.last + 1).trim())
        }
        return Intencion.ConsultaIA(original)
    }
}
