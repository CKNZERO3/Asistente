package com.miasistente.app.laboratorio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RegistroRendimientoTest {

    private fun muestra(t: Double, rss: Long, temp: Double?, cpu: Double, termico: Int? = 0) =
        MuestraSistema(
            tSeg = t,
            ramLibreMb = 2000L - rss,
            ramTotalMb = 8000L,
            memoriaBaja = false,
            rssMb = rss,
            hwmMb = rss,
            pssMb = rss,
            cpuProcesoPct = cpu,
            bateriaPct = 80,
            cargando = false,
            tempBateriaC = temp,
            corrienteUa = null,
            estadoTermico = termico,
            margenTermico = null
        )

    private fun resultado(exito: Boolean = true, error: String? = null) = ResultadoPrueba(
        modelo = "gemma3-1b",
        backend = "CPU",
        inicioEpochMs = 0L,
        prefillSolicitado = 256,
        decodeSolicitado = 128,
        repeticion = 1,
        conDisipador = false,
        exito = exito,
        error = error,
        initSeg = 3.2,
        primerTokenSeg = 0.8,
        prefillTokens = 256,
        decodeTokens = 128,
        prefillTokS = 210.0,
        decodeTokS = 9.5,
        contextoUsado = 1280,
        duracionSeg = 20.0,
        muestras = listOf(
            muestra(0.0, 900L, 30.0, 50.0),
            muestra(1.0, 1500L, 31.5, 380.0, termico = 1),
            muestra(2.0, 1400L, 33.0, 350.0)
        )
    )

    @Test
    fun calculaPicosYVariacionDeTemperatura() {
        val r = resultado()
        assertThat(r.rssPicoMb).isEqualTo(1500L)
        assertThat(r.cpuPicoPct).isEqualTo(380.0)
        assertThat(r.tempInicioC).isEqualTo(30.0)
        assertThat(r.tempFinC).isEqualTo(33.0)
        assertThat(r.estadoTermicoMax).isEqualTo(1)
        assertThat(r.resumen()).contains("Δ 3.0")
    }

    @Test
    fun unaPruebaFallidaMuestraElErrorReal() {
        val r = resultado(exito = false, error = "RuntimeException: NPU no disponible")
        assertThat(r.resumen()).contains("FALLÓ: RuntimeException: NPU no disponible")
    }

    @Test
    fun elJsonIncluyeBackendYMuestras() {
        val json = resultado().aJson()
        assertThat(json.getString("backend")).isEqualTo("CPU")
        assertThat(json.getJSONArray("muestras").length()).isEqualTo(3)
        assertThat(json.getDouble("decodeTokS")).isEqualTo(9.5)
    }
}
