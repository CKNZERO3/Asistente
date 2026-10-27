package com.miasistente.app.comandos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.miasistente.app.data.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Respuesta de error de la API (código HTTP + mensaje). */
class HttpException(val codigo: Int, mensaje: String) : IOException(mensaje)

/** ¿Hay internet de verdad? */
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

/**
 * Cliente mínimo de la API REST de Gemini.
 *
 * - La API Key sale de SecureStorage y viaja en un encabezado (nunca en la URL, para que no
 *   quede en registros).
 * - [MODELO_POR_DEFECTO] puede quedar obsoleto con el tiempo: si Gemini responde "model not
 *   found", cambia este nombre por uno vigente (Google AI Studio muestra la lista).
 * - Se intenta con búsqueda de Google activada; si el modelo no la admite (error 400) se
 *   reintenta sin ella.
 */
class GeminiClient(
    private val almacen: SecureStorage,
    private val modelo: String = MODELO_POR_DEFECTO
) {

    companion object {
        const val MODELO_POR_DEFECTO = "gemini-2.5-flash"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val INSTRUCCION =
            "Eres un asistente de voz en un teléfono. Responde siempre en español, de forma " +
                "clara y breve (máximo 3 frases), sin formato Markdown ni listas."
    }

    suspend fun preguntar(pregunta: String): Result<String> = withContext(Dispatchers.IO) {
        val clave = almacen.obtenerApiKey()
            ?: return@withContext Result.failure(IllegalStateException("Falta la API Key de Gemini."))
        try {
            Result.success(llamar(clave, pregunta, conBusqueda = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.codigo == 400) {
                try {
                    Result.success(llamar(clave, pregunta, conBusqueda = false))
                } catch (e2: Exception) {
                    Result.failure(e2)
                }
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun llamar(clave: String, pregunta: String, conBusqueda: Boolean): String {
        val cuerpo = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", INSTRUCCION)))
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

        val conexion = (URL("$BASE/$modelo:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", clave)
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }

            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                val detalle = conexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw HttpException(codigo, extraerError(detalle) ?: "Error HTTP $codigo")
            }
            val respuesta = conexion.inputStream.bufferedReader().use { it.readText() }
            return extraerTexto(respuesta)
        } finally {
            conexion.disconnect()
        }
    }

    private fun extraerTexto(json: String): String {
        val partes = JSONObject(json)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val texto = buildString {
            if (partes != null) {
                for (i in 0 until partes.length()) {
                    append(partes.optJSONObject(i)?.optString("text").orEmpty())
                }
            }
        }.trim()
        if (texto.isEmpty()) throw IOException("Gemini no devolvió texto.")
        return texto
    }

    private fun extraerError(json: String): String? = try {
        JSONObject(json).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}
