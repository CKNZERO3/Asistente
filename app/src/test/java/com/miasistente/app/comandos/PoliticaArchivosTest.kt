package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PoliticaArchivosTest {

    @Test
    fun reconoceArchivosDeTexto() {
        assertThat(PoliticaArchivos.esTexto("notas.txt", null)).isTrue()
        assertThat(PoliticaArchivos.esTexto("datos.CSV", null)).isTrue()
        assertThat(PoliticaArchivos.esTexto("sin_extension", "text/plain")).isTrue()
        assertThat(PoliticaArchivos.esTexto("foto.jpg", "image/jpeg")).isFalse()
        assertThat(PoliticaArchivos.esTexto("informe.pdf", "application/pdf")).isFalse()
    }

    @Test
    fun rechazaLoQueNoEsTextoOPesaDemasiado() {
        assertThat(PoliticaArchivos.motivoDeRechazo("a.pdf", "application/pdf", 1_000)).contains("solo puedo resumir")
        assertThat(PoliticaArchivos.motivoDeRechazo("a.txt", "text/plain", 300 * 1024L)).contains("200 KB")
        assertThat(PoliticaArchivos.motivoDeRechazo("a.txt", "text/plain", 10 * 1024L)).isNull()
        assertThat(PoliticaArchivos.motivoDeRechazo("a.txt", null, null)).isNull()
    }

    @Test
    fun detectaCuandoPideResumirUnArchivo() {
        assertThat(PoliticaArchivos.pideResumirArchivo("resume el archivo")).isTrue()
        assertThat(PoliticaArchivos.pideResumirArchivo("¿Puedes resumir este documento?")).isTrue()
        assertThat(PoliticaArchivos.pideResumirArchivo("hazme un resumen del texto adjunto")).isTrue()
        assertThat(PoliticaArchivos.pideResumirArchivo("qué hora es")).isFalse()
        assertThat(PoliticaArchivos.pideResumirArchivo("resume la historia de Roma")).isFalse()
    }

    @Test
    fun noTratacomoPeticionDeArchivoUnTextoLargoPegado() {
        val largo = "resume este texto: " + "palabra ".repeat(60)
        assertThat(PoliticaArchivos.pideResumirArchivo(largo)).isFalse()
    }
}
