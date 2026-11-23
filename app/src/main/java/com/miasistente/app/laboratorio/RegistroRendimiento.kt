package com.miasistente.app.laboratorio

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private fun fmt(valor: Double?, decimales: Int = 2): String =
    if (valor == null || !valor.isFinite()) "?" else String.format(Locale.US, "%.${decimales}f", valor)

private fun Double.finito(): Double = if (isFinite()) this else 0.0

/** Lectura del estado del teléfono, una por segundo mientras corre una prueba. */
data class MuestraSistema(
    /** Segundos desde que empezó el muestreo. */
    val tSeg: Double,
    val ramLibreMb: Long,
    val ramTotalMb: Long,
    val memoriaBaja: Boolean,
    /** Memoria residente del proceso de Iris (VmRSS). -1 si no se pudo leer. */
    val rssMb: Long,
    /** Pico de memoria residente del proceso desde que arrancó (VmHWM). -1 si no se pudo leer. */
    val hwmMb: Long,
    val pssMb: Long,
    /** Tiempo de CPU del proceso como % de UN núcleo (con varios hilos puede pasar de 100). */
    val cpuProcesoPct: Double,
    val bateriaPct: Int?,
    val cargando: Boolean,
    val tempBateriaC: Double?,
    /** Corriente de la batería en µA. El signo y la unidad cambian según el fabricante. */
    val corrienteUa: Long?,
    /** 0 = normal … 6 = apagado de emergencia (PowerManager.THERMAL_STATUS_*). */
    val estadoTermico: Int?,
    /** Margen térmico estimado (1.0 = al límite de throttling severo). */
    val margenTermico: Double?,
    /** Temperaturas de /sys/class/thermal si el sistema deja leerlas (suele estar bloqueado). */
    val zonasC: Map<String, Double> = emptyMap()
) {
    fun aJson(): JSONObject = JSONObject().apply {
        put("tSeg", tSeg.finito())
        put("ramLibreMb", ramLibreMb)
        put("ramTotalMb", ramTotalMb)
        put("memoriaBaja", memoriaBaja)
        put("rssMb", rssMb)
        put("hwmMb", hwmMb)
        put("pssMb", pssMb)
        put("cpuProcesoPct", cpuProcesoPct.finito())
        putOpt("bateriaPct", bateriaPct)
        put("cargando", cargando)
        putOpt("tempBateriaC", tempBateriaC?.takeIf { it.isFinite() })
        putOpt("corrienteUa", corrienteUa)
        putOpt("estadoTermico", estadoTermico)
        putOpt("margenTermico", margenTermico?.takeIf { it.isFinite() })
        put("zonasC", JSONObject(zonasC))
    }
}

/** Resultado de UNA ejecución del benchmark (un backend, un tamaño de prompt, una repetición). */
data class ResultadoPrueba(
    val modelo: String,
    val backend: String,
    val inicioEpochMs: Long,
    val prefillSolicitado: Int,
    val decodeSolicitado: Int,
    val repeticion: Int,
    val conDisipador: Boolean,
    val exito: Boolean,
    val error: String?,
    val initSeg: Double?,
    val primerTokenSeg: Double?,
    val prefillTokens: Int?,
    val decodeTokens: Int?,
    val prefillTokS: Double?,
    val decodeTokS: Double?,
    /** Tamaño real del KV cache que usó el motor (puede diferir del pedido). */
    val contextoUsado: Int?,
    val duracionSeg: Double,
    val muestras: List<MuestraSistema>
) {
    val rssPicoMb: Long? get() = muestras.maxOfOrNull { it.rssMb }?.takeIf { it >= 0 }
    val hwmMb: Long? get() = muestras.maxOfOrNull { it.hwmMb }?.takeIf { it >= 0 }
    val cpuPicoPct: Double? get() = muestras.maxOfOrNull { it.cpuProcesoPct }
    val ramLibreMinMb: Long? get() = muestras.minOfOrNull { it.ramLibreMb }
    val tempInicioC: Double? get() = muestras.firstNotNullOfOrNull { it.tempBateriaC }
    val tempFinC: Double? get() = muestras.lastOrNull { it.tempBateriaC != null }?.tempBateriaC
    val estadoTermicoMax: Int? get() = muestras.mapNotNull { it.estadoTermico }.maxOrNull()

    fun resumen(): String = buildString {
        append("$modelo | $backend | prefill $prefillSolicitado / decode $decodeSolicitado")
        append(" | rep $repeticion | disipador: ${if (conDisipador) "sí" else "no"}\n")
        if (!exito) {
            append("  FALLÓ: ${error ?: "error desconocido"}\n")
        } else {
            append("  init ${fmt(initSeg)} s | primer token ${fmt(primerTokenSeg)} s | ")
            append("prefill ${fmt(prefillTokS, 1)} tok/s | decode ${fmt(decodeTokS, 1)} tok/s\n")
        }
        append("  RSS pico ${rssPicoMb ?: "?"} MB (VmHWM ${hwmMb ?: "?"}) | ")
        append("RAM libre mín. ${ramLibreMinMb ?: "?"} MB | CPU proceso pico ${fmt(cpuPicoPct, 0)}%\n")
        append("  batería ${fmt(tempInicioC, 1)} → ${fmt(tempFinC, 1)} °C")
        if (tempInicioC != null && tempFinC != null) {
            append(" (Δ ${fmt(tempFinC!! - tempInicioC!!, 1)})")
        }
        append(" | estado térmico máx. ${estadoTermicoMax ?: "?"} | duración ${fmt(duracionSeg, 1)} s")
    }

    fun aJson(): JSONObject = JSONObject().apply {
        put("modelo", modelo)
        put("backend", backend)
        put("inicioEpochMs", inicioEpochMs)
        put("prefillSolicitado", prefillSolicitado)
        put("decodeSolicitado", decodeSolicitado)
        put("repeticion", repeticion)
        put("conDisipador", conDisipador)
        put("exito", exito)
        putOpt("error", error)
        putOpt("initSeg", initSeg?.takeIf { it.isFinite() })
        putOpt("primerTokenSeg", primerTokenSeg?.takeIf { it.isFinite() })
        putOpt("prefillTokens", prefillTokens)
        putOpt("decodeTokens", decodeTokens)
        putOpt("prefillTokS", prefillTokS?.takeIf { it.isFinite() })
        putOpt("decodeTokS", decodeTokS?.takeIf { it.isFinite() })
        putOpt("contextoUsado", contextoUsado)
        put("duracionSeg", duracionSeg.finito())
        put("muestras", JSONArray(muestras.map { it.aJson() }))
    }
}
