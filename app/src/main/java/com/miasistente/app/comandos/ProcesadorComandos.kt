package com.miasistente.app.comandos

import android.content.Context
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Punto de entrada único para cualquier comando (hablado o escrito):
 *
 *   texto → quitar palabra clave → IntentParser → ConsultaIA? → GeminiClient (online)
 *                                              → otra       → EjecutorLocal (offline)
 */
class ProcesadorComandos(context: Context) {

    private val appContext = context.applicationContext
    private val parser = IntentParser()
    private val local = EjecutorLocal(appContext)
    private val almacen = SecureStorage(appContext)
    private val gemini = GeminiClient(almacen)
    private val ajustes = Ajustes(appContext)

    suspend fun procesar(texto: String, palabraClave: String = ajustes.palabraClave): ResultadoComando {
        val comando = quitarPalabraClave(texto, palabraClave)
        if (comando.isBlank()) return ResultadoComando(false, "No escuché ningún comando.")

        return try {
            when (val intencion = parser.parsear(comando)) {
                is Intencion.ConsultaIA -> consultarGemini(intencion.pregunta)
                else -> local.ejecutar(intencion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoComando(false, "Error: ${e.message ?: "desconocido"}")
        }
    }

    /** "Oye asistente, abre cámara" → "abre cámara". Sin palabra clave al inicio, no cambia nada. */
    private fun quitarPalabraClave(texto: String, palabra: String): String {
        val original = texto.trim()
        val norm = normalizar(original)
        val p = normalizar(palabra)
        if (p.isEmpty()) return original
        val m = Regex("""^(?:(?:oye|hey|ok)\s+)?${Regex.escape(p)}\b[\s,.:;!?-]*""").find(norm)
            ?: return original
        return (if (original.length == norm.length) original else norm).substring(m.value.length)
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
}
