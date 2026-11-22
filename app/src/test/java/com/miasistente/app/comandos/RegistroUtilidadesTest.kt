package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RegistroUtilidadesTest {

    @Test
    fun generaPromptCompactoConHerramientasYContexto() {
        val prompt = RegistroUtilidades.generarPromptCompactoLocal("Usuario: Carlos")

        assertThat(prompt).contains(RegistroUtilidades.TOOL_SISTEMA)
        assertThat(prompt).contains(RegistroUtilidades.TOOL_ALARMA)
        assertThat(prompt).contains(RegistroUtilidades.TOOL_NOTAS)
        assertThat(prompt).contains(RegistroUtilidades.TOOL_DELEGAR_NUBE)
        assertThat(prompt).contains("Usuario: Carlos")
    }

    @Test
    fun parseaOrdenesEncadenadasEnArregloJson() {
        val respuestaModelo = """
            [
              {"tool": "sistema_control", "args": {"accion": "brillo", "valor": "bajar"}},
              {"tool": "alarma_temporizador", "args": {"tipo": "alarma", "valor": "06:30", "etiqueta": "Trabajo"}},
              {"tool": "notas_listas", "args": {"accion": "guardar", "contenido": "Comprar café", "destino": "keep"}}
            ]
        """.trimIndent()

        val llamadas = RegistroUtilidades.parsearLlamadasLocales(respuestaModelo)

        assertThat(llamadas).hasSize(3)
        assertThat(llamadas[0].tool).isEqualTo(RegistroUtilidades.TOOL_SISTEMA)
        assertThat(llamadas[0].args["accion"]).isEqualTo("brillo")
        assertThat(llamadas[1].tool).isEqualTo(RegistroUtilidades.TOOL_ALARMA)
        assertThat(llamadas[1].args["valor"]).isEqualTo("06:30")
        assertThat(llamadas[2].tool).isEqualTo(RegistroUtilidades.TOOL_NOTAS)
        assertThat(llamadas[2].args["destino"]).isEqualTo("keep")
    }

    @Test
    fun toleraBloquesMarkdownYTextoExtraDelModeloLocal() {
        val respuestaSucia = """
            Claro, aquí tienes la acción solicitada:
            ```json
            {"tool": "apps_navegacion", "args": {"accion": "abrir_app", "objetivo": "WhatsApp"}}
            ```
            Listo.
        """.trimIndent()

        val llamadas = RegistroUtilidades.parsearLlamadasLocales(respuestaSucia)

        assertThat(llamadas).hasSize(1)
        assertThat(llamadas.first().tool).isEqualTo(RegistroUtilidades.TOOL_APPS)
        assertThat(llamadas.first().args["objetivo"]).isEqualTo("WhatsApp")
    }

    @Test
    fun devuelveListaVaciaAnteJsonInvalidoOSinHerramientas() {
        assertThat(RegistroUtilidades.parsearLlamadasLocales("")).isEmpty()
        assertThat(RegistroUtilidades.parsearLlamadasLocales("Hola, ¿en qué puedo ayudarte hoy?")).isEmpty()
        assertThat(RegistroUtilidades.parsearLlamadasLocales("{json_roto: [}")).isEmpty()
    }

    @Test
    fun generaFunctionDeclarationsParaGeminiSinDelegarNube() {
        val declaraciones = RegistroUtilidades.generarFunctionDeclarationsGemini()

        assertThat(declaraciones.length()).isEqualTo(RegistroUtilidades.catalogo.size - 1)

        val nombres = buildList {
            for (i in 0 until declaraciones.length()) {
                add(declaraciones.getJSONObject(i).getString("name"))
            }
        }
        assertThat(nombres).contains(RegistroUtilidades.TOOL_SISTEMA)
        assertThat(nombres).contains(RegistroUtilidades.TOOL_MEMORIA)
        assertThat(nombres).doesNotContain(RegistroUtilidades.TOOL_DELEGAR_NUBE)
    }
}
