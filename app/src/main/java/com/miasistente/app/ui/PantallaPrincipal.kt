package com.miasistente.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miasistente.app.comandos.Conectividad
import com.miasistente.app.comandos.GeminiClient
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.AvatarRepositorio
import com.miasistente.app.data.Permisos
import com.miasistente.app.data.PerfilUsuario
import com.miasistente.app.data.SecureStorage
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Permisos que hacen falta para arrancar el servicio de voz. */
private fun permisosParaServicio(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    // Android 13+ exige permiso explícito para mostrar notificaciones
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private val IDIOMA_ES: Locale = Locale.forLanguageTag("es")

/** Hora actual, actualizada justo al cambiar cada minuto. */
@Composable
private fun rememberAhora(): State<LocalDateTime> =
    produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }

private fun String.capitalizar(): String =
    replaceFirstChar { it.titlecase(IDIOMA_ES) }

private fun saludoSegunHora(hora: Int): String = when (hora) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

/**
 * Pantalla principal "Iris":
 *   barra superior · fecha/hora/clima · saludo · barra de entrada flotante con micrófono.
 * La configuración (nombre, permisos, palabra clave, avatar) vive en el menú lateral.
 */
@Composable
fun PantallaPrincipal() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ajustes = remember { Ajustes(context) }
    val perfil = remember { PerfilUsuario(context) }
    val procesador = remember { ProcesadorComandos(context) }
    val almacen = remember { SecureStorage(context) }

    // --- Estado del servicio de voz (viene de AsistenteService) -----------------------------
    val activo by AsistenteService.isRunning.collectAsStateWithLifecycle()
    val estado by AsistenteService.estadoAudio.collectAsStateWithLifecycle()
    val estadoAvatar by AsistenteService.assistantState.collectAsStateWithLifecycle()

    // --- Permisos ----------------------------------------------------------------------------
    var microfonoOk by remember { mutableStateOf(Permisos.microfono(context)) }
    var superposicionOk by remember { mutableStateOf(Permisos.superposicion(context)) }
    var microfonoDenegado by remember { mutableStateOf(false) }
    var iniciarTrasPermisos by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Al volver de Ajustes (superposición) hay que volver a comprobar
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        microfonoOk = Permisos.microfono(context)
        superposicionOk = Permisos.superposicion(context)
    }

    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        microfonoOk = Permisos.microfono(context)
        if (!microfonoOk) microfonoDenegado = true
        if (iniciarTrasPermisos) {
            iniciarTrasPermisos = false
            if (microfonoOk) {
                mensaje = null
                AsistenteService.start(context)
            } else {
                mensaje = "Sin permiso de micrófono no puedo escuchar."
            }
        }
    }

    // --- Palabra clave y perfil ---------------------------------------------------------------
    var palabraEscrita by rememberSaveable { mutableStateOf(ajustes.palabraClave) }
    var nombre by rememberSaveable { mutableStateOf(perfil.nombre) }

    fun activarServicio() {
        val normalizada = Ajustes.normalizar(palabraEscrita)
        if (normalizada.isEmpty()) {
            mensaje = "Escribe una palabra clave en Ajustes (solo letras)."
            return
        }
        ajustes.palabraClave = normalizada
        palabraEscrita = normalizada

        val faltantes = permisosParaServicio().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isEmpty()) {
            mensaje = null
            AsistenteService.start(context)
        } else {
            iniciarTrasPermisos = true
            lanzadorPermisos.launch(faltantes.toTypedArray())
        }
    }

    // --- Avatar --------------------------------------------------------------------------------
    var avatar by remember { mutableStateOf(ajustes.avatar) }
    var imagenPersonalizada by remember { mutableStateOf<ImageBitmap?>(null) }
    var mensajeAvatar by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { imagenPersonalizada = AvatarRepositorio.cargar(context) }

    val lanzadorGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (AvatarRepositorio.guardarDesdeUri(context, uri)) {
                    imagenPersonalizada = AvatarRepositorio.cargar(context)
                    avatar = Ajustes.AVATAR_PERSONALIZADO
                    ajustes.avatar = avatar
                    mensajeAvatar = null
                } else {
                    mensajeAvatar = "No pude leer esa imagen. Prueba con otra."
                }
            }
        }
    }

    // --- Clave de Gemini (se ve y se cambia desde el menú lateral) ------------------------------
    var tieneClave by remember { mutableStateOf(false) }
    var verificandoClave by remember { mutableStateOf(false) }
    var resultadoClave by remember { mutableStateOf<ResultadoComando?>(null) }

    // Leer el almacén cifrado puede tardar un poco: fuera del hilo principal
    LaunchedEffect(Unit) { tieneClave = withContext(Dispatchers.IO) { almacen.tieneApiKey() } }

    fun guardarClave(clave: String) {
        scope.launch {
            withContext(Dispatchers.IO) { almacen.guardarApiKey(clave) }
            tieneClave = true
            resultadoClave = ResultadoComando(true, "Clave guardada.")
        }
    }

    fun borrarClave() {
        scope.launch {
            withContext(Dispatchers.IO) { almacen.borrarApiKey() }
            tieneClave = false
            resultadoClave = null
        }
    }

    /** Hace una consulta mínima a Gemini para comprobar que la clave funciona. */
    fun verificarClave() {
        if (verificandoClave) return
        scope.launch {
            verificandoClave = true
            resultadoClave = when {
                !tieneClave -> ResultadoComando(false, "Aún no hay clave guardada.")
                !Conectividad.hayInternet(context) ->
                    ResultadoComando(false, "Sin internet: no pude comprobar la clave.")
                else -> GeminiClient(almacen).preguntar("Responde solo con la palabra: ok").fold(
                    onSuccess = { ResultadoComando(true, "La clave funciona.") },
                    onFailure = { ResultadoComando(false, "Gemini: ${it.message ?: "error desconocido"}") }
                )
            }
            verificandoClave = false
        }
    }

    // --- Comandos escritos ---------------------------------------------------------------------
    var entrada by rememberSaveable { mutableStateOf("") }
    var comandoLocal by remember { mutableStateOf("") }
    var resultadoLocal by remember { mutableStateOf<ResultadoComando?>(null) }
    var procesando by remember { mutableStateOf(false) }

    fun enviarTexto() {
        val texto = entrada.trim()
        if (texto.isEmpty() || procesando) return
        entrada = ""
        comandoLocal = texto
        resultadoLocal = null
        scope.launch {
            procesando = true
            resultadoLocal = try {
                procesador.procesar(texto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResultadoComando(false, "No pude ejecutar ese comando.")
            }
            procesando = false
        }
    }

    // --- Aviso de "palabra clave detectada" (3 segundos) --------------------------------------
    var mostrarActivacion by remember { mutableStateOf(false) }
    LaunchedEffect(estado.activaciones) {
        if (estado.activaciones > 0) {
            mostrarActivacion = true
            delay(3_000)
            mostrarActivacion = false
        } else {
            mostrarActivacion = false
        }
    }

    // --- Fecha, hora y saludo -------------------------------------------------------------------
    val ahora by rememberAhora()
    val fecha = remember(ahora.toLocalDate()) {
        val dia = ahora.dayOfWeek.getDisplayName(TextStyle.FULL, IDIOMA_ES).capitalizar()
        val mes = ahora.month.getDisplayName(TextStyle.FULL, IDIOMA_ES).capitalizar()
        "$dia, ${ahora.dayOfMonth} de $mes"
    }
    val formato24h = DateFormat.is24HourFormat(context)
    val hora = remember(ahora.hour, ahora.minute, formato24h) {
        ahora.format(DateTimeFormatter.ofPattern(if (formato24h) "HH:mm" else "h:mm a", IDIOMA_ES))
    }
    val saludo = remember(ahora.hour, nombre) {
        val base = saludoSegunHora(ahora.hour)
        if (nombre.isBlank()) base else "$base, ${nombre.trim()}"
    }

    // --- Textos y colores de estado -------------------------------------------------------------
    val preparando = activo && estado.preparandoModelo
    val estadoVisual = if (procesando) AssistantState.THINKING else estadoAvatar
    val palabra = estado.palabraClave.ifEmpty { ajustes.palabraClave }
    val textoError = mensaje ?: estado.error

    val chip: Pair<String, Color>? = when {
        preparando && estado.progresoModelo >= 0 ->
            "Descargando modelo… ${estado.progresoModelo} %" to Paleta.Ambar
        preparando -> "Cargando modelo de voz…" to Paleta.Ambar
        !activo -> null
        mostrarActivacion -> "¡Te escuché!" to Paleta.Morado
        estadoVisual == AssistantState.LISTENING -> "Escuchando…" to Paleta.Azul
        estadoVisual == AssistantState.THINKING -> "Pensando…" to Paleta.Ambar
        estadoVisual == AssistantState.SPEAKING -> "Respondiendo…" to Paleta.Verde
        estado.capturando -> "Esperando «$palabra»" to Paleta.Verde
        else -> "Abriendo micrófono…" to Paleta.Ambar
    }

    // ==========================================================================================
    // Interfaz
    // ==========================================================================================
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PaletaIris.PanelLateral,
                drawerContentColor = Color.White
            ) {
                PanelAjustes(
                    nombre = nombre,
                    onNombre = {
                        nombre = it
                        perfil.nombre = it
                    },
                    microfonoOk = microfonoOk,
                    superposicionOk = superposicionOk,
                    onPedirMicrofono = {
                        if (microfonoDenegado) {
                            context.startActivity(Permisos.intentAjustesApp(context))
                        } else {
                            lanzadorPermisos.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    },
                    onPedirSuperposicion = {
                        try {
                            context.startActivity(Permisos.intentSuperposicion(context))
                        } catch (e: ActivityNotFoundException) {
                            mensaje = "Tu teléfono no permite abrir esa pantalla de Ajustes."
                        }
                    },
                    palabra = palabraEscrita,
                    onPalabra = { palabraEscrita = it },
                    palabraEditable = !activo,
                    avatarSeleccionado = avatar,
                    imagenPersonalizada = imagenPersonalizada,
                    mensajeAvatar = mensajeAvatar,
                    onSeleccionarAvatar = {
                        avatar = it
                        ajustes.avatar = it
                    },
                    onElegirGaleria = { lanzadorGaleria.launch("image/*") },
                    tieneClave = tieneClave,
                    verificandoClave = verificandoClave,
                    resultadoClave = resultadoClave,
                    onGuardarClave = { guardarClave(it) },
                    onBorrarClave = { borrarClave() },
                    onVerificarClave = { verificarClave() },
                    onCerrar = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        FondoNoche(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // 1) Barra superior
                BarraSuperiorIris(
                    activo = activo,
                    avatarId = avatar,
                    imagenPersonalizada = imagenPersonalizada,
                    hayAviso = !microfonoOk || !superposicionOk,
                    onAbrirAjustes = { scope.launch { drawerState.open() } }
                )

                // 2) Fecha, hora y clima
                CabeceraFechaClima(fecha = fecha, hora = hora)

                // 3) Zona central: saludo, estado y última conversación
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .align(BiasAlignment(0f, 0.2f))
                            .widthIn(max = 640.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SaludoPrincipal(saludo)

                        chip?.let { (texto, color) -> ChipEstado(texto, color) }

                        if (preparando && estado.progresoModelo >= 0) {
                            LinearProgressIndicator(
                                progress = { estado.progresoModelo / 100f },
                                color = PaletaIris.Acento,
                                trackColor = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Solo la primera vez (≈ 39 MB). Mantén la app abierta.",
                                color = PaletaIris.TextoSuave,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        textoError?.let {
                            Text(it, color = Paleta.Rojo, style = MaterialTheme.typography.bodyMedium)
                        }

                        AnimatedVisibility(visible = comandoLocal.isNotEmpty()) {
                            TarjetaConversacion(
                                comando = comandoLocal,
                                resultado = resultadoLocal,
                                procesando = procesando,
                                onCerrar = {
                                    comandoLocal = ""
                                    resultadoLocal = null
                                }
                            )
                        }
                    }
                }

                // 4) Barra de entrada flotante
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    BarraEntradaIris(
                        texto = entrada,
                        onTexto = { entrada = it },
                        onEnviar = { enviarTexto() },
                        estado = estadoVisual,
                        activo = activo,
                        onMicrofono = {
                            if (activo) AsistenteService.stop(context) else activarServicio()
                        },
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }

            }
        }
    }
}
