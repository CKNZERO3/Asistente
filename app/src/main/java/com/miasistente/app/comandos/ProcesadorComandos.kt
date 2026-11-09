package com.miasistente.app.comandos

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.miasistente.app.accesibilidad.IrisAccessibilityService
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.SecureStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        // Patrón para lectura de pantalla bajo demanda
        if (patronLeerPantalla.containsMatchIn(normalizar(comando))) return leerPantalla()

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

    private val patronLeerPantalla =
        Regex("""\b(?:lee|leer|leeme|lea)\b.*\b(?:pantalla|pagina|web|sitio|contenido)\b""")

    private fun quitarPalabraClave(texto: String, palabra: String): String {
        val original = texto.trim()
        val norm = normalizar(original)
        val p = normalizar(palabra)
        if (p.isEmpty()) return original
        val m = Regex("""^(?:(?:oye|hey|ok)\s+)?${Regex.escape(p)}\b[\s,.:;!?-]*""").find(norm)
            ?: return original
        return (if (original.length == norm.length) original else norm).substring(m.value.length)
    }

    private suspend fun leerPantalla(): ResultadoComando {
        if (!IrisAccessibilityService.estaActivo()) {
            try {
                appContext.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // Sin pantalla de ajustes disponible
            }
            return ResultadoComando(false, "Activa el servicio de accesibilidad de Iris para poder leer la pantalla.")
        }
        val texto = IrisAccessibilityService.textoVisible()?.take(8_000)
            ?: return ResultadoComando(false, "No encontré texto visible en la pantalla.")
        return consultarGemini(
            "Este es el texto visible en la pantalla del usuario. " +
                "Léelo y resúmelo en español de forma breve y clara:\n\n$texto"
        )
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
