package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ComandosMemoriaTest {

    @Test
    fun recordarUnHecho() {
        assertThat(ComandosMemoria.interpretar("Recuerda que mi color favorito es el azul"))
            .isEqualTo(AccionMemoria.Recordar("Mi color favorito es el azul"))
        assertThat(ComandosMemoria.interpretar("recuerda que mi cumpleaños es el 5 de mayo."))
            .isEqualTo(AccionMemoria.Recordar("Mi cumpleaños es el 5 de mayo"))
    }

    @Test
    fun consultarLaMemoria() {
        assertThat(ComandosMemoria.interpretar("¿Qué recuerdas?")).isEqualTo(AccionMemoria.Consultar(null))
        assertThat(ComandosMemoria.interpretar("¿qué sabes de mí?")).isEqualTo(AccionMemoria.Consultar(null))
        assertThat(ComandosMemoria.interpretar("qué recuerdas de mi color favorito"))
            .isEqualTo(AccionMemoria.Consultar("mi color favorito"))
    }

    @Test
    fun seguimientoDeNotaNoGuardada() {
        assertThat(ComandosMemoria.interpretar("No se guardó")).isEqualTo(AccionMemoria.NoSeGuardo)
        assertThat(ComandosMemoria.interpretar("pero no lo guardó")).isEqualTo(AccionMemoria.NoSeGuardo)
        assertThat(ComandosMemoria.interpretar("no aparece")).isEqualTo(AccionMemoria.NoSeGuardo)
    }

    @Test
    fun noRobaOtrasOrdenes() {
        assertThat(ComandosMemoria.interpretar("recuérdame comprar pan")).isNull()
        assertThat(ComandosMemoria.interpretar("qué sabes de física cuántica")).isNull()
        assertThat(ComandosMemoria.interpretar("cuál es la capital de Francia")).isNull()
    }
}
