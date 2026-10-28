package com.miasistente.app.audio

import kotlin.math.log10
import kotlin.math.sqrt

data class ResultadoVoz(
    val nivelDb: Float,
    val ruidoDb: Float,
    val hayVoz: Boolean,
    val calibrando: Boolean
)

class VoiceDetector(
    private val margenSobreRuidoDb: Float = 6f,        // Más sensible a tonos suaves
    private val umbralMinimoDb: Float = -55f,          // Capta susurros o voces lejanas
    private val framesParaIniciarVoz: Int = 2,         // Respuesta ultra rápida (~64 ms)
    private val framesParaTerminarVoz: Int = 22,       // Pausa cómoda (~700 ms) antes de cortar
    private val framesCalibracion: Int = 20,
    private val framesMaximoVoz: Int = 937             // Hasta 30 segundos de comando continuo
) {

    private companion object {
        const val NIVEL_SILENCIO_DB = -90f
        const val VELOCIDAD_BAJADA_RUIDO = 0.3f
        const val VELOCIDAD_SUBIDA_RUIDO = 0.02f
    }

    private var ruidoDb = NIVEL_SILENCIO_DB
    private var framesVistos = 0
    private var framesConVozSeguidos = 0
    private var framesSinVozSeguidos = 0
    private var framesEnVoz = 0
    private var enVoz = false

    fun reiniciar() {
        ruidoDb = NIVEL_SILENCIO_DB
        framesVistos = 0
        framesConVozSeguidos = 0
        framesSinVozSeguidos = 0
        framesEnVoz = 0
        enVoz = false
    }

    fun procesar(muestras: ShortArray, cantidad: Int): ResultadoVoz {
        val nivel = calcularNivelDb(muestras, cantidad)
        framesVistos++

        if (framesVistos <= framesCalibracion) {
            ruidoDb += (nivel - ruidoDb) / framesVistos
            return ResultadoVoz(nivel, ruidoDb, hayVoz = false, calibrando = true)
        }

        val umbral = maxOf(ruidoDb + margenSobreRuidoDb, umbralMinimoDb)
        val frameConVoz = nivel > umbral

        if (frameConVoz) {
            framesConVozSeguidos++
            framesSinVozSeguidos = 0
        } else {
            framesSinVozSeguidos++
            framesConVozSeguidos = 0
        }

        if (!enVoz && framesConVozSeguidos >= framesParaIniciarVoz) {
            enVoz = true
            framesEnVoz = 0
        } else if (enVoz && framesSinVozSeguidos >= framesParaTerminarVoz) {
            enVoz = false
        }

        if (enVoz) {
            framesEnVoz++
            if (framesEnVoz > framesMaximoVoz) {
                ruidoDb = nivel
                enVoz = false
                framesConVozSeguidos = 0
                framesSinVozSeguidos = 0
                framesEnVoz = 0
            }
        } else if (!frameConVoz) {
            val velocidad = if (nivel < ruidoDb) VELOCIDAD_BAJADA_RUIDO else VELOCIDAD_SUBIDA_RUIDO
            ruidoDb += velocidad * (nivel - ruidoDb)
        }

        return ResultadoVoz(nivel, ruidoDb, hayVoz = enVoz, calibrando = false)
    }

    private fun calcularNivelDb(muestras: ShortArray, cantidad: Int): Float {
        if (cantidad <= 0) return NIVEL_SILENCIO_DB
        var sumaCuadrados = 0L
        for (i in 0 until cantidad) {
            val s = muestras[i].toInt()
            sumaCuadrados += (s * s).toLong()
        }
        val rms = sqrt(sumaCuadrados.toDouble() / cantidad)
        val db = 20.0 * log10(rms / 32768.0)
        return db.coerceAtLeast(NIVEL_SILENCIO_DB.toDouble()).toFloat()
    }
}
