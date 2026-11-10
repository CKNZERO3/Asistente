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
}
