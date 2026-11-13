package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

class HabilidadesLocalesTest {

    private val zona = ZoneId.of("America/Caracas")
    private fun a(h: Int, m: Int) = HabilidadesLocales(
        Clock.fixed(ZonedDateTime.of(2026, 11, 13, h, m, 0, 0, zona).toInstant(), zona)
    )
    private val habilidades = a(14, 5)

    @Test
    fun respondeLaHora() {
        assertThat(habilidades.responder("¿Qué hora es?")).isEqualTo("Son las 2:05 de la tarde.")
        assertThat(a(13, 0).responder("dime la hora por favor")).isEqualTo("Es la 1:00 de la tarde.")
        assertThat(a(0, 30).responder("qué hora es")).isEqualTo("Son las 12:30 de la madrugada.")
    }

    @Test
    fun respondeLaFecha() {
        assertThat(habilidades.responder("¿Qué día es hoy?"))
            .isEqualTo("Hoy es viernes, 13 de noviembre de 2026.")
    }

    @Test
    fun calculaConPrioridadYParentesis() {
        assertThat(habilidades.responder("cuánto es 15 por 8")).isEqualTo("El resultado es 120.")
        assertThat(habilidades.responder("calcula 12 + 5 * 3")).isEqualTo("El resultado es 27.")
        assertThat(habilidades.responder("cuanto es (2+3)*4")).isEqualTo("El resultado es 20.")
        assertThat(habilidades.responder("cuanto es 10 entre 4")).isEqualTo("El resultado es 2,5.")
        assertThat(habilidades.responder("cuanto es 0,1 mas 0,2")).isEqualTo("El resultado es 0,3.")
        assertThat(habilidades.responder("cuanto es 1.000 por 3")).isEqualTo("El resultado es 3000.")
        assertThat(habilidades.responder("el 15% de 200")).isEqualTo("El 15 % de 200 es 30.")
    }

    @Test
    fun noSeDivideEntreCero() {
        assertThat(habilidades.responder("cuanto es 5 entre 0")).isEqualTo("No se puede dividir entre cero.")
    }

    @Test
    fun loQueNoEsHabilidadLocalDevuelveNull() {
        assertThat(habilidades.responder("555-1234")).isNull()
        assertThat(habilidades.responder("2024")).isNull()
        assertThat(habilidades.responder("cuéntame un chiste")).isNull()
        assertThat(habilidades.responder("qué hora abre el banco")).isNull()
        assertThat(habilidades.responder("cuanto es 2 por perro")).isNull()
        assertThat(habilidades.responder("cuanto es 2 +")).isNull()
    }

    @Test
    fun detectaPreguntasDeClima() {
        assertThat(habilidades.peticionClima("¿Qué tiempo hace en Caracas?")).isEqualTo(PeticionClima("caracas"))
        assertThat(habilidades.peticionClima("cómo está el clima en valencia hoy")).isEqualTo(PeticionClima("valencia"))
        assertThat(habilidades.peticionClima("va a llover en Mérida")).isEqualTo(PeticionClima("merida"))
        assertThat(habilidades.peticionClima("cómo está el clima")).isEqualTo(PeticionClima(null))
    }

    @Test
    fun noConfundeClimaConOtrasConsultas() {
        assertThat(habilidades.peticionClima("clima en caracas mañana")).isNull()
        assertThat(habilidades.peticionClima("cuánto tiempo tarda en hervir el agua")).isNull()
    }
}
