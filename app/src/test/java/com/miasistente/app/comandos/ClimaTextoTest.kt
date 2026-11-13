package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClimaTextoTest {

    @Test
    fun resumenEnEspanol() {
        val d = DatosClima("Caracas, Venezuela", 29.4, 32.1, 70, 12.3, 2, 31.0, 24.2, 40)
        assertThat(ClimaTexto.resumen(d)).isEqualTo(
            "En Caracas, Venezuela hay 29 °C y está parcialmente nublado. Sensación térmica de 32 °C. " +
                "Hoy se esperan entre 24 y 31 °C, con 40 % de probabilidad de lluvia. " +
                "Humedad del 70 % y viento de 12 km/h."
        )
    }

    @Test
    fun sinProbabilidadDeLluviaNoLaMenciona() {
        val d = DatosClima("Lima, Perú", 18.0, 17.0, 80, 5.0, 3, 20.0, 16.0, null)
        assertThat(ClimaTexto.resumen(d)).doesNotContain("probabilidad")
    }

    @Test
    fun codigosDesconocidosTienenDescripcion() {
        assertThat(ClimaTexto.descripcion(999)).isEqualTo("con condiciones variables")
        assertThat(ClimaTexto.descripcion(95)).isEqualTo("con tormenta")
    }
}
