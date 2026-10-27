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

/**
 * Detector de palabra clave basado en Vosk (reconocimiento de voz offline).
 *
 * Idea: en vez de reconocer cualquier cosa, le damos a Vosk una "gramática" mínima con
 * solo dos opciones: tu palabra clave y "[unk]" (cualquier otra cosa). Así el modelo solo
 * decide "¿dijo la palabra clave o no?", que es mucho más barato y preciso que transcribir.
 * Cambiar la palabra clave es cambiar un texto: no hay que entrenar nada.
 *
 * Ahorro de batería: Vosk SOLO trabaja mientras el VoiceDetector dice que hay voz.
 * En silencio, este motor solo guarda los últimos frames en una memoria circular.
 *
 * Memoria circular (preroll): el VoiceDetector tarda ~96 ms en confirmar que hay voz, y
 * sin ayuda se perdería el comienzo de la palabra. Por eso se guardan los últimos
 * PREROLL_FRAMES frames y, cuando empieza la voz, se entregan primero a Vosk.
 *
 * Limitaciones conocidas:
 *  - La palabra clave debe existir en el vocabulario del modelo (palabras comunes del
 *    español). Si no existe, Vosk la ignora y nunca se activará. Revisa "Último reconocido"
 *    en pantalla para comprobarlo.
 *  - Una gramática tan pequeña puede dar falsos positivos con palabras parecidas. Se
 *    reduce exigiendo una confianza mínima (parámetro [confianzaMinima]).
 *
 * @param rutaModelo carpeta del modelo ya descomprimido
 * @param palabraClave palabra o frase a detectar (se normaliza a minúsculas y solo letras)
 * @param confianzaMinima 0..1; súbela si hay falsas activaciones, bájala si no te detecta
 * @throws java.io.IOException si el modelo no se puede cargar
 */
class VoskWakeWord(
    rutaModelo: String,
    palabraClave: String,
    private val confianzaMinima: Float = 0.6f
) : WakeWordEngine {

    private companion object {
        const val TAG = "VoskWakeWord"
        const val PREROLL_FRAMES = 10 // 10 × 32 ms = 320 ms de audio previo
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

    // Carga el modelo (puede tardar 1-3 segundos y lanza IOException si está dañado)
    private val model: Model = Model(rutaModelo)

    private val recognizer: Recognizer = try {
        // Gramática: ["tu palabra", "[unk]"]  (JSONArray escapa bien cualquier carácter)
        val gramatica = JSONArray().put(this.palabraClave).put("[unk]").toString()
        Recognizer(model, AudioRecorder.SAMPLE_RATE.toFloat(), gramatica).also {
            it.setWords(true) // incluye la confianza de cada palabra en el resultado
        }
    } catch (e: Throwable) {
        model.close()
        throw e
    }

    // Memoria circular de los últimos frames (se reutilizan los arreglos, sin crear basura)
    private val preroll = Array(PREROLL_FRAMES) { ShortArray(AudioRecorder.FRAME_SIZE) }
    private val prerollLargo = IntArray(PREROLL_FRAMES)
    private var prerollSiguiente = 0
    private var prerollUsados = 0

    private var vozActiva = false
    private var cerrado = false

    init {
        Log.d(TAG, "Motor listo. Palabra clave: «${this.palabraClave}»")
    }

    // @Synchronized: procesar() corre en el hilo de audio y close() en el principal;
    // así nunca se libera la memoria nativa mientras Vosk la está usando.
    @Synchronized
    override fun procesar(muestras: ShortArray, cantidad: Int, hayVoz: Boolean): Boolean {
        if (cerrado) return false
        var detectada = false

        if (hayVoz) {
            if (!vozActiva) {
                // Empieza una frase: primero el audio previo guardado (del más viejo al más nuevo)
                vozActiva = true
                val inicio = (prerollSiguiente - prerollUsados + PREROLL_FRAMES) % PREROLL_FRAMES
                for (i in 0 until prerollUsados) {
                    val indice = (inicio + i) % PREROLL_FRAMES
                    if (alimentar(preroll[indice], prerollLargo[indice])) detectada = true
                }
                prerollUsados = 0
            }
            if (alimentar(muestras, cantidad)) detectada = true
        } else {
            if (vozActiva) {
                // La frase terminó: pedir el resultado final de lo que Vosk aún tenga pendiente
                vozActiva = false
                if (evaluar(recognizer.getFinalResult())) detectada = true
                recognizer.reset()
            }
            guardarEnPreroll(muestras, cantidad)
        }
        return detectada
    }

    /** Entrega audio a Vosk. Si Vosk cierra una frase por su cuenta, evalúa el resultado. */
    private fun alimentar(muestras: ShortArray, cantidad: Int): Boolean =
        if (recognizer.acceptWaveForm(muestras, cantidad)) {
            evaluar(recognizer.getResult())
        } else {
            false
        }

    private fun guardarEnPreroll(muestras: ShortArray, cantidad: Int) {
        val largo = minOf(cantidad, AudioRecorder.FRAME_SIZE)
        System.arraycopy(muestras, 0, preroll[prerollSiguiente], 0, largo)
        prerollLargo[prerollSiguiente] = largo
        prerollSiguiente = (prerollSiguiente + 1) % PREROLL_FRAMES
        if (prerollUsados < PREROLL_FRAMES) prerollUsados++
    }

    /**
     * Lee el JSON de Vosk y decide si contiene la palabra clave con confianza suficiente.
     * Ejemplo de JSON:
     *   {"result":[{"conf":1.0,"end":1.2,"start":0.6,"word":"asistente"}],"text":"asistente"}
     */
    private fun evaluar(json: String): Boolean {
        val objeto = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return false
        }

        val texto = objeto.optString("text", "").trim()
        if (texto.isEmpty()) return false
        ultimo = texto

        // ¿La palabra (o frase) aparece completa? Se rodea de espacios para no confundir
        // "asistente" con "asistentes".
        if (!" $texto ".contains(" $palabraClave ")) return false

        // Confianza: la más baja entre las palabras que forman la palabra clave
        val partes = palabraClave.split(" ")
        var confianza = 1.0
        val palabras = objeto.optJSONArray("result")
        if (palabras != null) {
            for (i in 0 until palabras.length()) {
                val p = palabras.optJSONObject(i) ?: continue
                if (p.optString("word") in partes) {
                    confianza = minOf(confianza, p.optDouble("conf", 1.0))
                }
            }
        }

        val aceptada = confianza >= confianzaMinima
        Log.d(TAG, "«$texto» confianza=${"%.2f".format(confianza)} -> " +
                if (aceptada) "ACEPTADA" else "rechazada")
        return aceptada
    }

    @Synchronized
    override fun close() {
        if (cerrado) return
        cerrado = true
        try {
            recognizer.close()
        } finally {
            model.close()
        }
        Log.d(TAG, "Motor liberado")
    }
}
