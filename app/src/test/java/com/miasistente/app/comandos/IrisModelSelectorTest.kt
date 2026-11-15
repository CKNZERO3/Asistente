package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IrisModelSelectorTest {

    @Test
    fun tareasComplejasUsanModeloAvanzado() {
        val decision = IrisModelSelector.seleccionar(
            "Investiga y compara tres alternativas y prepara un informe detallado."
        )

        assertThat(decision.modelo).isEqualTo(IrisModelSelector.MODELO_AVANZADO)
        assertThat(decision.categoria).isEqualTo(CategoriaTarea.COMPLEJA)
    }

    @Test
    fun transformacionesBrevesUsanModeloLigero() {
        val decision = IrisModelSelector.seleccionar(
            "Traduce este párrafo al inglés."
        )

        assertThat(decision.modelo).isEqualTo(IrisModelSelector.MODELO_LIGERO)
        assertThat(decision.categoria).isEqualTo(CategoriaTarea.LIGERA)
    }

    @Test
    fun conversacionGeneralUsaModeloConMayorCuota() {
        val decision = IrisModelSelector.seleccionar(
            "¿Qué significa la palabra resiliencia?"
        )

        assertThat(decision.modelo).isEqualTo(IrisModelSelector.MODELO_GENERAL)
        assertThat(decision.categoria).isEqualTo(CategoriaTarea.GENERAL)
    }

    @Test
    fun tareaComplejaTienePrioridadSobreReglaLigera() {
        val decision = IrisModelSelector.seleccionar(
            "Resume e investiga detalladamente este tema con varias fuentes."
        )

        assertThat(decision.modelo).isEqualTo(IrisModelSelector.MODELO_AVANZADO)
    }

    @Test
    fun noSeAceptanModelosFueraDelCatalogoInicial() {
        assertThat(IrisModelSelector.esModeloPermitido("modelo-inventado")).isFalse()
        assertThat(IrisModelSelector.esModeloPermitido(IrisModelSelector.MODELO_GENERAL)).isTrue()
        assertThat(IrisModelSelector.esModeloPermitido("gemini-2.5-flash")).isFalse()
    }

    @Test
    fun preguntasDeDatosVanAGeminiNoAlModeloLocal() {
        listOf(
            "¿Cómo se llama el satélite que tiene la Tierra?",
            "¿Cuál es la capital de Francia?",
            "define resiliencia",
            "dame una lista de países de Europa",
            "cuéntame la historia de Roma",
            "hola, ¿cuál es la capital de Francia?"
        ).forEach {
            assertThat(IrisModelSelector.seleccionar(it).categoria).isEqualTo(CategoriaTarea.GENERAL)
        }
    }

    @Test
    fun saludosYEscrituraCreativaCortaSeQuedanEnElTelefono() {
        listOf(
            "hola",
            "Buenos días, Iris",
            "gracias",
            "escríbeme un poema de cuatro líneas sobre el mar",
            "cuéntame un chiste",
            "dame ideas para una fiesta de cumpleaños",
            "redacta un mensaje para felicitar a mi madre"
        ).forEach {
            assertThat(IrisModelSelector.seleccionar(it).categoria).isEqualTo(CategoriaTarea.LIGERA)
        }
    }

    @Test
    fun escribirUnProgramaOUnInformeTecnicoEsComplejo() {
        assertThat(IrisModelSelector.seleccionar("escribe un programa en Python").categoria)
            .isEqualTo(CategoriaTarea.COMPLEJA)
        assertThat(IrisModelSelector.seleccionar("redacta un informe técnico del proyecto").categoria)
            .isEqualTo(CategoriaTarea.COMPLEJA)
    }
}
