package com.miasistente.app.audio

import kotlin.math.log10
import kotlin.math.sqrt

/** Resultado de analizar un frame de audio. */
data class ResultadoVoz(
    /** Volumen del frame en dBFS: -90 = silencio total, 0 = máximo posible. Voz normal: ≈ -35 a -15. */
    val nivelDb: Float,
    /** Nivel de ruido de fondo estimado, en dBFS. */
    val ruidoDb: Float,
    /** true mientras se considera que alguien está hablando. */
    val hayVoz: Boolean,
    /** true durante el primer segundo, mientras mide el ruido ambiente. */
    val calibrando: Boolean
)

/**
 * Detector de voz básico y 100 % local, basado en energía (volumen).
 *
 * Cómo funciona:
 *  1. Calcula el volumen de cada frame (RMS convertido a dBFS).
 *  2. Durante el primer segundo mide el ruido ambiente ("calibración").
 *  3. Después considera "voz" cualquier frame claramente más fuerte que el ruido
 *     de fondo (margen configurable) y por encima de un mínimo absoluto.
 *  4. Para evitar parpadeos: exige varios frames seguidos para ENTRAR en voz
 *     y varios frames de silencio seguidos para SALIR (como "colgar" una llamada).
 *  5. El ruido de fondo se va actualizando solo, para adaptarse al entorno.
 *
 * LIMITACIÓN: solo mide volumen. No distingue una voz de un portazo, música alta o
 * la televisión. Su trabajo es ser una "compuerta" barata: avisar de que HAY sonido
 * fuerte para que los modelos pesados de las fases siguientes (wakeword, verificación
 * de voz) solo trabajen cuando conviene. Las decisiones inteligentes las toman ellos.
 *
 * No es seguro entre hilos: úsalo siempre desde el mismo hilo (el de audio).
 *
 * @param margenSobreRuidoDb cuántos dB por encima del ruido debe estar un frame para contar como voz.
 * @param umbralMinimoDb volumen mínimo absoluto para contar como voz (evita falsas alarmas en cuartos muy silenciosos).
 * @param framesParaIniciarVoz frames seguidos con voz para declarar "hay voz" (3 ≈ 96 ms).
 * @param framesParaTerminarVoz frames seguidos sin voz para declarar "terminó" (15 ≈ 480 ms).
 * @param framesCalibracion frames usados para medir el ruido inicial (30 ≈ 1 s).
 * @param framesMaximoVoz si la "voz" dura más que esto (625 ≈ 20 s) se asume que es ruido nuevo y se recalibra.
 */
class VoiceDetector(
    private val margenSobreRuidoDb: Float = 10f,
    private val umbralMinimoDb: Float = -50f,
    private val framesParaIniciarVoz: Int = 3,
    private val framesParaTerminarVoz: Int = 15,
    private val framesCalibracion: Int = 30,
    private val framesMaximoVoz: Int = 625
) {

    private companion object {
        const val NIVEL_SILENCIO_DB = -90f
        const val VELOCIDAD_BAJADA_RUIDO = 0.3f   // el ruido de fondo baja rápido
        const val VELOCIDAD_SUBIDA_RUIDO = 0.02f  // y sube despacio
    }

    private var ruidoDb = NIVEL_SILENCIO_DB
    private var framesVistos = 0
    private var framesConVozSeguidos = 0
    private var framesSinVozSeguidos = 0
    private var framesEnVoz = 0
    private var enVoz = false

    /** Vuelve al estado inicial (se recalibra el ruido). Llamar al reiniciar la captura. */
    fun reiniciar() {
        ruidoDb = NIVEL_SILENCIO_DB
        framesVistos = 0
        framesConVozSeguidos = 0
        framesSinVozSeguidos = 0
        framesEnVoz = 0
        enVoz = false
    }

    /**
     * Analiza un frame de audio.
     *
     * @param muestras audio PCM de 16 bits
     * @param cantidad cuántas muestras válidas hay en el arreglo
     */
    fun procesar(muestras: ShortArray, cantidad: Int): ResultadoVoz {
        val nivel = calcularNivelDb(muestras, cantidad)
        framesVistos++

        // Fase de calibración: promedio del volumen ambiente, sin detectar voz todavía
        if (framesVistos <= framesCalibracion) {
            // Promedio acumulado: en el primer frame queda exactamente igual al nivel medido
            ruidoDb += (nivel - ruidoDb) / framesVistos
            return ResultadoVoz(nivel, ruidoDb, hayVoz = false, calibrando = true)
        }

        // ¿Este frame es claramente más fuerte que el ruido de fondo?
        val umbral = maxOf(ruidoDb + margenSobreRuidoDb, umbralMinimoDb)
        val frameConVoz = nivel > umbral

        if (frameConVoz) {
            framesConVozSeguidos++
            framesSinVozSeguidos = 0
        } else {
            framesSinVozSeguidos++
            framesConVozSeguidos = 0
        }

        // Entrar / salir del estado "hay voz" con histéresis
        if (!enVoz && framesConVozSeguidos >= framesParaIniciarVoz) {
            enVoz = true
            framesEnVoz = 0
        } else if (enVoz && framesSinVozSeguidos >= framesParaTerminarVoz) {
            enVoz = false
        }

        if (enVoz) {
            framesEnVoz++
            if (framesEnVoz > framesMaximoVoz) {
                // Demasiado largo para ser un comando: era un ruido nuevo (ventilador, música...)
                ruidoDb = nivel
                enVoz = false
                framesConVozSeguidos = 0
                framesSinVozSeguidos = 0
                framesEnVoz = 0
            }
        } else if (!frameConVoz) {
            // Solo se aprende el ruido de fondo con frames tranquilos y fuera de voz
            val velocidad = if (nivel < ruidoDb) VELOCIDAD_BAJADA_RUIDO else VELOCIDAD_SUBIDA_RUIDO
            ruidoDb += velocidad * (nivel - ruidoDb)
        }

        return ResultadoVoz(nivel, ruidoDb, hayVoz = enVoz, calibrando = false)
    }

    /** Volumen del frame en dBFS (RMS). Rango: -90 (silencio) a 0 (máximo). */
    private fun calcularNivelDb(muestras: ShortArray, cantidad: Int): Float {
        if (cantidad <= 0) return NIVEL_SILENCIO_DB

        var sumaCuadrados = 0L
        for (i in 0 until cantidad) {
            val s = muestras[i].toInt()
            sumaCuadrados += (s * s).toLong()
        }
        val rms = sqrt(sumaCuadrados.toDouble() / cantidad)
        val db = 20.0 * log10(rms / 32768.0) // rms = 0 da -infinito, se corrige abajo
        return db.coerceAtLeast(NIVEL_SILENCIO_DB.toDouble()).toFloat()
    }
}
