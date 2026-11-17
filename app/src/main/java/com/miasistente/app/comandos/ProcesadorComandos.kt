package com.miasistente.app.comandos

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.miasistente.app.BuildConfig
import com.miasistente.app.accesibilidad.IrisAccessibilityService
import com.miasistente.app.data.Ajustes
import com.miasistente.app.memoria.MemoriaLocal
import kotlinx.coroutines.CancellationException

class ProcesadorComandos(context: Context) {

    private val appContext = context.applicationContext
    private val parser = IntentParser()
    private val local = EjecutorLocal(appContext)
    private val gemini = GeminiClient()
    private val habilidades = HabilidadesLocales()
    private val clima = ClimaOpenMeteo()
    private val modeloLocal = ModeloLocal(context)
    private val ajustes = Ajustes(appContext)
    private val notas = GestorNotas(appContext)
    private val memoria = MemoriaLocal.get(appContext)

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
            ComandosMemoria.interpretar(comando)?.let { accion ->
                responderMemoria(accion)?.let { return it }
            }
            when (val intencion = parser.parsear(comando)) {
                is Intencion.ConsultaIA -> responderConsulta(intencion.pregunta)
                // Solo el contenido (sin las palabras de la orden) y en el destino pedido.
                is Intencion.CrearNota -> notas.guardar(
                    ExtractorNota.analizar(comando)
                        ?: PeticionGuardar(ExtractorNota.limpiar(intencion.contenido), DestinoNota.SinEspecificar)
                )
                is Intencion.AbrirAplicacion -> local.ejecutar(intencion).also {
                    // Apps favoritas: se aprende en segundo plano, sin latencia.
                    if (it.exito) memoria.registrarHabito(CONTEXTO_APPS, normalizar(intencion.nombreApp))
                }
                else -> local.ejecutar(intencion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoComando(false, "No pude completar esa acción.")
        }
    }

    /**
     * "Guarda esta imagen en el cuaderno Recetas" con adjuntos. Devuelve null si el texto no es
     * una orden de guardar, y entonces la pantalla sigue con el flujo normal de adjuntos.
     */
    suspend fun guardarAdjuntos(texto: String, adjuntos: List<AdjuntoNota>): ResultadoComando? {
        if (adjuntos.isEmpty()) return null
        val comando = quitarPalabraClave(texto, ajustes.palabraClave)
        val peticion = ExtractorNota.analizar(comando) ?: return null
        return notas.guardar(peticion, adjuntos)
    }

    /** Memoria esencial y seguimiento de notas: todo local, sin internet. */
    private suspend fun responderMemoria(accion: AccionMemoria): ResultadoComando? = when (accion) {
        AccionMemoria.NoSeGuardo -> notas.reintentarUltima()
        is AccionMemoria.Recordar -> {
            memoria.recordar(accion.hecho)
            ResultadoComando(true, "Entendido, lo recordaré: ${accion.hecho}")
        }
        is AccionMemoria.Consultar -> {
            val encontrados = if (accion.tema == null) memoria.hechos() else memoria.buscar(accion.tema)
            if (encontrados.isEmpty()) {
                ResultadoComando(
                    true,
                    if (accion.tema == null) "Todavía no me has pedido que recuerde nada. Dime «recuerda que…»."
                    else "No tengo nada guardado sobre «${accion.tema}»."
                )
            } else {
                ResultadoComando(true, "Esto es lo que recuerdo:\n" + encontrados.joinToString("\n") { "• $it" })
            }
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

    /**
     * Antes de gastar cuota de Gemini se intenta resolver la consulta en el teléfono
     * (hora, fecha, cálculos), con un servicio gratuito (clima) o con Gemma 3 1B local.
     */
    private suspend fun responderConsulta(pregunta: String): ResultadoComando {
        habilidades.responder(pregunta)?.let { return ResultadoComando(true, it) }
        habilidades.peticionClima(pregunta)?.let { return consultarClima(it) }

        val seleccion = IrisModelSelector.seleccionar(pregunta)
        // Noticias, resultados, precios, personajes…: solo sirve la búsqueda en internet.
        val tiempoReal = IrisModelSelector.requiereTiempoReal(pregunta)
        if (tiempoReal && !Conectividad.hayInternet(appContext)) {
            return ResultadoComando(
                false,
                "Para darte datos actuales (noticias, resultados, precios…) necesito conexión a internet."
            )
        }
        // Solo las tareas LIGERAS van primero al modelo local; las preguntas de datos o
        // conocimiento van a Gemini porque el modelo pequeño inventa respuestas.
        val usarLocalPrimero = modeloLocal.disponible() &&
            seleccion.categoria == CategoriaTarea.LIGERA && !tiempoReal

        if (usarLocalPrimero) {
            modeloLocal.generar(INSTRUCCION_LOCAL, pregunta)
                .onSuccess { return ResultadoComando(true, it) }
        }
        return consultarIris(
            pregunta,
            seleccion.modelo,
            intentarLocalSiFalla = !usarLocalPrimero && !tiempoReal
        )
    }

    /**
     * Resume o analiza un archivo de texto adjunto. Hasta [PoliticaArchivos.MAX_CARACTERES_LOCAL]
     * caracteres lo hace el modelo del teléfono; hasta [PoliticaArchivos.MAX_CARACTERES_NUBE],
     * Gemini; más que eso se rechaza.
     */
    suspend fun resumirArchivo(
        nombre: String,
        lectura: LecturaArchivo,
        instruccionUsuario: String? = null
    ): ResultadoComando {
        val contenido = when (lectura) {
            is LecturaArchivo.Rechazado -> return ResultadoComando(false, lectura.motivo)
            is LecturaArchivo.Texto -> lectura.contenido.trim()
        }
        if (contenido.isEmpty()) return ResultadoComando(false, "«$nombre» está vacío.")
        if (contenido.length > PoliticaArchivos.MAX_CARACTERES_NUBE) {
            return ResultadoComando(
                false,
                "«$nombre» es demasiado largo para resumirlo (límite: ${PoliticaArchivos.MAX_CARACTERES_NUBE} caracteres)."
            )
        }

        val indicacion = instruccionUsuario?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Resume en español, de forma breve y clara, el siguiente archivo"
        val peticion = "$indicacion («$nombre»):\n\n$contenido"

        val cabeEnLocal = contenido.length <= PoliticaArchivos.MAX_CARACTERES_LOCAL && modeloLocal.disponible()
        if (cabeEnLocal) {
            modeloLocal.generar(INSTRUCCION_LOCAL, peticion)
                .onSuccess { return ResultadoComando(true, it) }
        }
        return consultarIris(peticion, IrisModelSelector.MODELO_LIGERO, intentarLocalSiFalla = !cabeEnLocal)
    }

    private suspend fun consultarClima(peticion: PeticionClima): ResultadoComando {
        val ciudad = peticion.ciudad ?: return ResultadoComando(
            false,
            "¿De qué ciudad quieres saber el clima? Por ejemplo: «clima en Caracas»."
        )
        if (!Conectividad.hayInternet(appContext)) {
            return ResultadoComando(false, "Para consultar el clima necesito conexión a internet.")
        }
        return try {
            val datos = clima.consultar(ciudad)
            if (datos == null) {
                ResultadoComando(false, "No encontré una ciudad llamada «$ciudad».")
            } else {
                ResultadoComando(true, ClimaTexto.resumen(datos))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoComando(false, "No pude consultar el clima ahora mismo. Inténtalo de nuevo.")
        }
    }

    private suspend fun consultarIris(
        pregunta: String,
        modelo: String? = null,
        intentarLocalSiFalla: Boolean = true
    ): ResultadoComando {
        if (!Conectividad.hayInternet(appContext)) {
            if (intentarLocalSiFalla && modeloLocal.disponible()) {
                modeloLocal.generar(INSTRUCCION_LOCAL, pregunta)
                    .onSuccess { return ResultadoComando(true, AVISO_SIN_NUBE + it) }
            }
            return ResultadoComando(false, "Ahora mismo no tengo conexión a internet.")
        }

        val elegido = modelo ?: IrisModelSelector.seleccionar(pregunta).modelo

        return gemini.preguntar(pregunta, elegido).fold(
            onSuccess = { ResultadoComando(true, it) },
            onFailure = { errorNube ->
                if (intentarLocalSiFalla && modeloLocal.disponible()) {
                    modeloLocal.generar(INSTRUCCION_LOCAL, pregunta)
                        .onSuccess { return ResultadoComando(true, AVISO_SIN_NUBE + it) }
                }
                ResultadoComando(false, mensajeAmigable(errorNube))
            }
        )
    }

    // Solo en compilaciones debug (las de GitHub Actions) se añade el detalle técnico para
    // poder diagnosticar; en una release firmada el usuario solo ve el mensaje amable.
    private fun mensajeAmigable(error: Throwable): String {
        val base = mensajeBase(error)
        if (!BuildConfig.DEBUG || error !is HttpException) return base
        val detalle = error.message.orEmpty().replace('\n', ' ').take(160)
        return "$base\n[detalle: HTTP ${error.codigo} · ${error.modelo} · $detalle]"
    }

    private fun mensajeBase(error: Throwable): String = when {
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

    private companion object {
        const val CONTEXTO_APPS = "app_abierta"
        const val AVISO_SIN_NUBE =
            "No pude consultar a Gemini; esta respuesta viene de mi modelo local y puede tener errores.\n\n"
    }
}
