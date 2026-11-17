package com.miasistente.app.memoria

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ClasificadorCuadernosTest {

    private val cuadernos = listOf(
        CuadernoResumen(1, "Notas Personales"),
        CuadernoResumen(2, "Proyecto de App"),
        CuadernoResumen(3, "Compras", "• leche")
    )

    @Test
    fun reconoceElTema() {
        assertThat(ClasificadorCuadernos.tema("comprar pan, azúcar, leche")).isEqualTo("Compras")
        assertThat(ClasificadorCuadernos.tema("reunión con el cliente el lunes")).isEqualTo("Trabajo")
        assertThat(ClasificadorCuadernos.tema("receta de arepas: harina, agua y sal")).isEqualTo("Recetas")
        assertThat(ClasificadorCuadernos.tema("hola qué tal")).isNull()
    }

    @Test
    fun eligeElCuadernoQueEncaja() {
        assertThat(ClasificadorCuadernos.elegir("comprar huevos y queso", cuadernos)?.id).isEqualTo(3L)
        assertThat(ClasificadorCuadernos.elegir("nueva pantalla para el proyecto de la app", cuadernos)?.id)
            .isEqualTo(2L)
    }

    @Test
    fun siNadaEncajaSeCreaUnoNuevoConTituloCorto() {
        assertThat(ClasificadorCuadernos.elegir("receta de torta de chocolate con horno", cuadernos)).isNull()
        assertThat(ClasificadorCuadernos.tituloNuevo("receta de torta de chocolate con horno")).isEqualTo("Recetas")
        assertThat(ClasificadorCuadernos.tituloNuevo("Ana prefiere girasoles amarillos"))
            .isEqualTo("Ana prefiere girasoles")
        assertThat(ClasificadorCuadernos.tituloNuevo("")).isEqualTo("Notas")
    }
}
