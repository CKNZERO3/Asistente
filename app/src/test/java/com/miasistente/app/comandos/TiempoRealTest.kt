package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TiempoRealTest {

    @Test
    fun detectaConsultasQueNecesitanInternet() {
        listOf(
            "¿Quién ganó el partido de anoche?",
            "últimas noticias de Venezuela",
            "precio del dólar hoy",
            "resultados de la Champions",
            "¿cuántos años tiene Shakira?",
            "¿Quién es el actual presidente de Chile?",
            "¿Cuál es el último descubrimiento sobre Marte?"
        ).forEach { assertThat(IrisModelSelector.requiereTiempoReal(it)).isTrue() }
    }

    @Test
    fun loQueNoDependeDeLaActualidadNoCuenta() {
        listOf(
            "traduce al inglés: hoy es lunes",
            "¿Cómo se llama el satélite de la Tierra?",
            "resume este texto",
            "escríbeme un poema sobre el mar"
        ).forEach { assertThat(IrisModelSelector.requiereTiempoReal(it)).isFalse() }
    }
}
