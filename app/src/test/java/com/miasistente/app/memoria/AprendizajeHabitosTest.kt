package com.miasistente.app.memoria

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AprendizajeHabitosTest {

    private fun elegir(veces: Int, opcion: String, desde: List<PesoHabito> = emptyList()): List<PesoHabito> {
        var pesos = desde
        repeat(veces) { pesos = AprendizajeHabitos.actualizar(pesos, opcion) }
        return pesos
    }

    @Test
    fun sinHistorialNoDecide() {
        assertThat(AprendizajeHabitos.decidir(emptyList())).isNull()
        assertThat(AprendizajeHabitos.decidir(elegir(2, "cuaderno"))).isNull()
    }

    @Test
    fun conUnHabitoClaroLoAutomatiza() {
        assertThat(AprendizajeHabitos.decidir(elegir(3, "cuaderno"))).isEqualTo("cuaderno")
    }

    @Test
    fun siElHabitoCambiaSeAdapta() {
        val antes = elegir(3, "cuaderno")
        assertThat(AprendizajeHabitos.decidir(elegir(2, "app:keep", antes))).isNull()
        assertThat(AprendizajeHabitos.decidir(elegir(4, "app:keep", antes))).isEqualTo("app:keep")
    }
}
