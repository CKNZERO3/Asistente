package com.miasistente.app.comandos

import android.content.Context
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

class ProcesadorComandos(context: Context) {

    private val appContext = context.applicationContext
    private val local = EjecutorLocal(appContext)
    private val almacen = SecureStorage(appContext)
    private val gemini = GeminiClient(almacen)
    private val ajustes = Ajustes(appContext)

    suspend fun procesar(texto: String): ResultadoComando {
        if (texto.isBlank()) return ResultadoComando(false, "No escuché ningún comando.")

        // Limpiar la palabra clave del inicio si viene en el texto transcrito
        val palabra = ajustes.palabraClave
        var comandoTexto = texto.trim()
        val limpioBase = sinAcentos(comandoTexto).lowercase()
        val palabraLimpia = sinAcentos(palabra).lowercase()
        if (limpioBase.startsWith(palabraLimpia)) {
            comandoTexto = comandoTexto.substring(palabraLimpia.length).trimStart(',', ' ', '.', ';')
        }

        return when (val comando = InterpreteComandos.interpretar(comandoTexto)) {
            is Comando.ConsultaIA -> consultarGemini(comando.pregunta)
            else -> local.ejecutar(comando)
        }
    }

    private suspend fun consultarGemini(pregunta: String): ResultadoComando {
        if (!Conectividad.hayInternet(appContext)) {
            return ResultadoComando(false, "Sin internet: esta consulta necesita Gemini.")
        }
        val hayClave = withContext(Dispatchers.IO) { almacen.tieneApiKey() }
        if (!hayClave) {
            return ResultadoComando(false, "Falta la API Key de Gemini (guárdala en Capacidades).")
        }
        return gemini.preguntar(pregunta).fold(
            onSuccess = { ResultadoComando(true, it) },
            onFailure = { ResultadoComando(false, "Gemini: ${it.message ?: "error desconocido"}") }
        )
    }

    private fun sinAcentos(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
