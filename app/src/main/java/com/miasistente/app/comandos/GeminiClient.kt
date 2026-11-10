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
        private const val TIMEOUT_CONEXION_MS = 15_000
        private const val TIMEOUT_LECTURA_MS = 60_000

        private const val INSTRUCCION =
            "Eres Iris, el asistente personal del usuario en su teléfono. " +
                "Responde siempre en español, con claridad y naturalidad. " +
                "Habla en primera persona como Iris. No menciones motores, modelos, " +
                "proveedores ni servicios internos. Adapta la extensión a la complejidad " +
                "de la solicitud; no limites una tarea compleja a tres frases."
    }

    suspend fun preguntar(
        pregunta: String,
        modeloSolicitado: String = modelo
    ): Result<String> = withContext(Dispatchers.IO) {
        val modeloInicial = modeloSolicitado
            .takeIf(IrisModelSelector::esModeloPermitido)
            ?: MODELO_POR_DEFECTO
        val candidatos = candidatosFallback(modeloInicial)
        var ultimoError: Throwable? = null

        for (indice in candidatos.indices) {
            val modeloActual = candidatos[indice]
            try {
                return@withContext Result.success(
                    llamarConCompatibilidad(pregunta, modeloActual)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                ultimoError = e
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

    /**
     * Un 429 del propio Worker no se arregla cambiando de modelo: respeta el límite
     * del Worker y no multiplica solicitudes inútiles. Los 429 de Google sí permiten
     * probar una alternativa con cuota disponible.
     */
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
                    IrisModelSelector.MODELO_GENERAL,
                    IrisModelSelector.MODELO_LIGERO
                )
            IrisModelSelector.MODELO_LIGERO ->
                listOf(
                    IrisModelSelector.MODELO_LIGERO,
                    IrisModelSelector.MODELO_GENERAL
                )
            else ->
                listOf(
                    IrisModelSelector.MODELO_GENERAL,
                    IrisModelSelector.MODELO_LIGERO
                )
        }.distinct()

    /**
     * Algunos modelos o proyectos pueden no aceptar Google Search en una solicitud.
     * Ante un HTTP 400 se reintenta una sola vez sin esa herramienta.
     */
    private fun llamarConCompatibilidad(pregunta: String, modelo: String): String =
        try {
            llamar(pregunta, modelo, conBusqueda = true)
        } catch (e: HttpException) {
            if (e.codigo != 400) throw e
            Log.i(TAG, "Se reintentará $modelo sin Google Search.")
            llamar(pregunta, modelo, conBusqueda = false)
        }

    private fun llamar(
        pregunta: String,
        modelo: String,
        conBusqueda: Boolean
    ): String {
        val cuerpo = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", INSTRUCCION))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", pregunta)))
                )
            )
            if (conBusqueda) {
                put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
        }

        val url = "$BASE/$modelo:generateContent"
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONEXION_MS
            readTimeout = TIMEOUT_LECTURA_MS
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
