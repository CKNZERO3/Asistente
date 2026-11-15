package com.miasistente.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class VozAsistente(context: Context) {
    @Volatile private var listo = false
    private var tts: TextToSpeech? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { estado ->
            if (estado == TextToSpeech.SUCCESS) {
                val motor = tts
                if (motor != null) {
                    val r = motor.setLanguage(Locale.getDefault())
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        motor.setLanguage(Locale.forLanguageTag("es-ES"))
                    }
                    listo = true
                }
            }
        }
    }

    private suspend fun esperarListo(): TextToSpeech? {
        repeat(30) {
            if (listo) return tts
            delay(100)
        }
        return null
    }

    suspend fun hablar(texto: String) {
        val limpio = limpiarTextoParaVoz(texto)
        if (limpio.isBlank()) return
        val motor = esperarListo() ?: return
        val id = "iris-${System.nanoTime()}"
        val fin = CompletableDeferred<Unit>()

        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == id) _isSpeaking.value = true
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) {
                    _isSpeaking.value = false
                    fin.complete(Unit)
                }
            }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) {
                    _isSpeaking.value = false
                    fin.complete(Unit)
                }
            }
        })

        _isSpeaking.value = true
        val resultado = motor.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, id)
        if (resultado == TextToSpeech.ERROR) {
            _isSpeaking.value = false
            return
        }
        try {
            withTimeoutOrNull(25_000L) { fin.await() }
        } finally {
            _isSpeaking.value = false
        }
    }

    fun detener() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun cerrar() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        listo = false
        _isSpeaking.value = false
    }

    companion object {
        private val acotacionEntreAsteriscos = Regex("""\*[^*\n]{1,35}\*""")
        private val acotacionEntreCorchetes = Regex("""\[[^\]\n]{1,35}\]""")
        private val acotacionEmocionalParentesis = Regex(
            """\(\s*(?:sonrisa|sonríe|sonriendo|ríe|risas|suspiro|suspirando|guiño|pausa|asiente|emocionada|alegre|amable)\s*\)""",
            RegexOption.IGNORE_CASE
        )
        private val emojisYSymbols = Regex("""[\p{So}\p{Cs}\uFE0F\u200D]""")
        private val simbolosMarkdown = Regex("""[*_`#~]+""")
        private val espaciosMultiples = Regex("""\s{2,}""")

        /**
         * Elimina acotaciones escénicas (*sonrisa*, [ríe], (sonrisa)), emojis y símbolos
         * de formato Markdown para que el sintetizador lea únicamente texto natural.
         */
        fun limpiarTextoParaVoz(texto: String): String {
            return texto
                .replace(acotacionEmocionalParentesis, " ")
                .replace(acotacionEntreCorchetes, " ")
                .replace(acotacionEntreAsteriscos) { match ->
                    val contenido = match.value.trim('*').trim()
                    if (contenido.contains(' ') && contenido.split(' ').size > 2) {
                        " $contenido "
                    } else {
                        " "
                    }
                }
                .replace(simbolosMarkdown, "")
                .replace(emojisYSymbols, "")
                .replace(espaciosMultiples, " ")
                .trim()
        }
    }
}
