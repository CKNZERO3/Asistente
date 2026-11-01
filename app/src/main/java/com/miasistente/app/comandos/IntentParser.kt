package com.miasistente.app.comandos

import java.text.Normalizer

sealed interface Intencion {
    data class CrearNota(val contenido: String) : Intencion
    data class AbrirAplicacion(val nombreApp: String) : Intencion
    data class ReproducirMusica(val consulta: String) : Intencion
    data class ConsultaIA(val pregunta: String) : Intencion
}

data class ResultadoComando(val exito: Boolean, val mensaje: String)

private val MARCAS = Regex("\\p{Mn}+")

/** Minúsculas y sin acentos ("Cámara" → "camara"). */
internal fun normalizar(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(MARCAS, "").lowercase()

/** Convierte una frase en una [Intencion]. Lo que no encaje con un comando local va a Gemini. */
class IntentParser {

    private companion object {
        val CORTESIA = Regex("""^(?:(?:por favor|porfa|puedes|podrias|quiero|necesito|me puedes|me podrias)\s+)+""")
        val COLA = Regex("""\s+(?:por favor|porfa)$""", RegexOption.IGNORE_CASE)

        fun regla(patron: String, crear: (String) -> Intencion) = Regex(patron) to crear

        // El orden importa: la primera regla que encaje gana.
        val REGLAS = listOf(
            // Notas ("escribe" no captura correos ni mensajes)
            regla("""^(?:(?:crea|crear|nueva|toma|anade|agrega) (?:una )?nota|(?:guarda|guardar|anota|anotar|apunta|apuntar|recuerdame|recordarme|escribe(?!\s+(?:un |el )?(?:correo|mensaje|email)\b)|escribir)(?: una nota| nota)?)\b[:, ]*(?:(?:que|de|sobre) )?(.*)$""") {
                Intencion.CrearNota(it)
            },
            // Abrir aplicaciones
            regla("""^(?:abre(?:me)?|abrir|ejecuta(?:me)?|ejecutar|lanza)\b\s*(?:(?:la|el)\s+)?(?:(?:aplicacion|app)\s+)?(?:de\s+)?(.+)$""") {
                Intencion.AbrirAplicacion(it)
            },
            // Música ("pon" excluye alarmas, temporizadores, volumen, etc.)
            regla("""^(?:(?:pon|poner)(?:me)?(?!\s+(?:una |un |el |la )?(?:alarma|temporizador|cronometro|recordatorio|volumen|brillo|linterna|luz|modo)\b)|reproduce(?:me)?|reproducir|escuchar)\b\s*(?:(?:(?:la |el )?(?:musica|cancion|tema)|algo)\b\s*)?(?:de\s+)?(.*?)(?:\s+(?:en|con|usando)\s+musicolet)?\s*$""") {
                Intencion.ReproducirMusica(it)
            }
        )
    }

    fun parsear(texto: String): Intencion {
        val original = texto.trim()
        val norm = normalizar(original)
        // El contenido se corta del original para conservar acentos y mayúsculas
        val base = if (original.length == norm.length) original else norm
        val k = CORTESIA.find(norm)?.value?.length ?: 0
        val cuerpo = norm.substring(k)

        for ((regla, crear) in REGLAS) {
            val r = regla.find(cuerpo)?.groups?.get(1)?.range ?: continue
            return crear(limpiar(limpiar(base.substring(k + r.first, k + r.last + 1)).replace(COLA, "")))
        }
        return Intencion.ConsultaIA(original)
    }

    private fun limpiar(s: String) = s.trim(' ', '.', ',', ';', ':', '!', '?', '¿', '¡')
}
