package com.miasistente.app.laboratorio

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.benchmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ejecuta el benchmark oficial de LiteRT-LM (cada llamada crea su propio motor) mientras otra
 * corrutina muestrea RAM, CPU del proceso, temperatura y estado térmico.
 *
 * IMPORTANTE: aquí NO hay respaldo automático. Si pides NPU y no funciona, el resultado sale como
 * fallido con el error real, para no confundir una medición de GPU con una de NPU.
 */
@OptIn(ExperimentalApi::class)
class PruebaRendimiento(contexto: Context) {

    private val context = contexto.applicationContext

    suspend fun ejecutar(
        modelo: File,
        nombreModelo: String,
        backendNombre: String,
        prefill: Int,
        decode: Int,
        repeticion: Int,
        conDisipador: Boolean,
        alMuestra: (MuestraSistema) -> Unit = {}
    ): ResultadoPrueba = coroutineScope {
        val muestreador = MuestreadorSistema(context)
        val muestras = mutableListOf<MuestraSistema>()
        val inicioEpoch = System.currentTimeMillis()
        val t0 = SystemClock.elapsedRealtime()

        val lector = launch(Dispatchers.Default) {
            muestreador.muestrear { m ->
                synchronized(muestras) { muestras.add(m) }
                alMuestra(m)
            }
        }

        var info: BenchmarkInfo? = null
        var error: String? = null
        try {
            info = withContext(Dispatchers.Default) {
                benchmark(
                    modelPath = modelo.absolutePath,
                    backend = crearBackend(backendNombre),
                    prefillTokens = prefill,
                    decodeTokens = decode,
                    cacheDir = context.cacheDir.absolutePath,
                    maxNumTokens = prefill + decode + MARGEN_CONTEXTO
                )
            }
        } catch (e: CancellationException) {
            lector.cancel()
            throw e
        } catch (e: Throwable) {
            error = "${e::class.java.simpleName}: ${e.message ?: "sin detalle"}"
        }

        lector.cancelAndJoin()
        synchronized(muestras) { muestras.add(muestreador.tomarMuestra()) }
        val duracion = (SystemClock.elapsedRealtime() - t0) / 1000.0
        val resultado = info

        ResultadoPrueba(
            modelo = nombreModelo,
            backend = backendNombre,
            inicioEpochMs = inicioEpoch,
            prefillSolicitado = prefill,
            decodeSolicitado = decode,
            repeticion = repeticion,
            conDisipador = conDisipador,
            exito = error == null && resultado != null,
            error = error,
            initSeg = resultado?.initTimeInSecond,
            primerTokenSeg = resultado?.timeToFirstTokenInSecond,
            prefillTokens = resultado?.lastPrefillTokenCount,
            decodeTokens = resultado?.lastDecodeTokenCount,
            prefillTokS = resultado?.lastPrefillTokensPerSecond,
            decodeTokS = resultado?.lastDecodeTokensPerSecond,
            contextoUsado = resultado?.maxNumTokens?.takeIf { it > 0 },
            duracionSeg = duracion,
            muestras = synchronized(muestras) { muestras.toList() }
        )
    }

    private fun crearBackend(nombre: String): Backend = when (nombre) {
        "GPU" -> Backend.GPU()
        // Requiere que las librerías de NPU del fabricante vayan dentro de la app y un modelo
        // compilado para esa NPU. Sin eso fallará y el error quedará registrado.
        "NPU" -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU(threadCount = HILOS_CPU)
    }

    private companion object {
        const val HILOS_CPU = 4 // igual que ModeloLocal, para que la medición sea comparable
        const val MARGEN_CONTEXTO = 64
    }
}
