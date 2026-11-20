package com.miasistente.app.data

import com.google.common.truth.Truth.assertThat
import com.miasistente.app.comandos.construirInstruccionLocal
import org.junit.jupiter.api.Test

class InteligenciaConfigTest {

    @Test
    fun serializarYDeserializarReglasMantieneEstadoYContenido() {
        val originales = listOf(
            ReglaComportamiento("1", "Brevedad", "Responde en 3 líneas.", activa = true),
            ReglaComportamiento("2", "Formal", "Usa tono formal.", activa = false)
        )

        val json = InteligenciaConfig.serializarReglas(originales)
        val restauradas = InteligenciaConfig.deserializarReglas(json)

        assertThat(restauradas).hasSize(2)
        assertThat(restauradas[0].titulo).isEqualTo("Brevedad")
        assertThat(restauradas[0].activa).isTrue()
        assertThat(restauradas[1].activa).isFalse()
    }

    @Test
    fun compilarInstruccionesActivasSoloIncluyeLasHabilitadas() {
        val reglas = listOf(
            ReglaComportamiento("1", "Viñetas", "Usa listas con viñetas.", activa = true),
            ReglaComportamiento("2", "Inactiva", "No debe aparecer.", activa = false),
            ReglaComportamiento("3", "Código", "Incluye ejemplos Kotlin.", activa = true)
        )

        val bloque = InteligenciaConfig.compilarInstruccionesActivas(reglas)

        assertThat(bloque).contains("[Viñetas]: Usa listas con viñetas.")
        assertThat(bloque).contains("[Código]: Incluye ejemplos Kotlin.")
        assertThat(bloque).doesNotContain("Inactiva")
    }

    @Test
    fun construirInstruccionLocalCombinaReglasYMemoriaDelUsuario() {
        val prompt = construirInstruccionLocal(
            contextoUsuario = "- El usuario se llama Cristian.",
            instruccionesPersonalizadas = "- [Brevedad]: Sé conciso."
        )

        assertThat(prompt).contains("Instrucciones personalizadas del usuario")
        assertThat(prompt).contains("[Brevedad]: Sé conciso.")
        assertThat(prompt).contains("Memoria y preferencias del usuario")
        assertThat(prompt).contains("Cristian")
    }
}
