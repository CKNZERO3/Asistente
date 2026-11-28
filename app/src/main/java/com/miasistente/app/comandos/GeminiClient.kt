package com.miasistente.app.comandos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.miasistente.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(
    val codigo: Int,
    mensaje: String,
    val modelo: String
) : IOException(mensaje) {
    constructor(codigo: Int, mensaje: String) :
        this(codigo, mensaje, IrisModelSelector.MODELO_GENERAL)
}

object Conectividad {
    fun hayInternet(context: Context): Boolean {
        val gestor = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return false
        val red = gestor.activeNetwork ?: return false
        val capacidades = gestor.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class GeminiClient(
    private val modelo: String = IrisModelSelector.MODELO_GENERAL
) {

    companion object {
        const val MODELO_POR_DEFECTO = IrisModelSelector.MODELO_GENERAL
        private const val BASE = "https://asistente.cristian-nava-ruido.workers.dev/v1beta/models"
        private const val TAG = "IrisGeminiClient"
        private val cuota = CuotaModelos()
        private const val TIMEOUT_CONEXION_MS = 8_000
        private const val TIMEOUT_LECTURA_FLASH_MS = 12_000
        private const val TIMEOUT_LECTURA_COMPLEJA_MS = 30_000
        private const val MAX_MENSAJES_HISTORIAL = 6

        private const val INSTRUCCION =
            "Eres Iris, el asistente personal del usuario en su teléfono. " +
                "Responde siempre en español, con claridad y naturalidad. " +
                "Habla en primera persona como Iris. No menciones motores, modelos, " +
                "proveedores ni servicios internos. Adapta la extensión a la complejidad " +
                "de la solicitud; no limites una tarea compleja a tres frases."
    }

    private val historialLock = Any()
    private val historial = ArrayDeque<Pair<String, String>>(MAX_MENSAJES_HISTORIAL)

    fun limpiarHistorial() {
        synchronized(historialLock) {
            historial.clear()
        }
    }

    private fun snapshotHistorial(): List<Pair<String, String>> =
        synchronized(historialLock) { historial.toList() }

    private fun registrarTurno(preguntaUsuario: String, respuestaModelo: String) {
        synchronized(historialLock) {
            historial.addLast("user" to preguntaUsuario)
            historial.addLast("model" to respuestaModelo)
            while (historial.size > MAX_MENSAJES_HISTORIAL) {
                historial.removeFirst()
            }
            while (historial.isNotEmpty() && historial.first().first != "user") {
                historial.removeFirst()
            }
        }
    }

    suspend fun preguntar(
        pregunta: String,
        modeloSolicitado: String = modelo,
        instruccionesExtra: String = "",
        permitirBusquedaWeb: Boolean = true,
        esCompleja: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val modeloInicial = modeloSolicitado
            .takeIf(IrisModelSelector::esModeloPermitido)
            ?: if (esCompleja) IrisModelSelector.MODELO_AVANZADO else MODELO_POR_DEFECTO
        val todos = candidatosFallback(modeloInicial)
        val candidatos = todos.filter(cuota::disponible)
        if (candidatos.isEmpty()) {
            val espera = todos.minOf(cuota::segundosRestantes)
            return@withContext Result.failure(
                HttpException(429, "Cuota agotada temporalmente; reintenta en $espera s.", todos.first())
            )
        }
        var ultimoError: Throwable? = null
        val contextoPrevio = snapshotHistorial()

        for (indice in candidatos.indices) {
            val modeloActual = candidatos[indice]
            try {
                val texto = llamarConCompatibilidad(
                    pregunta = pregunta,
                    historialPrevio = contextoPrevio,
                    modelo = modeloActual,
                    instruccionesExtra = instruccionesExtra,
                    permitirBusquedaWeb = permitirBusquedaWeb,
                    esCompleja = esCompleja
                )
                cuota.registrarExito(modeloActual)
                registrarTurno(pregunta, texto)
                return@withContext Result.success(texto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                ultimoError = e
                if (e.codigo == 429 && !e.message.orEmpty().contains("Worker", ignoreCase = true)) {
                    cuota.registrarAgotada(modeloActual)
                }
                val puedeCambiar = indice < candidatos.lastIndex &&
                    debeProbarModeloAlternativo(e)

                if (puedeCambiar) {
                    Log.w(
                        TAG,
                        "Modelo $modeloActual devolvió HTTP ${e.codigo}; se probará una alternativa."
                    )
                    continue
                }

                Log.e(TAG, "Falló el modelo $modeloActual con HTTP ${e.codigo}: ${e.message?.take(240)}")
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo completar la consulta con $modeloActual.", e)
                return@withContext Result.failure(e)
            }
        }

        Result.failure(ultimoError ?: IOException("No hay un modelo disponible."))
    }

    private fun debeProbarModeloAlternativo(error: HttpException): Boolean {
        val mensaje = error.message.orEmpty()
        return when (error.codigo) {
            404 -> !mensaje.contains("Ruta no encontrada", ignoreCase = true)
            429 -> !mensaje.contains("Worker", ignoreCase = true)
            503 -> true
            else -> false
        }
    }

    private fun candidatosFallback(modeloSolicitado: String): List<String> =
        when (modeloSolicitado) {
            IrisModelSelector.MODELO_AVANZADO ->
                listOf(
                    IrisModelSelector.MODELO_AVANZADO,
                    IrisModelSelector.MODELO_AVANZADO_ALTERNO,
                    IrisModelSelector.MODELO_GENERAL,
                    IrisModelSelector.MODELO_LIGERO_ALTERNO
                )
            IrisModelSelector.MODELO_AVANZADO_ALTERNO ->
                listOf(
                    IrisModelSelector.MODELO_AVANZADO_ALTERNO,
                    IrisModelSelector.MODELO_AVANZADO,
                    IrisModelSelector.MODELO_GENERAL,
                    IrisModelSelector.MODELO_LIGERO_ALTERNO
                )
            else ->
                listOf(
                    IrisModelSelector.MODELO_GENERAL,
                    IrisModelSelector.MODELO_LIGERO_ALTERNO,
                    IrisModelSelector.MODELO_AVANZADO,
                    IrisModelSelector.MODELO_AVANZADO_ALTERNO
                )
        }.distinct()

    private fun llamarConCompatibilidad(
        pregunta: String,
        historialPrevio: List<Pair<String, String>>,
        modelo: String,
        instruccionesExtra: String,
        permitirBusquedaWeb: Boolean,
        esCompleja: Boolean
    ): String {
        return try {
            llamar(
                pregunta = pregunta,
                historialPrevio = historialPrevio,
                modelo = modelo,
                instruccionesExtra = instruccionesExtra,
                conBusqueda = permitirBusquedaWeb,
                desactivarThinking = !esCompleja,
                readTimeoutMs = if (esCompleja) TIMEOUT_LECTURA_COMPLEJA_MS else TIMEOUT_LECTURA_FLASH_MS
            )
        } catch (e: HttpException) {
            if (e.codigo != 400) throw e
            Log.i(TAG, "Reintentando $modelo en modo compatible sin herramientas/thinkingConfig.")
            llamar(
                pregunta = pregunta,
                historialPrevio = historialPrevio,
                modelo = modelo,
                instruccionesExtra = instruccionesExtra,
                conBusqueda = false,
                desactivarThinking = false,
                readTimeoutMs = if (esCompleja) TIMEOUT_LECTURA_COMPLEJA_MS else TIMEOUT_LECTURA_FLASH_MS
            )
        }
    }

    private fun llamar(
        pregunta: String,
        historialPrevio: List<Pair<String, String>>,
        modelo: String,
        instruccionesExtra: String,
        conBusqueda: Boolean,
        desactivarThinking: Boolean,
        readTimeoutMs: Int
    ): String {
        val contentsArray = JSONArray().apply {
            for ((rol, textoTurno) in historialPrevio) {
                put(
                    JSONObject()
                        .put("role", rol)
                        .put("parts", JSONArray().put(JSONObject().put("text", textoTurno)))
                )
            }
            put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", pregunta)))
            )
        }

        val cuerpo = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", instruccionConFecha(instruccionesExtra)))
                )
            )
            put("contents", contentsArray)
            if (desactivarThinking) {
                put(
                    "generationConfig",
                    JSONObject().put(
                        "thinkingConfig",
                        JSONObject().put("thinkingBudget", 0)
                    )
                )
            }
            if (conBusqueda) {
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
        }

        val url = "$BASE/$modelo:generateContent"
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONEXION_MS
            readTimeout = readTimeoutMs
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (BuildConfig.APP_TOKEN.isNotBlank()) {
                setRequestProperty("X-App-Token", BuildConfig.APP_TOKEN)
            }
        }

        try {
            conexion.outputStream.use {
                it.write(cuerpo.toString().toByteArray(Charsets.UTF_8))
            }

            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                val flujoError = conexion.errorStream ?: conexion.inputStreamOrNull()
                val detalle = flujoError?.bufferedReader()?.use { it.readText() }.orEmpty()
                val mensaje = extraerError(detalle) ?: "Error HTTP $codigo"
                throw HttpException(codigo, mensaje, modelo)
            }

            val respuesta = conexion.inputStream.bufferedReader().use { it.readText() }
            return extraerTexto(respuesta)
        } finally {
            conexion.disconnect()
        }
    }

    private fun instruccionConFecha(instruccionesExtra: String): String {
        val formato = java.text.SimpleDateFormat(
            "EEEE d 'de' MMMM 'de' yyyy, HH:mm",
            java.util.Locale.forLanguageTag("es")
        )
        val base = "$INSTRUCCION Fecha y hora actuales del usuario: ${formato.format(java.util.Date())}. " +
            "Para noticias, resultados deportivos, precios, personas públicas o datos recientes, " +
            "usa la búsqueda en internet y responde con lo más actual que encuentres."
        val extra = instruccionesExtra.trim()
        return if (extra.isEmpty()) base
        else "$base\n\nInstrucciones personalizadas del usuario (síguelas en tus respuestas):\n$extra"
    }

    private fun HttpURLConnection.inputStreamOrNull() =
        try {
            inputStream
        } catch (_: IOException) {
            null
        }

    private fun extraerTexto(json: String): String {
        val partes = JSONObject(json)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")

        val texto = buildString {
            if (partes != null) {
                for (i in 0 until partes.length()) {
                    val parte = partes.optJSONObject(i) ?: continue
                    append(parte.optString("text").orEmpty())
                }
            }
        }.trim()

        if (texto.isEmpty()) throw IOException("Iris no recibió texto utilizable.")
        return texto
    }

    private fun extraerError(json: String): String? = try {
        JSONObject(json).optJSONObject("error")
            ?.optString("message")
            ?.takeIf { it.isNotBlank() && it != "null" }
    } catch (_: Exception) {
        json.trim().takeIf { it.isNotBlank() }?.take(300)
    }
}
