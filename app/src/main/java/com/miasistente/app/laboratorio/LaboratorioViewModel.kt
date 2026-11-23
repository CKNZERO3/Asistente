package com.miasistente.app.laboratorio

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miasistente.app.data.InteligenciaConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LaboratorioViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>().applicationContext
    private val prueba = PruebaRendimiento(app)
    private var trabajo: Job? = null

    val modelos = mutableStateListOf<File>()
    var modeloSel by mutableStateOf<File?>(null)
    var backend by mutableStateOf("CPU")
    var prefill by mutableStateOf(256)
    var decode by mutableStateOf(128)
    var repeticiones by mutableStateOf(3)
    var minutosSostenida by mutableStateOf(10)
    var disipador by mutableStateOf(false)

    var corriendo by mutableStateOf(false)
    var estado by mutableStateOf("Listo")
    var ultimaMuestra by mutableStateOf<MuestraSistema?>(null)
    val resultados = mutableStateListOf<ResultadoPrueba>()

    // --- Modelos -----------------------------------------------------------------------------

    /** Mismo archivo que usa ModeloLocal (se repite aquí para no tocar esa clase). */
    private fun instalado() = File(ctx.filesDir, "modelo_local/modelo_local.litertlm")

    /** Carpeta para copiar más modelos por adb sin reemplazar el instalado en Iris. */
    fun carpetaModelosExterna(): File? = ctx.getExternalFilesDir("modelos")?.also { it.mkdirs() }

    fun refrescarModelos() {
        val lista = mutableListOf<File>()
        val base = instalado()
        if (base.isFile) lista += base
        carpetaModelosExterna()
            ?.listFiles { f -> f.isFile && f.name.endsWith(".litertlm", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.let { lista += it }
        modelos.clear()
        modelos.addAll(lista)
        val actual = modeloSel
        if (actual == null || actual !in lista) modeloSel = lista.firstOrNull()
    }

    fun nombre(f: File): String =
        if (f == instalado()) {
            "instalado:" + InteligenciaConfig(ctx).nombreModeloLocalInstalado.ifBlank { f.name }
        } else {
            f.name
        }

    fun tamanoMb(f: File): Long = f.length() / (1024L * 1024L)

    // --- Ejecución ---------------------------------------------------------------------------

    fun iniciar(sostenida: Boolean) {
        val modelo = modeloSel ?: run {
            estado = "Elige un modelo primero"
            return
        }
        if (corriendo) return
        corriendo = true
        trabajo = viewModelScope.launch {
            try {
                if (!sostenida) {
                    for (rep in 1..repeticiones) {
                        estado = "Ejecutando $rep/$repeticiones…"
                        val r = ejecutarUna(modelo, rep)
                        resultados.add(r)
                        if (!r.exito) break
                    }
                } else {
                    val limite = SystemClock.elapsedRealtime() + minutosSostenida * 60_000L
                    var n = 0
                    while (SystemClock.elapsedRealtime() < limite) {
                        n++
                        val restante = (limite - SystemClock.elapsedRealtime()) / 1000
                        estado = "Prueba sostenida: ejecución $n (quedan ${restante}s)…"
                        val r = ejecutarUna(modelo, n)
                        resultados.add(r)
                        if (!r.exito) break
                    }
                }
                estado = "Terminado"
            } catch (e: CancellationException) {
                estado = "Detenido"
                throw e
            } finally {
                corriendo = false
            }
        }
    }

    private suspend fun ejecutarUna(modelo: File, rep: Int): ResultadoPrueba =
        prueba.ejecutar(
            modelo = modelo,
            nombreModelo = nombre(modelo),
            backendNombre = backend,
            prefill = prefill,
            decode = decode,
            repeticion = rep,
            conDisipador = disipador
        ) { ultimaMuestra = it }

    fun detener() {
        // Una llamada nativa en curso no se puede interrumpir: se detiene al terminar esa ejecución.
        estado = "Deteniendo… (termina la ejecución en curso)"
        trabajo?.cancel()
    }

    fun borrarResultados() {
        if (!corriendo) resultados.clear()
    }

    // --- Exportación -------------------------------------------------------------------------

    private fun infoDispositivo(): JSONObject {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return JSONObject().apply {
            put("fabricante", Build.MANUFACTURER)
            put("modelo", Build.MODEL)
            put("hardware", Build.HARDWARE)
            put("board", Build.BOARD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                put("socFabricante", Build.SOC_MANUFACTURER)
                put("socModelo", Build.SOC_MODEL)
            }
            put("androidSdk", Build.VERSION.SDK_INT)
            put("ramTotalMb", mem.totalMem / (1024L * 1024L))
            put("abis", Build.SUPPORTED_ABIS.joinToString())
            put("litertlm", "0.19.1") // versión fijada en app/build.gradle.kts
        }
    }

    fun resumenTexto(): String = buildString {
        appendLine("=== Laboratorio de rendimiento · Iris ===")
        appendLine(
            "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · ${Build.HARDWARE} · " +
                "Android SDK ${Build.VERSION.SDK_INT}"
        )
        appendLine("LiteRT-LM 0.19.1 · pruebas: ${resultados.size}")
        appendLine()
        resultados.forEachIndexed { i, r ->
            appendLine("[${i + 1}] ${r.resumen()}")
            appendLine()
        }
    }

    fun exportarJson(): String = JSONObject().apply {
        put("generado", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        put("dispositivo", infoDispositivo())
        put("pruebas", JSONArray(resultados.map { it.aJson() }))
    }.toString(2)

    fun nombreArchivoSugerido(): String =
        "iris_rendimiento_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"

    fun guardarEn(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(exportarJson().toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                estado = if (ok) "Archivo guardado" else "No pude guardar el archivo"
            }
        }
    }
}
