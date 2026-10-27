package com.miasistente.app.comandos

import android.content.Context
import com.miasistente.app.data.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Punto de entrada único para cualquier comando de texto (hablado o escrito).
 *
 *   texto → InterpreteComandos → ¿local? → EjecutorLocal (sin internet)
 *                              → ¿IA?    → GeminiClient (con internet y API Key)
 *
 * El servicio de voz todavía NO lo llama: en la próxima fase, al detectar la palabra clave,
 * se transcribirá el comando y se enviará aquí con `procesar(texto)`.
 */
class ProcesadorComandos(context: Context) {

    private val appContext = context.applicationContext
    private val local = EjecutorLocal(appContext)
    private val almacen = SecureStorage(appContext)
    private val gemini = GeminiClient(almacen)

    suspend fun procesar(texto: String): ResultadoComando {
        if (texto.isBlank()) return ResultadoComando(false, "No escuché ningún comando.")

        return when (val comando = InterpreteComandos.interpretar(texto)) {
            is Comando.ConsultaIA -> consultarGemini(comando.pregunta)
            else -> local.ejecutar(comando)
        }
    }

    private suspend fun consultarGemini(pregunta: String): ResultadoComando {
        if (!Conectividad.hayInternet(appContext)) {
            return ResultadoComando(false, "Sin internet: esta consulta necesita Gemini.")
        }
        // Leer el almacén cifrado puede tardar: fuera del hilo principal
        val hayClave = withContext(Dispatchers.IO) { almacen.tieneApiKey() }
        if (!hayClave) {
            return ResultadoComando(false, "Falta la API Key de Gemini (guárdala en Capacidades).")
        }
        return gemini.preguntar(pregunta).fold(
            onSuccess = { ResultadoComando(true, it) },
            onFailure = { ResultadoComando(false, "Gemini: ${it.message ?: "error desconocido"}") }
        )
    }
}
