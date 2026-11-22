package com.miasistente.app.comandos

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import com.miasistente.app.BuildConfig
import com.miasistente.app.accesibilidad.IrisAccessibilityService
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.InteligenciaConfig
import com.miasistente.app.data.ModoEjecucionLocal
import com.miasistente.app.data.PerfilUsuario
import com.miasistente.app.data.ProveedorIA
import com.miasistente.app.memoria.MemoriaLocal
import kotlinx.coroutines.CancellationException

class ProcesadorComandos(context: Context) {

    private val appContext = context.applicationContext
    private val parser = IntentParser()
    private val local = EjecutorLocal(appContext)
    private val gemini = GeminiClient()
    private val clienteProveedores = ClienteProveedoresIA()
    private val habilidades = HabilidadesLocales()
    private val clima = ClimaOpenMeteo()
    private val modeloLocal = ModeloLocal(context)
    private val ajustes = Ajustes(appContext)
    private val perfil = PerfilUsuario(appContext)
    private val notas = GestorNotas(appContext)
    private val memoria = MemoriaLocal.get(appContext)
    private val inteligencia = InteligenciaConfig(appContext)
    private val despachador = DespachadorUtilidades(parser, local, notas, memoria, clima)

    suspend fun procesar(
        texto: String,
        palabraClave: String = ajustes.palabraClave
    ): ResultadoComando {
        val comando = quitarPalabraClave(texto, palabraClave)
        if (comando.isBlank()) {
            return ResultadoComando(false, "No escuché ningún comando.")
        }

        if (patronLeerPantalla.containsMatchIn(normalizar(comando))) {
            return leerPantalla().also { registrarSiRespuestaUtil(it) }
        }

        if (patronCalendario.containsMatchIn(normalizar(comando))) {
            return abrirCalendario()
        }

        return try {
            ComandosMemoria.interpretar(comando)?.let { accion ->
                responderMemoria(accion)?.let { return it }
            }

            ExtractorNota.analizar(comando)?.let { peticion ->
                return notas.guardar(peticion)
            }

            // Nivel 0: comandos deterministas instantáneos (0 ms, 0 tokens)
            when (val intencion = parser.parsear(comando)) {
                is Intencion.CrearNota -> notas.guardar(
                    ExtractorNota.analizar(comando)
                        ?: PeticionGuardar(ExtractorNota.limpiar(intencion.contenido), DestinoNota.SinEspecificar)
                )
                is Intencion.AbrirAplicacion -> local.ejecutar(intencion).also {
                    if (it.exito) memoria.registrarHabito(CONTEXTO_APPS, normalizar(intencion.nombreApp))
                }
                is Intencion.ConsultaIA -> {
                    habilidades.responder(intencion.pregunta)?.let {
                        return ResultadoComando(true, it)
                    }
                    habilidades.peticionClima(intencion.pregunta)?.let {
                        return consultarClima(it)
                    }

                    // Nivel 1: enrutador de utilidades local (Tool-Calling con Gemma 3)
                    if (modeloLocal.disponible() && inteligencia.modoEjecucionLocal != ModoEjecucionLocal.SOLO_NUBE) {
                        val contexto = memoria.construirContextoUsuario(comando, perfil.nombre)
                        val promptTools = RegistroUtilidades.generarPromptCompactoLocal(contexto)
                        val salidaJson = modeloLocal.generar(promptTools, comando).getOrDefault("")
                        val llamadas = RegistroUtilidades.parsearLlamadasLocales(salidaJson)
                        val despacho = despachador.despachar(comando, llamadas)

                        if (despacho.manejadoLocalmente && despacho.resultado != null) {
                            return despacho.resultado.also { registrarSiRespuestaUtil(it) }
                        }
                        val consultaFinal = despacho.escalarANubePrompt ?: intencion.pregunta
                        return responderConsulta(consultaFinal).also { registrarSiRespuestaUtil(it) }
                    }

                    responderConsulta(intencion.pregunta).also {
                        registrarSiRespuestaUtil(it)
                    }
                }
                else -> local.ejecutar(intencion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoComando(false, "No pude completar esa acción.")
        }
    }

    suspend fun guardarAdjuntos(texto: String, adjuntos: List<AdjuntoNota>): ResultadoComando? {
        if (adjuntos.isEmpty()) return null
        val comando = quitarPalabraClave(texto, ajustes.palabraClave)
        val peticion = ExtractorNota.analizar(comando) ?: return null
        return notas.guardar(peticion, adjuntos)
    }

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
                    if (accion.tema == null) "Todavía no me has pedido que recuerde nada. Dime «recuerda que…» o importa un archivo de memoria."
                    else "No tengo nada guardado sobre «${accion.tema}»."
                )
            } else {
                ResultadoComando(true, "Esto es lo que recuerdo:\n" + encontrados.joinToString("\n") { "• $it" })
            }
        }
    }

    private fun registrarSiRespuestaUtil(resultado: ResultadoComando) {
        if (resultado.exito && resultado.mensaje.isNotBlank()) {
            val limpio = resultado.mensaje.removePrefix(AVISO_SIN_NUBE).trim()
            GestorNotas.registrarContenidoGenerado(limpio)
        }
    }

    private suspend fun systemPromptLocal(consulta: String): String {
        val nombreUsuario = perfil.nombre.ifBlank {
            appContext.getSharedPreferences("iris_prefs", Context.MODE_PRIVATE)
                .getString("user_name", null)
        }
        val contexto = memoria.construirContextoUsuario(consulta, nombreUsuario)
        val instrucciones = inteligencia.construirBloqueInstrucciones()
        return construirInstruccionLocal(contexto, instrucciones)
    }

    private val patronLeerPantalla =
        Regex("""\b(?:lee|leer|leeme|lea)\b.*\b(?:pantalla|pagina|web|sitio|contenido)\b""")

    private val patronCalendario =
        Regex("""^(?:abre|abrir|muestra|ver|consulta|revisa)\s+(?:el\s+|mi\s+)?(?:calendario|agenda)$""")

    private fun abrirCalendario(): ResultadoComando {
        if (!inteligencia.conectorCalendario) {
            return ResultadoComando(
                false,
                "El conector de Calendario está desactivado en Configuración → Inteligencia personalizada."
            )
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ResultadoComando(true, "Abriendo tu calendario.")
        } catch (_: Exception) {
            ResultadoComando(false, "No encontré una aplicación de calendario disponible.")
        }
    }

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

    private suspend fun responderConsulta(pregunta: String): ResultadoComando {
        habilidades.responder(pregunta)?.let { return ResultadoComando(true, it) }
        habilidades.peticionClima(pregunta)?.let { return consultarClima(it) }

        val seleccion = IrisModelSelector.seleccionar(pregunta)
        val tiempoReal = IrisModelSelector.requiereTiempoReal(pregunta)

        if (tiempoReal && !inteligencia.conectorBusquedaWeb) {
            return ResultadoComando(
                false,
                "El conector de Búsqueda Web en tiempo real está desactivado en Configuración → Inteligencia personalizada."
            )
        }

        val proveedor = inteligencia.proveedorActivo
        if (proveedor == ProveedorIA.SOLO_LOCAL) {
            if (!modeloLocal.disponible()) {
                return ResultadoComando(
                    false,
                    "Tienes activo el modo «Solo Modelo Local», pero aún no has importado un archivo .litertlm."
                )
            }
            return modeloLocal.generar(systemPromptLocal(pregunta), pregunta).fold(
                onSuccess = { ResultadoComando(true, it) },
                onFailure = { ResultadoComando(false, "El modelo local no pudo generar una respuesta.") }
            )
        }

        if (tiempoReal && !Conectividad.hayInternet(appContext)) {
            return ResultadoComando(
                false,
                "Para darte datos actuales (noticias, resultados, precios…) necesito conexión a internet."
            )
        }

        val recuerdosRelevantes = if (!tiempoReal) memoria.buscar(pregunta, limite = 3) else emptyList()
        val modoLocal = inteligencia.modoEjecucionLocal
        val usarLocalPrimero = modeloLocal.disponible() && !tiempoReal && when (modoLocal) {
            ModoEjecucionLocal.PRIORIZAR_LOCAL -> true
            ModoEjecucionLocal.SOLO_NUBE -> false
            ModoEjecucionLocal.HIBRIDO -> seleccion.categoria == CategoriaTarea.LIGERA ||
                (!Conectividad.hayInternet(appContext) && recuerdosRelevantes.isNotEmpty())
        }

        if (usarLocalPrimero) {
            modeloLocal.generar(systemPromptLocal(pregunta), pregunta)
                .onSuccess { return ResultadoComando(true, it) }
        }
        return consultarIris(
            pregunta,
            seleccion.modelo,
            intentarLocalSiFalla = !usarLocalPrimero && !tiempoReal
        )
    }

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
        if (cabeEnLocal && inteligencia.modoEjecucionLocal != ModoEjecucionLocal.SOLO_NUBE) {
            modeloLocal.generar(systemPromptLocal(indicacion), peticion)
                .onSuccess {
                    val res = ResultadoComando(true, it)
                    registrarSiRespuestaUtil(res)
                    return res
                }
        }
        return consultarIris(peticion, IrisModelSelector.MODELO_LIGERO, intentarLocalSiFalla = !cabeEnLocal)
            .also { registrarSiRespuestaUtil(it) }
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
        val proveedor = inteligencia.proveedorActivo
        val instruccionesExtra = inteligencia.construirBloqueInstrucciones()
        val permitirWeb = inteligencia.conectorBusquedaWeb

        // Si el usuario eligió un servidor local como Ollama, puede funcionar sin salida WAN
        if (proveedor == ProveedorIA.OLLAMA_LOCAL) {
            val resOllama = clienteProveedores.consultar(
                proveedor = proveedor,
                endpoint = inteligencia.endpointProveedor(proveedor),
                modelo = inteligencia.modeloProveedor(proveedor),
                apiKey = inteligencia.obtenerApiKey(proveedor),
                pregunta = pregunta,
                instruccionesExtra = instruccionesExtra,
                permitirBusquedaWeb = false
            )
            resOllama.onSuccess { return ResultadoComando(true, it) }
            if (intentarLocalSiFalla && modeloLocal.disponible()) {
                modeloLocal.generar(systemPromptLocal(pregunta), pregunta)
                    .onSuccess { return ResultadoComando(true, AVISO_SIN_NUBE + it) }
            }
            return ResultadoComando(
                false,
                resOllama.exceptionOrNull()?.message ?: "No pude conectar con tu servidor Ollama local."
            )
        }

        if (!Conectividad.hayInternet(appContext)) {
            if (intentarLocalSiFalla && modeloLocal.disponible()) {
                modeloLocal.generar(systemPromptLocal(pregunta), pregunta)
                    .onSuccess { return ResultadoComando(true, AVISO_SIN_NUBE + it) }
            }
            return ResultadoComando(false, "Ahora mismo no tengo conexión a internet.")
        }

        if (proveedor == ProveedorIA.GEMINI_DIRECTO || proveedor == ProveedorIA.OPENAI_COMPATIBLE) {
            val resExterno = clienteProveedores.consultar(
                proveedor = proveedor,
                endpoint = inteligencia.endpointProveedor(proveedor),
                modelo = inteligencia.modeloProveedor(proveedor),
                apiKey = inteligencia.obtenerApiKey(proveedor),
                pregunta = pregunta,
                instruccionesExtra = instruccionesExtra,
                permitirBusquedaWeb = permitirWeb
            )
            resExterno.onSuccess { return ResultadoComando(true, it) }
            if (intentarLocalSiFalla && modeloLocal.disponible()) {
                modeloLocal.generar(systemPromptLocal(pregunta), pregunta)
                    .onSuccess { return ResultadoComando(true, AVISO_SIN_NUBE + it) }
            }
            return ResultadoComando(
                false,
                mensajeAmigable(resExterno.exceptionOrNull() ?: IllegalStateException("Error en proveedor externo"))
            )
        }

        val elegido = modelo ?: IrisModelSelector.seleccionar(pregunta).modelo

        return gemini.preguntar(
            pregunta = pregunta,
            modeloSolicitado = elegido,
            instruccionesExtra = instruccionesExtra,
            permitirBusquedaWeb = permitirWeb
        ).fold(
            onSuccess = { ResultadoComando(true, it) },
            onFailure = { errorNube ->
                if (intentarLocalSiFalla && modeloLocal.disponible()) {
                    modeloLocal.generar(systemPromptLocal(pregunta), pregunta)
                        .onSuccess { return ResultadoComando(true, AVISO_SIN_NUBE + it) }
                }
                ResultadoComando(false, mensajeAmigable(errorNube))
            }
        )
    }

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
            "No tengo autorización para completar la consulta. Revisa tu API Key en Inteligencia personalizada."
        error is HttpException && error.codigo == 404 ->
            "El servicio de inteligencia no encontró el modelo o ruta configurada."
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
            "No pude consultar a la nube; esta respuesta viene de mi modelo local y puede tener errores.\n\n"
    }
}
