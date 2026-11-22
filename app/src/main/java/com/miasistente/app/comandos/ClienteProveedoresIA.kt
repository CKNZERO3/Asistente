package com.miasistente.app.comandos

import com.miasistente.app.data.ProveedorIA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cliente HTTP multi-proveedor (Google Gemini API directa, OpenAI, Groq, Ollama/Local)
 * que opera con las claves cifradas en [com.miasistente.app.data.SecureStorage].
 */
class ClienteProveedoresIA {

    suspend fun consultar(
        proveedor: ProveedorIA,
        endpoint: String,
        modelo: String,
        apiKey: String,
        pregunta: String,
        instruccionesExtra: String = "",
        permitirBusquedaWeb: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            when (proveedor) {
                ProveedorIA.GEMINI_DIRECTO -> consultarGeminiDirecto(
                    baseUrl = normalizarBaseGemini(endpoint),
                    modelo = modelo.ifBlank { proveedor.modeloDefecto },
                    apiKey = apiKey,
                    pregunta = pregunta,
                    instruccionesExtra = instruccionesExtra,
                    permitirBusquedaWeb = permitirBusquedaWeb
                )
                ProveedorIA.OPENAI_COMPATIBLE, ProveedorIA.OLLAMA_LOCAL -> consultarOpenAICompatible(
                    endpoint = normalizarEndpointOpenAI(endpoint.ifBlank { proveedor.endpointDefecto }),
                    modelo = modelo.ifBlank { proveedor.modeloDefecto },
                    apiKey = apiKey,
                    requiereKey = proveedor.requiereApiKey,
                    pregunta = pregunta,
                    instruccionesExtra = instruccionesExtra
                )
                else -> error("Proveedor no compatible con consulta externa directa.")
            }
        }
    }

    suspend fun probarConexion(
        proveedor: ProveedorIA,
        endpoint: String,
        modelo: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val inicio = System.currentTimeMillis()
        consultar(
            proveedor = proveedor,
            endpoint = endpoint,
            modelo = modelo,
            apiKey = apiKey,
            pregunta = "Responde únicamente con: OK",
            instruccionesExtra = "",
            permitirBusquedaWeb = false
        ).map {
            val ms = System.currentTimeMillis() - inicio
            "Conexión verificada (${ms} ms)"
        }
    }

    private fun consultarGeminiDirecto(
        baseUrl: String,
        modelo: String,
        apiKey: String,
        pregunta: String,
        instruccionesExtra: String,
        permitirBusquedaWeb: Boolean
    ): String {
        if (apiKey.isBlank()) {
            throw HttpException(401, "Falta configurar tu API Key de Google Gemini.", modelo)
        }
        return try {
            llamarGeminiDirecto(
                baseUrl = baseUrl,
                modelo = modelo,
                apiKey = apiKey,
                pregunta = pregunta,
                instruccionesExtra = instruccionesExtra,
                conBusqueda = permitirBusquedaWeb,
                conUtilidades = true
            )
        } catch (e: HttpException) {
            if (e.codigo == 400) {
                llamarGeminiDirecto(
                    baseUrl = baseUrl,
                    modelo = modelo,
                    apiKey = apiKey,
                    pregunta = pregunta,
                    instruccionesExtra = instruccionesExtra,
                    conBusqueda = false,
                    conUtilidades = false
                )
            } else {
                throw e
            }
        }
    }

    private fun llamarGeminiDirecto(
        baseUrl: String,
        modelo: String,
        apiKey: String,
        pregunta: String,
        instruccionesExtra: String,
        conBusqueda: Boolean,
        conUtilidades: Boolean = true
    ): String {
        val cuerpo = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", construirSystemPrompt(instruccionesExtra)))
                )
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", pregunta)))
                )
            )
            // Nivel 2: Búsqueda web + Function Calling nativo de utilidades de Iris
            val toolsArray = JSONArray()
            if (conBusqueda) {
                toolsArray.put(JSONObject().put("google_search", JSONObject()))
            }
            if (conUtilidades) {
                toolsArray.put(
                    JSONObject().put(
                        "functionDeclarations",
                        RegistroUtilidades.generarFunctionDeclarationsGemini()
                    )
                )
            }
            if (toolsArray.length() > 0) {
                put("tools", toolsArray)
            }
        }

        val url = "$baseUrl/$modelo:generateContent"
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONEXION_MS
            readTimeout = TIMEOUT_LECTURA_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }

        try {
            conexion.outputStream.use {
                it.write(cuerpo.toString().toByteArray(Charsets.UTF_8))
            }
            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                val detalle = (conexion.errorStream ?: conexion.inputStreamOrNull())
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw HttpException(codigo, extraerError(detalle) ?: "Error HTTP $codigo", modelo)
            }
            val respuesta = conexion.inputStream.bufferedReader().use { it.readText() }
            return extraerTextoGemini(respuesta)
        } finally {
            conexion.disconnect()
        }
    }

    private fun consultarOpenAICompatible(
        endpoint: String,
        modelo: String,
        apiKey: String,
        requiereKey: Boolean,
        pregunta: String,
        instruccionesExtra: String
    ): String {
        if (requiereKey && apiKey.isBlank()) {
            throw HttpException(401, "Falta configurar la API Key del proveedor.", modelo)
        }
        val mensajes = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", construirSystemPrompt(instruccionesExtra))
            )
            put(
                JSONObject()
                    .put("role", "user")
                    .put("content", pregunta)
            )
        }
        val cuerpo = JSONObject().apply {
            put("model", modelo)
            put("messages", mensajes)
            put("temperature", 0.5)
        }

        val conexion = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_CONEXION_MS
            readTimeout = TIMEOUT_LECTURA_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }

        try {
            conexion.outputStream.use {
                it.write(cuerpo.toString().toByteArray(Charsets.UTF_8))
            }
            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                val detalle = (conexion.errorStream ?: conexion.inputStreamOrNull())
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw HttpException(codigo, extraerError(detalle) ?: "Error HTTP $codigo", modelo)
            }
            val respuesta = conexion.inputStream.bufferedReader().use { it.readText() }
            return extraerTextoOpenAI(respuesta)
        } finally {
            conexion.disconnect()
        }
    }

    private fun construirSystemPrompt(instruccionesExtra: String): String {
        val formato = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es"))
        val base = "Eres Iris, el asistente personal del usuario en su teléfono. " +
            "Responde siempre en español, con claridad y naturalidad. " +
            "Habla en primera persona como Iris. " +
            "Fecha y hora actuales del usuario: ${formato.format(Date())}."
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

    companion object {
        private const val TIMEOUT_CONEXION_MS = 15_000
        private const val TIMEOUT_LECTURA_MS = 60_000

        fun normalizarBaseGemini(raw: String): String {
            val limpia = raw.trim().trimEnd('/')
            return if (limpia.isBlank()) "https://generativelanguage.googleapis.com/v1beta/models"
            else limpia
        }

        fun normalizarEndpointOpenAI(raw: String): String {
            val limpia = raw.trim().trimEnd('/')
            if (limpia.isBlank()) return "https://api.openai.com/v1/chat/completions"
            return if (limpia.endsWith("/chat/completions")) limpia
            else if (limpia.endsWith("/v1")) "$limpia/chat/completions"
            else limpia
        }

        fun extraerTextoGemini(json: String): String {
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

            if (texto.isEmpty()) throw IOException("El proveedor no devolvió texto utilizable.")
            return texto
        }

        fun extraerTextoOpenAI(json: String): String {
            val texto = JSONObject(json)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()

            if (texto.isEmpty()) throw IOException("El proveedor no devolvió texto utilizable.")
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
}
