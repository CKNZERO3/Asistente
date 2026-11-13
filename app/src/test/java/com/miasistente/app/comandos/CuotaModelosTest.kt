package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CuotaModelosTest {

    private var ahora = 1_000_000L
    private val cuota = CuotaModelos { ahora }

    @Test
    fun modeloNuevoEstaDisponible() {
        assertThat(cuota.disponible("m")).isTrue()
        assertThat(cuota.segundosRestantes("m")).isEqualTo(0)
    }

    @Test
    fun tras429QuedaEnPausa60SegundosYLuegoVuelve() {
        cuota.registrarAgotada("m")
        assertThat(cuota.disponible("m")).isFalse()
        assertThat(cuota.segundosRestantes("m")).isEqualTo(60)

        ahora += 59_000
        assertThat(cuota.disponible("m")).isFalse()
        ahora += 1_000
        assertThat(cuota.disponible("m")).isTrue()
    }

    @Test
    fun laPausaSeDuplicaYTieneTopeDe15Minutos() {
        repeat(2) { cuota.registrarAgotada("m"); ahora += 10 * 60_000 }
        cuota.registrarAgotada("m")
        assertThat(cuota.segundosRestantes("m")).isEqualTo(240)
        repeat(10) { cuota.registrarAgotada("m") }
        assertThat(cuota.segundosRestantes("m")).isEqualTo(900)
    }

    @Test
    fun unExitoReiniciaElModelo() {
        cuota.registrarAgotada("m")
        cuota.registrarAgotada("m")
        cuota.registrarExito("m")
        assertThat(cuota.disponible("m")).isTrue()
        cuota.registrarAgotada("m")
        assertThat(cuota.segundosRestantes("m")).isEqualTo(60)
    }

    @Test
    fun losModelosSeCuentanPorSeparado() {
        cuota.registrarAgotada("a")
        assertThat(cuota.disponible("b")).isTrue()
    }
}
