package com.miasistente.app.comandos

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.miasistente.app.accesibilidad.IrisAccessibilityService
import com.miasistente.app.data.Ajustes
import kotlinx.coroutines.CancellationException

class ProcesadorComandos(context: Context) {

    private val appContext = context.applicationContext
    private val parser = IntentParser()
    private val local = EjecutorLocal(appContext)
    private val gemini = GeminiClient()
    private val ajustes = Ajustes(appContext)

    suspend fun procesar(
        texto: String,
        palabraClave: String = ajustes.palabraClave
    ): ResultadoComando {
        val comando = quitarPalabraClave(texto, palabraClave)
        if (comando.isBlank()) {
            return ResultadoComando(false, "No escuché ningún comando.")
        }

        if (patronLeerPantalla.containsMatchIn(normalizar(comando))) {
            return leerPantalla()
        }

        return try {
            when (val intencion = parser.parsear(comando)) {
                is Intencion.ConsultaIA -> consultarIris(intencion.pregunta)
                else -> local.ejecutar(intencion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoComando(false, "No pude completar esa acción.")
        }
    }

    private val patronLeerPantalla =
        Regex("""\b(?:lee|leer|leeme|lea)\b.*\b(?:pantalla|pagina|web|sitio|contenido)\b""")

    private fun quitarPalabraClave(texto: String, palabra: String): String {
        val original = texto.trim()
        val norm = normalizar(original)
        val p = normalizar(palabra)
        if (p.isEmpty()) return original

        val m = Regex(
            """^(?:(?:oye|hey|ok|mira)\s+)?${Regex.escape(p)}\b[\s,.:;!?-]*"""
        ).find(norm) ?: return original

        return (if (original.length == norm.length) original else norm).substring(m.value.length)
    }

    private suspend fun leerPantalla(): ResultadoComando {
        if (!IrisAccessibilityService.estaActivo()) {
            try {
                appContext.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // El mensaje siguiente explica cómo habilitar el permiso.
            }
            return ResultadoComando(
                false,
                "Activa el servicio de accesibilidad de Iris para poder leer la pantalla."
            )
        }

        val texto = IrisAccessibilityService.textoVisible()?.take(8_000)
            ?: return ResultadoComando(false, "No encontré texto visible en la pantalla.")

        return consultarIris(
            "Este es el texto visible en la pantalla del usuario. " +
                "Léelo y resúmelo en español de forma breve y clara:\n\n$texto"
        )
    }

    private suspend fun consultarIris(pregunta: String): ResultadoComando {
        if (!Conectividad.hayInternet(appContext)) {
            return ResultadoComando(false, "Ahora mismo no tengo conexión a internet.")
        }

        val seleccion = IrisModelSelector.seleccionar(pregunta)

        return gemini.preguntar(pregunta, seleccion.modelo).fold(
            onSuccess = { ResultadoComando(true, it) },
            onFailure = { ResultadoComando(false, mensajeAmigable(it)) }
        )
    }

    private fun mensajeAmigable(error: Throwable): String = when {
        error is HttpException && error.codigo == 429 ->
            "Estoy atendiendo muchas consultas en este momento. Inténtalo de nuevo en unos segundos."
        error is HttpException && error.codigo in 401..403 ->
            "No tengo autorización para completar la consulta en este momento."
        error is HttpException && error.codigo == 404 ->
            "Mi servicio de inteligencia no encontró un modelo disponible para esta solicitud."
        error is HttpException && error.codigo >= 500 ->
            "Tuve un problema temporal al preparar la respuesta. Inténtalo de nuevo."
        error is java.io.IOException && error.message?.contains("texto utilizable") == true ->
            "No pude preparar una respuesta esta vez. ¿Puedes intentarlo de otra manera?"
        else ->
            "No pude completar la consulta. Inténtalo de nuevo."
    }
}
