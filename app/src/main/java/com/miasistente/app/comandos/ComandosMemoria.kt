package com.miasistente.app.comandos

/** Órdenes sobre la memoria de Iris que se resuelven sin red. */
sealed interface AccionMemoria {
    /** "Recuerda que mi color favorito es el azul". */
    data class Recordar(val hecho: String) : AccionMemoria

    /** "¿Qué recuerdas?" / "¿Qué recuerdas de mi color favorito?" ([tema] null = todo). */
    data class Consultar(val tema: String?) : AccionMemoria

    /** "No se guardó": seguimiento de la última nota. */
    data object NoSeGuardo : AccionMemoria
}

object ComandosMemoria {

    private val RECORDAR = Regex("""^(?:recuerda|memoriza|aprende|ten en cuenta|toma en cuenta)\s+que\s+(.+)$""")
    private val CONSULTAR = Regex(
        """^(?:que|cuales cosas|cuanto)\s+(?:recuerdas|sabes de mi|sabes sobre mi|tienes guardado|has guardado|me recuerdas)""" +
            """(?:\s+(?:de|sobre|acerca de)\s+(.+))?$"""
    )
    private val NO_SE_GUARDO = Regex(
        """^(?:pero\s+|oye\s+)?(?:no\s+(?:se\s+|me\s+|lo\s+|la\s+)?(?:guardo|guardaste|grabo|aparece|salio|funciono|quedo guardad[oa])""" +
            """|no esta guardad[oa]|no la veo|no lo veo)\b"""
    )

    fun interpretar(frase: String): AccionMemoria? {
        val original = frase.trim()
        val norm = normalizar(original).trim(' ', '.', ',', ';', ':', '!', '?', '¿', '¡')
        // Se corta del original (sin signos iniciales) para conservar acentos y mayúsculas.
        val base = original.trimStart('¿', '¡', ' ').let { if (it.length >= norm.length) it else norm }

        if (NO_SE_GUARDO.containsMatchIn(norm)) return AccionMemoria.NoSeGuardo

        RECORDAR.find(norm)?.let { m ->
            val r = m.groups[1]!!.range
            val hecho = if (base.length >= r.last + 1) base.substring(r.first, r.last + 1) else m.groupValues[1]
            return AccionMemoria.Recordar(ExtractorNota.limpiar(hecho).replaceFirstChar { it.uppercase() })
        }
        CONSULTAR.find(norm)?.let { m ->
            val tema = m.groups[1]?.value?.trim()?.takeIf { it.isNotEmpty() && it != "mi" }
            return AccionMemoria.Consultar(tema)
        }
        return null
    }
}
