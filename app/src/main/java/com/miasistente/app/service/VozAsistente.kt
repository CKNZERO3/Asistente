package com.miasistente.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class VozAsistente(context: Context) {
    @Volatile private var listo = false
    private var tts: TextToSpeech? = null

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
        if (texto.isBlank()) return
        val motor = esperarListo() ?: return
        val id = "iris-${System.nanoTime()}"
        val fin = CompletableDeferred<Unit>()

        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) fin.complete(Unit)
            }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) fin.complete(Unit)
            }
        })

        motor.speak(texto, TextToSpeech.QUEUE_FLUSH, null, id)
        withTimeoutOrNull(25_000L) { fin.await() }
    }

    fun cerrar() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Ya cerrado
        }
        tts = null
        listo = false
    }
}
