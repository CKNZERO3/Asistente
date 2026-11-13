package com.miasistente.app.comandos

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.DayOfWeek
import java.time.Month
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** El usuario pidió el clima; [ciudad] es null si no dijo de dónde. */
data class PeticionClima(val ciudad: String?)

/**
 * Respuestas que no necesitan ninguna IA: hora, fecha, cálculos y detección de preguntas
 * de clima. Funcionan sin internet y no gastan cuota de Gemini.
 */
class HabilidadesLocales(private val reloj: Clock = Clock.systemDefaultZone()) {

    private companion object {
        val ES: Locale = Locale.forLanguageTag("es")

        val CORTESIA_INICIAL = Regex(
            """^(?:(?:por favor|porfa|puedes|podrias|me puedes|me podrias|decirme|dime|dame|me dices|quiero saber|quisiera saber|necesito saber)\s+)+"""
        )
        val CORTESIA_FINAL = Regex("""\s+(?:por favor|porfa)$""")

        val HORA = Regex("""^(?:que hora (?:es|son)|la hora|hora actual)(?: actual| ahora| ahora mismo)?$""")
        val FECHA = Regex(
            """^(?:que dia es|que fecha es|cual es la fecha|la fecha|a cuantos estamos|en que dia estamos)(?: de hoy| hoy| actual)?$"""
        )

        val PREFIJO_CALCULO = Regex(
            """^(?:cuanto es|cuanto son|cuanto da|cuanto seria|calcula|calcular|resuelve|resolver|cual es el resultado de)\s+"""
        )
        val PORCENTAJE = Regex(
            """^(?:cuanto es )?(?:el )?(\d+(?:[.,]\d+)?)\s*(?:%|por ciento) de (\d+(?:[.,]\d+)?)$"""
        )
        val SOLO_EXPRESION = Regex("""^[0-9.+\-*/() ]+$""")
        val MILES = Regex("""(?<![\d.])\d{1,3}(?:\.\d{3})+(?![\d.])""")

        const val ARTICULO = """(?:(?:el|la)\s+)?"""
        val CLIMA_CON_CIUDAD = Regex(
            """^(?:(?:que|como|cual)\s+(?:es\s+)?(?:esta\s+)?)?${ARTICULO}(?:clima|tiempo|temperatura|pronostico)""" +
                """(?:\s+(?:que\s+)?(?:hace|hara|hay|va a hacer|es|esta))?(?:\s+(?:hoy|ahora|actualmente))?\s+(?:en|de|para)\s+(.+)$"""
        )
        val CLIMA_SIN_CIUDAD = Regex(
            """^(?:(?:que|como|cual)\s+(?:es\s+)?(?:esta\s+)?)?${ARTICULO}(?:clima|tiempo|temperatura|pronostico)""" +
                """(?:\s+(?:que\s+)?(?:hace|hara|hay))?(?:\s+(?:hoy|ahora|actualmente))?$"""
        )
        val LLUVIA = Regex("""^(?:va a llover|llovera|esta lloviendo|llueve)(?:\s+(?:hoy|ahora))?\s+en\s+(.+)$""")
        val CIUDAD_PREFIJO = Regex("""^(?:hoy|ahora|actualmente)\s+(?:en\s+)?""")
        val CIUDAD_SUFIJO = Regex("""\s+(?:hoy|ahora|actualmente)$""")
    }

    private fun limpiar(texto: String): String =
        normalizar(texto)
            .trim(' ', '.', ',', ';', ':', '!', '?', '¿', '¡')
            .replace(Regex("""\s+"""), " ")
            .replace(CORTESIA_FINAL, "")
            .replace(CORTESIA_INICIAL, "")
            .trim()

    /** Hora, fecha o cálculo. Devuelve null si la frase no es de esta clase. */
    fun responder(texto: String): String? {
        val t = limpiar(texto)
        if (t.isEmpty()) return null
        return hora(t) ?: fecha(t) ?: calculo(t)
    }

    /** Detecta preguntas de clima (de hoy). "mañana" se deja pasar a Gemini. */
    fun peticionClima(texto: String): PeticionClima? {
        val t = limpiar(texto)
        if (t.isEmpty() || Regex("""\bmanana\b""").containsMatchIn(t)) return null

        val ciudad = CLIMA_CON_CIUDAD.find(t)?.groupValues?.get(1)
            ?: LLUVIA.find(t)?.groupValues?.get(1)
        if (ciudad != null) {
            val limpia = ciudad.replace(CIUDAD_PREFIJO, "").replace(CIUDAD_SUFIJO, "").trim()
            return PeticionClima(limpia.ifEmpty { null })
        }
        return if (CLIMA_SIN_CIUDAD.matches(t)) PeticionClima(null) else null
    }

    // --- Hora y fecha ---------------------------------------------------------------------

    private fun hora(t: String): String? {
        if (!HORA.matches(t)) return null
        val ahora = ZonedDateTime.now(reloj)
        val h24 = ahora.hour
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val periodo = when {
            h24 < 6 -> "de la madrugada"
            h24 < 12 -> "de la mañana"
            h24 < 19 -> "de la tarde"
            else -> "de la noche"
        }
        val minutos = ahora.minute.toString().padStart(2, '0')
        val inicio = if (h12 == 1) "Es la" else "Son las"
        return "$inicio $h12:$minutos $periodo."
    }

    private fun fecha(t: String): String? {
        if (!FECHA.matches(t)) return null
        val hoy = ZonedDateTime.now(reloj)
        val dia: DayOfWeek = hoy.dayOfWeek
        val mes: Month = hoy.month
        return "Hoy es ${dia.getDisplayName(TextStyle.FULL, ES)}, ${hoy.dayOfMonth} de " +
            "${mes.getDisplayName(TextStyle.FULL, ES)} de ${hoy.year}."
    }

    // --- Cálculos -------------------------------------------------------------------------

    private fun calculo(t: String): String? {
        PORCENTAJE.matchEntire(t)?.let { m ->
            val p = m.groupValues[1].replace(',', '.').toDouble()
            val base = m.groupValues[2].replace(',', '.').toDouble()
            return "El ${formatear(p)} % de ${formatear(base)} es ${formatear(p * base / 100)}."
        }

        val tienePrefijo = PREFIJO_CALCULO.containsMatchIn(t)
        var e = t.replace(PREFIJO_CALCULO, "")
        var hayPalabraOperador = false
        fun palabra(patron: String, simbolo: String) {
            val r = Regex(patron)
            if (r.containsMatchIn(e)) {
                hayPalabraOperador = true
                e = e.replace(r, " $simbolo ")
            }
        }
        palabra("""\b(?:multiplicado por|por)\b""", "*")
        palabra("""\b(?:dividido entre|dividido por|entre)\b""", "/")
        palabra("""\bmas\b""", "+")
        palabra("""\bmenos\b""", "-")
        e = e.replace(Regex("""(?<=\d)\s*x\s*(?=\d)"""), " * ")
        e = e.replace(MILES) { it.value.replace(".", "") }
        e = e.replace(Regex("""(?<=\d),(?=\d)"""), ".").trim()

        if (!SOLO_EXPRESION.matches(e)) return null
        val tieneOperadorSimbolo = e.any { it in "+*/" }
        if (!(tienePrefijo || hayPalabraOperador || tieneOperadorSimbolo)) return null

        return try {
            val valor = Evaluador(e).evaluar() ?: return null
            "El resultado es ${formatear(valor)}."
        } catch (_: ArithmeticException) {
            "No se puede dividir entre cero."
        }
    }

    private fun formatear(valor: Double): String {
        if (valor.isNaN() || valor.isInfinite()) throw ArithmeticException()
        return BigDecimal(valor).setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString().replace('.', ',')
    }

    /** Analizador de + - * / y paréntesis. Devuelve null si la expresión no es válida. */
    private class Evaluador(private val s: String) {
        private var i = 0

        fun evaluar(): Double? = try {
            val v = expresion()
            saltar()
            if (i == s.length) v else null
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun saltar() { while (i < s.length && s[i] == ' ') i++ }

        private fun expresion(): Double {
            var v = termino()
            while (true) {
                saltar()
                when (if (i < s.length) s[i] else ' ') {
                    '+' -> { i++; v += termino() }
                    '-' -> { i++; v -= termino() }
                    else -> return v
                }
            }
        }

        private fun termino(): Double {
            var v = factor()
            while (true) {
                saltar()
                when (if (i < s.length) s[i] else ' ') {
                    '*' -> { i++; v *= factor() }
                    '/' -> {
                        i++
                        val d = factor()
                        if (d == 0.0) throw ArithmeticException()
                        v /= d
                    }
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            saltar()
            require(i < s.length)
            return when (s[i]) {
                '-' -> { i++; -factor() }
                '+' -> { i++; factor() }
                '(' -> {
                    i++
                    val v = expresion()
                    saltar()
                    require(i < s.length && s[i] == ')')
                    i++
                    v
                }
                else -> numero()
            }
        }

        private fun numero(): Double {
            val inicio = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > inicio)
            return s.substring(inicio, i).toDouble() // NumberFormatException es IllegalArgumentException
        }
    }
}
