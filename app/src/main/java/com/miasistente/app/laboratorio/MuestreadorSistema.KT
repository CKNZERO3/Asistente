package com.miasistente.app.laboratorio

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.io.File

/**
 * Lee el estado del teléfono con APIs públicas de Android.
 *
 * Límite conocido: una app normal NO puede leer el uso de GPU ni de NPU. La NPU se detecta de forma
 * indirecta: comparando tokens/s y el tiempo de CPU del proceso entre backends.
 */
class MuestreadorSistema(contexto: Context) {

    private val context = contexto.applicationContext
    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val inicioMs = SystemClock.elapsedRealtime()
    private var cpuPrevMs = Process.getElapsedCpuTime()
    private var relojPrevMs = inicioMs

    /** Toma una muestra cada [intervaloMs] hasta que se cancele la corrutina que lo llama. */
    suspend fun muestrear(intervaloMs: Long = 1000L, alMuestrear: (MuestraSistema) -> Unit) {
        while (true) {
            alMuestrear(tomarMuestra())
            delay(intervaloMs)
        }
    }

    fun tomarMuestra(): MuestraSistema {
        val ahoraMs = SystemClock.elapsedRealtime()
        val cpuAhoraMs = Process.getElapsedCpuTime()
        val deltaReloj = ahoraMs - relojPrevMs
        val cpuPct = if (deltaReloj > 0) (cpuAhoraMs - cpuPrevMs) * 100.0 / deltaReloj else 0.0
        cpuPrevMs = cpuAhoraMs
        relojPrevMs = ahoraMs

        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dbg = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val (rss, hwm) = leerRss()

        val bateria = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempBateria = bateria
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10.0 }
        val nivel = bateria?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val escala = bateria?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val bateriaPct = if (nivel >= 0 && escala > 0) nivel * 100 / escala else null
        val cargando = (bateria?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val corriente = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Long.MIN_VALUE }

        val estadoTermico = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.currentThermalStatus
        } else {
            null
        }
        val margen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getThermalHeadroom(10).takeIf { !it.isNaN() }?.toDouble()
        } else {
            null
        }

        return MuestraSistema(
            tSeg = (ahoraMs - inicioMs) / 1000.0,
            ramLibreMb = mem.availMem / MB,
            ramTotalMb = mem.totalMem / MB,
            memoriaBaja = mem.lowMemory,
            rssMb = rss,
            hwmMb = hwm,
            pssMb = dbg.totalPss / 1024L,
            cpuProcesoPct = cpuPct,
            bateriaPct = bateriaPct,
            cargando = cargando,
            tempBateriaC = tempBateria,
            corrienteUa = corriente,
            estadoTermico = estadoTermico,
            margenTermico = margen,
            zonasC = leerZonasTermicas()
        )
    }

    /** VmRSS y VmHWM de /proc/self/status, en MB. */
    private fun leerRss(): Pair<Long, Long> {
        var rss = -1L
        var hwm = -1L
        try {
            File("/proc/self/status").forEachLine { linea ->
                when {
                    linea.startsWith("VmRSS:") -> rss = kbAMb(linea)
                    linea.startsWith("VmHWM:") -> hwm = kbAMb(linea)
                }
            }
        } catch (_: Exception) {
        }
        return rss to hwm
    }

    private fun kbAMb(linea: String): Long {
        val kb = linea.filter { it.isDigit() }.toLongOrNull() ?: return -1L
        return kb / 1024L
    }

    /** En la mayoría de teléfonos modernos SELinux bloquea esto para apps normales: queda vacío. */
    private fun leerZonasTermicas(): Map<String, Double> {
        val salida = linkedMapOf<String, Double>()
        val zonas = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
            ?: return salida
        for (zona in zonas.sortedBy { it.name }.take(MAX_ZONAS)) {
            try {
                val tipo = File(zona, "type").readText().trim()
                val bruto = File(zona, "temp").readText().trim().toDoubleOrNull() ?: continue
                val grados = if (bruto > 1000.0) bruto / 1000.0 else bruto
                if (grados in -20.0..150.0) salida["${zona.name}:$tipo"] = grados
            } catch (_: Exception) {
            }
        }
        return salida
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val MAX_ZONAS = 32
    }
}
