package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClienteProveedoresIATest {

    @Test
    fun normalizaEndpointsOpenAIYLocales() {
        assertThat(ClienteProveedoresIA.normalizarEndpointOpenAI("[https://api.groq.com/openai/v1](https://api.groq.com/openai/v1)"))
            .isEqualTo("[https://api.groq.com/openai/v1/chat/completions](https://api.groq.com/openai/v1/chat/completions)")
        assertThat(ClienteProveedoresIA.normalizarEndpointOpenAI("[http://127.0.0.1:11434/v1/chat/completions/](http://127.0.0.1:11434/v1/chat/completions/)"))
            .isEqualTo("[http://127.0.0.1:11434/v1/chat/completions](http://127.0.0.1:11434/v1/chat/completions)")
    }

    @Test
    fun extraeTextoDeRespuestaOpenAICompatible() {
        val json = """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "Hola, soy Iris operando desde tu API personalizada."
                  }
                }
              ]
            }
        """.trimIndent()

        assertThat(ClienteProveedoresIA.extraerTextoOpenAI(json))
            .isEqualTo("Hola, soy Iris operando desde tu API personalizada.")
    }

    @Test
    fun extraeTextoDeRespuestaGeminiDirecta() {
        val json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Respuesta directa de Gemini." }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        assertThat(ClienteProveedoresIA.extraerTextoGemini(json))
            .isEqualTo("Respuesta directa de Gemini.")
    }
}
