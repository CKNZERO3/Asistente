package com.miasistente.app.comandos

import android.content.Context
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.miasistente.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val codigo: Int, mensaje: String) : IOException(mensaje)

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
    private val modelo: String = MODELO_POR_DEFECTO
) {

    companion object {
        const val MODELO_POR_DEFECTO = "gemini-2.5-flash"
        // CAMBIA ESTA URL POR LA TUYA EXACTA DE CLOUDFLARE WORKER
        private const val BASE = "https://asistente.cristian-nava-ruido.workers.dev/v1beta/models"
        private const val TAG = "GeminiClient"
        private const val INSTRUCCION =
            "Eres Iris, el asistente personal de voz del usuario en su teléfono. Responde siempre " +
                "en español, de forma clara y breve (máximo 3 frases), sin formato Markdown ni listas. " +
                "Habla en primera persona, como Iris. No menciones el nombre de ningún modelo, " +
                "empresa o proveedor tecnológico a menos que el usuario te lo pregunte directamente."
    }

    suspend fun preguntar(pregunta: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(llamar(pregunta, conBusqueda = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.codigo == 400) {
                try {
                    Result.success(llamar(pregunta, conBusqueda = false))
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

    private fun llamar(pregunta: String, conBusqueda: Boolean): String {
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
            if (BuildConfig.APP_TOKEN.isNotBlank()) setRequestProperty("X-App-Token", BuildConfig.APP_TOKEN)
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }

            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                val detalle = conexion.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "HTTP $codigo: ${detalle.take(200)}")
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
