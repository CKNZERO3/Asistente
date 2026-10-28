package com.miasistente.app.audio

import android.util.Log
import com.miasistente.app.data.Ajustes
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

class VoskWakeWord(
    rutaModelo: String,
    palabraClave: String,
    private val confianzaMinima: Float = 0.6f
) : WakeWordEngine {

    private companion object {
        const val TAG = "VoskWakeWord"
        const val PREROLL_FRAMES = 10
    }

    override val palabraClave: String = Ajustes.normalizar(palabraClave)

    @Volatile
    private var ultimo = ""
    override val ultimoTexto: String
        get() = ultimo

    init {
        require(this.palabraClave.isNotEmpty()) { "La palabra clave está vacía." }
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    private val model: Model = Model(rutaModelo)

    // Recognizer con gramática estricta para la palabra clave
    private val recognizerWake: Recognizer = try {
        val gramatica = JSONArray().put(this.palabraClave).put("[unk]").toString()
        Recognizer(model, AudioRecorder.SAMPLE_RATE.toFloat(), gramatica).also {
            it.setWords(true)
        }
    } catch (e: Throwable) {
        model.close()
        throw e
    }

    // Recognizer de vocabulario libre para transcribir el comando completo tras la activación
    private val recognizerCommand: Recognizer = Recognizer(model, AudioRecorder.SAMPLE_RATE.toFloat())

    private val preroll = Array(PREROLL_FRAMES) { ShortArray(AudioRecorder.FRAME_SIZE) }
    private val prerollLargo = IntArray(PREROLL_FRAMES)
    private var prerollSiguiente = 0
    private var prerollUsados = 0

    private var vozActiva = false
    private var modoEscuchandoComando = false
    private var cerrado = false

    @Synchronized
    override fun procesar(muestras: ShortArray, cantidad: Int, hayVoz: Boolean): Boolean {
        if (cerrado) return false
        var detectada = false

        if (modoEscuchandoComando) {
            // Escuchando y transcribiendo el comando dictado tras el wake word
            if (hayVoz) {
                recognizerCommand.acceptWaveForm(muestras, cantidad)
            } else {
                // Silencio: el usuario terminó de hablar, obtenemos el texto final del comando
                modoEscuchandoComando = false
                val json = recognizerCommand.finalResult
                val textoTranscrito = extraerTextoJson(json)
                if (textoTranscrito.isNotBlank()) {
                    ultimo = textoTranscrito
                    detectada = true
                }
                recognizerCommand.reset()
                recognizerWake.reset()
            }
        } else {
            // Esperando la palabra clave
            if (hayVoz) {
                if (!vozActiva) {
                    vozActiva = true
                    val inicio = (prerollSiguiente - prerollUsados + PREROLL_FRAMES) % PREROLL_FRAMES
                    for (i in 0 until prerollUsados) {
                        val indice = (inicio + i) % PREROLL_FRAMES
                        if (alimentarWake(preroll[indice], prerollLargo[indice])) {
                            activarModoComando(muestras, cantidad)
                            return false
                        }
                    }
                    prerollUsados = 0
                }
                if (alimentarWake(muestras, cantidad)) {
                    activarModoComando(muestras, cantidad)
                    return false
                }
            } else {
                if (vozActiva) {
                    vozActiva = false
                    evaluarWake(recognizerWake.getFinalResult())
                    recognizerWake.reset()
                }
                guardarEnPreroll(muestras, cantidad)
            }
        }
        return detectada
    }

    private fun alimentarWake(muestras: ShortArray, cantidad: Int): Boolean =
        if (recognizerWake.acceptWaveForm(muestras, cantidad)) {
            evaluarWake(recognizerWake.getResult())
        } else {
            false
        }

    private fun activarModoComando(muestras: ShortArray, cantidad: Int) {
        Log.d(TAG, "¡Palabra clave detectada! Cambiando a transcripción de comando...")
        modoEscuchandoComando = true
        vozActiva = false
        // Alimentar el audio actual al reconocedor de comandos
        recognizerCommand.acceptWaveForm(muestras, cantidad)
    }

    private fun guardarEnPreroll(muestras: ShortArray, cantidad: Int) {
        val largo = minOf(cantidad, AudioRecorder.FRAME_SIZE)
        System.arraycopy(muestras, 0, preroll[prerollSiguiente], 0, largo)
        prerollLargo[prerollSiguiente] = largo
        prerollSiguiente = (prerollSiguiente + 1) % PREROLL_FRAMES
        if (prerollUsados < PREROLL_FRAMES) prerollUsados++
    }

    private fun evaluarWake(json: String): Boolean {
        val texto = extraerTextoJson(json)
        if (texto.isEmpty()) return false
        ultimo = texto
        if (!" $texto ".contains(" $palabraClave ")) return false

        val partes = palabraClave.split(" ")
        var confianza = 1.0
        val palabras = try { JSONObject(json).optJSONArray("result") } catch (_: Exception) { null }
        if (palabras != null) {
            for (i in 0 until palabras.length()) {
                val p = palabras.optJSONObject(i) ?: continue
                if (p.optString("word") in partes) {
                    confianza = minOf(confianza, p.optDouble("conf", 1.0))
                }
            }
        }
        return confianza >= confianzaMinima
    }

    private fun extraerTextoJson(json: String): String = try {
        JSONObject(json).optString("text", "").trim()
    } catch (e: JSONException) {
        ""
    }

    @Synchronized
    override fun close() {
        if (cerrado) return
        cerrado = true
        try {
            recognizerWake.close()
            recognizerCommand.close()
        } finally {
            model.close()
        }
        Log.d(TAG, "Motor liberado")
    }
}
