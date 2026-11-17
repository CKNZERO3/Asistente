package com.miasistente.app.comandos

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ExtractorNotaTest {

    private fun analizar(frase: String) = ExtractorNota.analizar(frase)

    @Test
    fun quitaLasPalabrasDeLaOrden() {
        assertThat(analizar("Puedes crear una nota y guardarla: comprar pan, azúcar, leche"))
            .isEqualTo(PeticionGuardar("comprar pan, azúcar, leche", DestinoNota.SinEspecificar))
        assertThat(analizar("guarda en la lista que diga: comprar pan"))
            .isEqualTo(PeticionGuardar("comprar pan", DestinoNota.SinEspecificar))
        assertThat(analizar("anota esto: llamar a mamá")?.contenido).isEqualTo("llamar a mamá")
        assertThat(analizar("toma nota de que mañana hay reunión a las 9")?.contenido)
            .isEqualTo("mañana hay reunión a las 9")
        assertThat(analizar("escribe una nota que diga hola")?.contenido).isEqualTo("hola")
        assertThat(analizar("oye iris, apunta comprar café por favor")?.contenido).isEqualTo("comprar café")
        assertThat(analizar("recuérdame comprar pan")?.contenido).isEqualTo("comprar pan")
    }

    @Test
    fun detectaElCuadernoPorNombre() {
        assertThat(analizar("Anota en Trabajo: revisar el informe"))
            .isEqualTo(PeticionGuardar("revisar el informe", DestinoNota.Cuaderno("Trabajo")))
        assertThat(analizar("anota en trabajo revisar el informe"))
            .isEqualTo(PeticionGuardar("revisar el informe", DestinoNota.Cuaderno("Trabajo")))
        assertThat(analizar("guarda esta nota en el cuaderno de compras: pan y leche"))
            .isEqualTo(PeticionGuardar("pan y leche", DestinoNota.Cuaderno("Compras")))
        assertThat(analizar("anota en mi cuaderno de ideas de proyectos: una app de recetas"))
            .isEqualTo(PeticionGuardar("una app de recetas", DestinoNota.Cuaderno("Ideas de proyectos")))
        assertThat(analizar("anota en mi cuaderno: idea para la app"))
            .isEqualTo(PeticionGuardar("idea para la app", DestinoNota.Cuaderno(null)))
    }

    @Test
    fun imagenesYArchivosSinTexto() {
        assertThat(analizar("Guarda esta imagen en el cuaderno Recetas"))
            .isEqualTo(PeticionGuardar("", DestinoNota.Cuaderno("Recetas"), mencionaAdjunto = true))
        assertThat(analizar("guarda las fotos en mi cuaderno"))
            .isEqualTo(PeticionGuardar("", DestinoNota.Cuaderno(null), mencionaAdjunto = true))
    }

    @Test
    fun detectaAppsExternas() {
        assertThat(analizar("guarda en Keep: comprar pan"))
            .isEqualTo(PeticionGuardar("comprar pan", DestinoNota.App("Keep")))
        assertThat(analizar("crea una nota en Google Keep que diga comprar leche"))
            .isEqualTo(PeticionGuardar("comprar leche", DestinoNota.App("Google Keep")))
        assertThat(analizar("anota en la app de Evernote: lista")?.destino).isEqualTo(DestinoNota.App("Evernote"))
        assertThat(analizar("crea una nota y guárdala en Keep: «comprar pan»"))
            .isEqualTo(PeticionGuardar("comprar pan", DestinoNota.App("Keep")))
    }

    @Test
    fun noConfundeElContenidoConUnDestino() {
        assertThat(analizar("anota que mañana voy en bus"))
            .isEqualTo(PeticionGuardar("mañana voy en bus", DestinoNota.SinEspecificar))
        assertThat(analizar("anota que el wifi es 1234")?.contenido).isEqualTo("el wifi es 1234")
    }

    @Test
    fun loQueNoEsUnaNotaDevuelveNull() {
        assertThat(analizar("escribe un correo a Juan")).isNull()
        assertThat(analizar("qué hora es")).isNull()
        assertThat(analizar("")).isNull()
    }
}
