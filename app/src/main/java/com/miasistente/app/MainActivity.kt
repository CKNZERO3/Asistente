package com.miasistente.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.miasistente.app.auth.GoogleSignInManager
import com.miasistente.app.auth.GoogleSignInResult
import com.miasistente.app.comandos.AdjuntoNota
import com.miasistente.app.comandos.EjecutorLocal
import com.miasistente.app.comandos.LecturaArchivo
import com.miasistente.app.comandos.ModeloLocal
import com.miasistente.app.comandos.PoliticaArchivos
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.service.VozAsistente
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.ChatAttachment
import com.miasistente.app.ui.ChatViewModel
import com.miasistente.app.ui.IrisScreen
import com.miasistente.app.ui.PermissionKind
import com.miasistente.app.ui.PermissionPrompt
import com.miasistente.app.ui.SavedChatAttachment
import com.miasistente.app.ui.SavedChatMessage
import com.miasistente.app.ui.SavedConversation
import com.miasistente.app.ui.loadSavedConversations
import com.miasistente.app.ui.persistSavedConversations
import com.miasistente.app.ui.saveUriToIrisMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.miasistente.app.memoria.ImportadorMemoria


class MainActivity : ComponentActivity() {

    private lateinit var procesador: ProcesadorComandos
    private lateinit var vozAsistente: VozAsistente
    private val chat: ChatViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences("iris_prefs", MODE_PRIVATE) }
    private val googleSignIn by lazy { GoogleSignInManager(this) }

    private var userName by mutableStateOf<String?>(null)
    private var userEmail by mutableStateOf<String?>(null)
    private var userPhoto by mutableStateOf<String?>(null)
    private var avatarEnabled by mutableStateOf(false)
    private var darkTheme by mutableStateOf(true)
    private var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)
    private val pendingAttachments = mutableStateListOf<ChatAttachment>()
    private var speakingMessageText by mutableStateOf<String?>(null)
    private var isAppInForeground by mutableStateOf(false)

    private var conversations by mutableStateOf<List<SavedConversation>>(emptyList())
    private var activeConversationId by mutableStateOf("conv_${System.currentTimeMillis()}")
    private val thinkingConversations = mutableStateMapOf<String, Boolean>()

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) prepararAdjuntos(uris, esFoto = false)
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) prepararAdjuntos(uris, esFoto = true)
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            if (pendingAttachments.size >= MAX_ADJUNTOS) {
                aviso("Máximo $MAX_ADJUNTOS archivos adjuntos por mensaje")
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val adjunto = withContext(Dispatchers.IO) { guardarCapturaCamara(bitmap) }
                if (adjunto != null) {
                    if (pendingAttachments.size < MAX_ADJUNTOS) {
                        pendingAttachments.add(adjunto)
                    }
                } else {
                    aviso("No pude procesar la foto de la cámara")
                }
            }
        }
    }

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) importarModeloLocal(uri)
    }

    private val pickMemoryFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) importarArchivoMemoria(uri)
    }

    private val pickMemoryFile = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
    ) { uri ->
    if (uri != null) {
        lifecycleScope.launch {
            val res = ImportadorMemoria.importarDesdeUri(applicationContext, uri)
            aviso(res.mensaje)
            }
        }
    }

    private val pedirPermisosVoz = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (tienePermisoMicrofono()) {
            activarMicrofono()
        } else {
            aviso("Necesito el permiso del micrófono para escucharte")
        }
    }

    private val pedirPermisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val concedido = resultados.values.any { it }
        if (concedido) {
            compartirUbicacionGps()
        } else {
            aviso("Concede el permiso de ubicación para usar el GPS")
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onStop() {
        isAppInForeground = false
        detenerVozSilenciosamente()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        procesador = ProcesadorComandos(this)
        vozAsistente = VozAsistente(this)

        darkTheme = prefs.getBoolean("dark_theme", true)
        userName = prefs.getString("user_name", null)
        userEmail = prefs.getString("user_email", null)
        userPhoto = prefs.getString("user_photo", null)
        conversations = loadSavedConversations(this)

        if (prefs.getBoolean("mic_enabled", false) && tienePermisoMicrofono()) {
            AsistenteService.start(this)
        }

        lifecycleScope.launch(Dispatchers.Default) {
            EjecutorLocal(applicationContext).precalentar()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AsistenteService.eventosComando.collect { evento ->
                    val targetConvId = activeConversationId
                    chat.addUser(evento.comandoUsuario)
                    chat.addAssistant(evento.respuesta)
                    guardarMensajeEnHistorial(
                        convId = targetConvId,
                        nuevoMensaje = SavedChatMessage(text = evento.comandoUsuario, fromUser = true)
                    )
                    guardarMensajeEnHistorial(
                        convId = targetConvId,
                        nuevoMensaje = SavedChatMessage(text = evento.respuesta, fromUser = false)
                    )
                    if (targetConvId == activeConversationId && isAppInForeground) {
                        reproducirVoz(evento.respuesta)
                    }
                }
            }
        }

        setContent {
            AsistenteTema {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val isServiceRunning by AsistenteService.isRunning.collectAsState()
                    val audio by AsistenteService.estadoAudio.collectAsState()
                    val isSpeaking by vozAsistente.isSpeaking.collectAsState()
                    val activeIsThinking = thinkingConversations[activeConversationId] == true || chat.isThinking

                    val view = LocalView.current
                    SideEffect {
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }

                    LaunchedEffect(audio.error) {
                        audio.error?.let { aviso(it) }
                    }

                    val voiceStatus: String? = when {
                        audio.preparandoModelo ->
                            if (audio.progresoModelo >= 0) "Descargando modelo de voz… ${audio.progresoModelo}%"
                            else "Preparando voz…"
                        isServiceRunning && audio.capturando ->
                            if (audio.hayVoz || audio.palabraClave.isBlank()) "Escuchando…"
                            else "Di «${audio.palabraClave}» y tu comando"
                        isServiceRunning -> "Activando micrófono…"
                        else -> null
                    }

                    IrisScreen(
                        userName = userName,
                        userEmail = userEmail,
                        profilePhoto = userPhoto,
                        avatarEnabled = avatarEnabled,
                        darkTheme = darkTheme,
                        onDarkThemeChange = { cambiarTema(it) },
                        messages = chat.messages,
                        conversations = conversations,
                        activeConversationId = activeConversationId,
                        onSelectConversation = { seleccionarConversacion(it) },
                        onDeleteConversation = { eliminarConversacion(it) },
                        isThinking = activeIsThinking,
                        isSpeaking = isSpeaking,
                        speakingMessageText = speakingMessageText,
                        micActive = isServiceRunning,
                        micPulse = isServiceRunning && audio.hayVoz,
                        voiceStatus = voiceStatus,
                        permissionPrompt = permissionPrompt,
                        pendingAttachments = pendingAttachments,
                        onRemoveAttachment = { index ->
                            if (index in pendingAttachments.indices) {
                                pendingAttachments.removeAt(index)
                            }
                        },
                        onPermissionConfirm = { confirmarPermiso() },
                        onPermissionDismiss = { permissionPrompt = null },
                        onAvatarChanged = { avatarEnabled = it },
                        onSend = { enviar(it) },
                        onVoice = { alternarMicrofono(isServiceRunning) },
                        onSpeakMessage = { alternarLecturaMensaje(it) },
                        onStopSpeaking = { detenerVozSilenciosamente() },
                        onAttach = { pickFile.launch("*/*") },
                        onPhotoSelected = { uri -> prepararAdjuntos(listOf(uri), esFoto = true) },
                        onAction = { destination -> manejarAccion(destination) }
                    )
                }
            }
        }
    }

    private fun detenerVozSilenciosamente() {
        speakingMessageText = null
        vozAsistente.detener()
    }

    private suspend fun reproducirVoz(texto: String) {
        speakingMessageText = texto
        try {
            vozAsistente.hablar(texto)
        } finally {
            if (speakingMessageText == texto) {
                speakingMessageText = null
            }
        }
    }

    private fun alternarLecturaMensaje(texto: String) {
        if (texto.isBlank()) return
        if (vozAsistente.isSpeaking.value && speakingMessageText == texto) {
            detenerVozSilenciosamente()
            return
        }
        detenerVozSilenciosamente()
        lifecycleScope.launch {
            reproducirVoz(texto)
        }
    }

    private fun iniciarNuevaConversacion() {
        detenerVozSilenciosamente()
        pendingAttachments.clear()
        chat.clear()
        chat.updateThinking(false)
        activeConversationId = "conv_${System.currentTimeMillis()}"
    }

    private fun seleccionarConversacion(conv: SavedConversation) {
        detenerVozSilenciosamente()
        pendingAttachments.clear()
        activeConversationId = conv.id
        chat.clear()
        conv.messages.forEach { saved ->
            val adjuntos = saved.attachments.map {
                ChatAttachment(
                    uri = it.uri,
                    name = it.name,
                    mime = it.mime,
                    sizeBytes = it.sizeBytes
                )
            }
            if (saved.fromUser) {
                chat.addUser(saved.text, adjuntos)
            } else if (saved.text.isNotBlank()) {
                chat.addAssistant(saved.text)
            }
        }
        chat.updateThinking(thinkingConversations[conv.id] == true)
    }

    private fun eliminarConversacion(convId: String) {
        val filtradas = conversations.filterNot { it.id == convId }
        conversations = filtradas
        persistSavedConversations(this, filtradas)
        thinkingConversations.remove(convId)
        if (activeConversationId == convId) {
            iniciarNuevaConversacion()
        }
    }

    private fun guardarMensajeEnHistorial(convId: String, nuevoMensaje: SavedChatMessage) {
        val ahora = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date())
        val lista = conversations.toMutableList()
        val idx = lista.indexOfFirst { it.id == convId }

        if (idx >= 0) {
            val existente = lista[idx]
            val mensajesActualizados = existente.messages + nuevoMensaje
            val tituloActual = existente.title.ifBlank {
                derivarTituloConversacion(mensajesActualizados)
            }
            lista[idx] = existente.copy(
                title = tituloActual,
                updatedAt = ahora,
                messages = mensajesActualizados
            )
        } else {
            val mensajesNuevos = listOf(nuevoMensaje)
            lista.add(
                0,
                SavedConversation(
                    id = convId,
                    title = derivarTituloConversacion(mensajesNuevos),
                    updatedAt = ahora,
                    messages = mensajesNuevos
                )
            )
        }

        conversations = lista
        persistSavedConversations(this, lista)
    }

    private fun derivarTituloConversacion(mensajes: List<SavedChatMessage>): String {
        val base = mensajes.firstOrNull { it.fromUser && it.text.isNotBlank() }?.text
            ?: mensajes.firstOrNull { it.attachments.isNotEmpty() }?.attachments?.firstOrNull()?.name
            ?: "Nueva conversación"
        return if (base.length > 34) base.take(34) + "…" else base
    }

    private fun enviar(texto: String) {
        val adjuntosPendientes = pendingAttachments.toList()
        if (texto.isBlank() && adjuntosPendientes.isEmpty()) return

        val targetConvId = activeConversationId
        pendingAttachments.clear()

        chat.addUser(texto, adjuntosPendientes)
        guardarMensajeEnHistorial(
            convId = targetConvId,
            nuevoMensaje = SavedChatMessage(
                text = texto,
                fromUser = true,
                attachments = adjuntosPendientes.map {
                    SavedChatAttachment(
                        uri = it.uri,
                        name = it.name,
                        mime = it.mime,
                        sizeBytes = it.sizeBytes
                    )
                }
            )
        )

        val ultimaRespuestaAsistente = chat.messages
        .lastOrNull { !it.fromUser && it.text.isNotBlank() }
        ?.text

        thinkingConversations[targetConvId] = true
        if (targetConvId == activeConversationId) {
            chat.updateThinking(true)
        }

        val adjuntosObjetivo = adjuntosPendientes.ifEmpty {
            if (PoliticaArchivos.pideResumirArchivo(texto)) {
                chat.messages.lastOrNull { msg ->
                    msg.attachments.any { !it.isImage }
                }?.attachments.orEmpty()
            } else {
                emptyList()
            }
        }

        lifecycleScope.launch {
            AsistenteService.updateAssistantState(AssistantState.THINKING)
            try {
                val resultado = withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) {
                        // "Guarda esta imagen en el cuaderno Recetas": va a Iris Notebook o a la app pedida.
                        procesador.guardarAdjuntos(
                            texto,
                            adjuntosObjetivo.map { AdjuntoNota(it.uri, it.name, it.mime) }
                        )?.let { return@withContext it }

                        val archivosTexto = adjuntosObjetivo.filter { !it.isImage }
                        val imagenes = adjuntosObjetivo.filter { it.isImage }

                        when {
                            archivosTexto.size == 1 && imagenes.isEmpty() -> {
                                val archivo = archivosTexto.first()
                                val lectura = PoliticaArchivos.leer(
                                    contentResolver,
                                    Uri.parse(archivo.uri),
                                    archivo.name,
                                    archivo.mime,
                                    archivo.sizeBytes
                                )
                                procesador.resumirArchivo(
                                    nombre = archivo.name,
                                    lectura = lectura,
                                    instruccionUsuario = texto.takeIf { it.isNotBlank() }
                                )
                            }
                            archivosTexto.isNotEmpty() -> {
                                val bloques = mutableListOf<String>()
                                for (archivo in archivosTexto) {
                                    when (
                                        val lectura = PoliticaArchivos.leer(
                                            contentResolver,
                                            Uri.parse(archivo.uri),
                                            archivo.name,
                                            archivo.mime,
                                            archivo.sizeBytes
                                        )
                                    ) {
                                        is LecturaArchivo.Texto ->
                                            bloques.add("--- ${archivo.name} ---\n${lectura.contenido.trim()}")
                                        is LecturaArchivo.Rechazado ->
                                            return@withContext ResultadoComando(false, lectura.motivo)
                                    }
                                }
                                val combinado = bloques.joinToString("\n\n")
                                val nombres = archivosTexto.joinToString(", ") { it.name }
                                procesador.resumirArchivo(
                                    nombre = nombres,
                                    lectura = LecturaArchivo.Texto(combinado),
                                    instruccionUsuario = texto.takeIf { it.isNotBlank() }
                                )
                            }
                            imagenes.isNotEmpty() -> {
                                val listaNombres = imagenes.joinToString(", ") { it.name }
                                val consultaImagen = if (texto.isBlank()) {
                                    "El usuario adjuntó ${imagenes.size} imagen(es) ($listaNombres). Indícale brevemente cómo puedes ayudarle con ellas."
                                } else {
                                    "$texto (imágenes adjuntas: $listaNombres)"
                                }
                                procesador.procesar(consultaImagen, ultimoMensajeAsistente = ultimaRespuestaAsistente)
                            }
                            else -> procesador.procesar(texto, ultimoMensajeAsistente = ultimaRespuestaAsistente)
                        }
                    }
                } ?: ResultadoComando(
                    exito = false,
                    mensaje = "La consulta tardó demasiado en responder. Inténtalo de nuevo."
                )

                val respuestaFinal = resultado.mensaje.ifBlank { "Listo." }

                guardarMensajeEnHistorial(
                    convId = targetConvId,
                    nuevoMensaje = SavedChatMessage(text = respuestaFinal, fromUser = false)
                )

                if (targetConvId == activeConversationId) {
                    chat.addAssistant(respuestaFinal)
                    if (resultado.exito && isAppInForeground) {
                        reproducirVoz(respuestaFinal)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = "No pude completar eso: ${e.message ?: "error desconocido"}"
                guardarMensajeEnHistorial(
                    convId = targetConvId,
                    nuevoMensaje = SavedChatMessage(text = errorMsg, fromUser = false)
                )
                if (targetConvId == activeConversationId) {
                    chat.addAssistant(errorMsg)
                }
            } finally {
                thinkingConversations.remove(targetConvId)
                if (targetConvId == activeConversationId) {
                    chat.updateThinking(false)
                }
                if (thinkingConversations.isEmpty()) {
                    AsistenteService.updateAssistantState(AssistantState.IDLE)
                }
            }
        }
    }

    private fun prepararAdjuntos(uris: List<Uri>, esFoto: Boolean) {
        val espacioDisponible = MAX_ADJUNTOS - pendingAttachments.size
        if (espacioDisponible <= 0) {
            aviso("Máximo $MAX_ADJUNTOS archivos adjuntos por mensaje")
            return
        }
        val seleccionados = uris.take(espacioDisponible)
        if (uris.size > espacioDisponible) {
            aviso("Solo se añadieron $espacioDisponible de ${uris.size} (máximo $MAX_ADJUNTOS)")
        }

        lifecycleScope.launch {
            val nuevos = withContext(Dispatchers.IO) {
                seleccionados.map { uri ->
                    val item = leerAdjunto(uri)
                    val tipo = if (esFoto || item.isImage) "photo" else "file"
                    val uriPersistente = copiarArchivoLocalmente(uri, item.name)
                    saveUriToIrisMedia(applicationContext, uri, tipo)
                    if (uriPersistente != null) item.copy(uri = uriPersistente) else item
                }
            }
            val cupoRestante = MAX_ADJUNTOS - pendingAttachments.size
            if (cupoRestante > 0) {
                pendingAttachments.addAll(nuevos.take(cupoRestante))
            }
        }
    }

    private fun copiarArchivoLocalmente(uri: Uri, nombreOriginal: String): String? {
        return try {
            val dir = File(filesDir, "iris_attachments").apply { mkdirs() }
            val limpio = nombreOriginal.replace(Regex("""[^\w.\-]"""), "_")
            val destino = File(dir, "${System.currentTimeMillis()}_$limpio")
            contentResolver.openInputStream(uri)?.use { input ->
                destino.outputStream().use { output -> input.copyTo(output) }
            }
            if (destino.exists() && destino.length() > 0L) {
                Uri.fromFile(destino).toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun guardarCapturaCamara(bitmap: Bitmap): ChatAttachment? {
        return try {
            val dir = File(filesDir, "iris_attachments").apply { mkdirs() }
            val nombre = "foto_${System.currentTimeMillis()}.png"
            val archivo = File(dir, nombre)
            FileOutputStream(archivo).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = Uri.fromFile(archivo)
            saveUriToIrisMedia(applicationContext, uri, "photo")
            ChatAttachment(uri.toString(), nombre, "image/png", archivo.length())
        } catch (_: Exception) {
            null
        }
    }

    private fun leerAdjunto(uri: Uri): ChatAttachment {
        val mime = contentResolver.getType(uri)
        var nombre = uri.lastPathSegment?.substringAfterLast('/') ?: "archivo"
        var tamano: Long? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val iNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (iNombre >= 0) cursor.getString(iNombre)?.let { nombre = it }
                    val iTamano = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (iTamano >= 0 && !cursor.isNull(iTamano)) tamano = cursor.getLong(iTamano)
                }
            }
        } catch (_: Exception) {}
        return ChatAttachment(uri.toString(), nombre, mime, tamano)
    }

    private fun importarModeloLocal(uri: Uri) {
        lifecycleScope.launch {
            val archivo = withContext(Dispatchers.IO) { leerAdjunto(uri) }
            if (!archivo.name.endsWith(".litertlm", ignoreCase = true)) {
                aviso("Elige un archivo de modelo con extensión .litertlm")
                return@launch
            }
            aviso("Importando el modelo… puede tardar un minuto")
            val resultado = ModeloLocal(applicationContext).importar(uri, archivo.sizeBytes)
            aviso(
                if (resultado.isSuccess) "IA local lista"
                else resultado.exceptionOrNull()?.message ?: "No pude importar el modelo"
            )
        }
    }
    private fun importarArchivoMemoria(uri: Uri) {
        lifecycleScope.launch {
            val resultado = ImportadorMemoria(applicationContext).importarDesdeUri(uri)
            aviso(resultado.mensaje)
        }
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    private fun compartirUbicacionGps() {
        if (!tienePermisoUbicacion()) {
            pedirPermisoUbicacion.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        lifecycleScope.launch {
            val textoUbicacion = withContext(Dispatchers.IO) {
                try {
                    val lm = getSystemService(LocationManager::class.java)
                    val proveedores = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                    )
                    val loc = proveedores.firstNotNullOfOrNull { proveedor ->
                        runCatching { lm?.getLastKnownLocation(proveedor) }.getOrNull()
                    }
                    if (loc == null) {
                        null
                    } else {
                        val ciudad = runCatching {
                            @Suppress("DEPRECATION")
                            val dirs = Geocoder(this@MainActivity, Locale.getDefault())
                                .getFromLocation(loc.latitude, loc.longitude, 1)
                            val d = dirs?.firstOrNull()
                            listOfNotNull(d?.locality ?: d?.subAdminArea, d?.countryName)
                                .joinToString(", ")
                                .takeIf { it.isNotBlank() }
                        }.getOrNull()

                        if (ciudad != null) {
                            "Mi ubicación actual: $ciudad (${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)})"
                        } else {
                            "Mi ubicación actual: ${String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)}"
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (textoUbicacion != null) {
                enviar(textoUbicacion)
            } else {
                aviso("Activa el GPS del teléfono e inténtalo de nuevo")
            }
        }
    }

    private fun tienePermisoMicrofono(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun alternarMicrofono(activo: Boolean) {
        if (activo) {
            prefs.edit().putBoolean("mic_enabled", false).apply()
            AsistenteService.stop(this)
            return
        }
        if (tienePermisoMicrofono()) {
            activarMicrofono()
            return
        }
        val bloqueado = prefs.getBoolean("mic_asked", false) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
        permissionPrompt = PermissionPrompt(PermissionKind.MICROPHONE, goToSettings = bloqueado)
    }

    private fun confirmarPermiso() {
        val prompt = permissionPrompt ?: return
        permissionPrompt = null
        if (prompt.goToSettings) {
            abrirAjustesDeLaApp()
            return
        }
        prefs.edit().putBoolean("mic_asked", true).apply()
        val permisos = arrayListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permisos.add("android.permission.POST_NOTIFICATIONS")
        pedirPermisosVoz.launch(permisos.toTypedArray())
    }

    private fun activarMicrofono() {
        prefs.edit().putBoolean("mic_enabled", true).apply()
        AsistenteService.start(this)
    }

    private fun cambiarTema(oscuro: Boolean) {
        darkTheme = oscuro
        prefs.edit().putBoolean("dark_theme", oscuro).apply()
    }

    private fun iniciarSesionGoogle() {
        lifecycleScope.launch {
            when (val resultado = googleSignIn.signIn(this@MainActivity)) {
                is GoogleSignInResult.Success -> {
                    completarInicioSesion(resultado.name, resultado.email, resultado.photoUrl)
                    aviso("Hola, ${resultado.name}")
                }
                GoogleSignInResult.Cancelled -> Unit
                is GoogleSignInResult.Error -> aviso(resultado.message)
            }
        }
    }

    private fun completarInicioSesion(nombre: String, email: String?, fotoUrl: String?) {
        userName = nombre
        userEmail = email
        userPhoto = fotoUrl
        prefs.edit()
            .putString("user_name", nombre)
            .putString("user_email", email)
            .putString("user_photo", fotoUrl)
            .apply()
    }

    private fun cerrarSesion() {
        userName = null
        userEmail = null
        userPhoto = null
        prefs.edit().remove("user_name").remove("user_email").remove("user_photo").apply()
        lifecycleScope.launch { googleSignIn.signOut() }
    }

        private fun manejarAccion(destination: String) {
        when (destination) {
            "new_chat", "clear_messages" -> iniciarNuevaConversacion()
            "permissions" -> abrirAjustesDeLaApp()
            "support" -> enviarCorreoSoporte()
            "sign_out" -> cerrarSesion()
            "sign_in" -> iniciarSesionGoogle()
            "import_model" -> pickModel.launch("*/*")
            "open_photos" -> pickPhoto.launch("image/*")
            "open_camera" -> takePicture.launch(null)
            "open_files", "open_drive" -> pickFile.launch("*/*")
            "use_gps_location" -> compartirUbicacionGps()
            "stop_speaking", "stop_external_agent_voice" -> detenerVozSilenciosamente()
            "search" -> aviso("Búsqueda próximamente")
            "gallery" -> aviso("Galería próximamente")
            "import" -> pickMemoryFile.launch(arrayOf("text/*", "application/json"))
            "import_model" -> pickModel.launch("*/*")
        }
    }
        

    private fun abrirAjustesDeLaApp() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            aviso("No se pudo abrir los ajustes")
        }
    }

    private fun enviarCorreoSoporte() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:contactirisasistant@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "Ayuda y sugerencias - Iris")
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            aviso("No hay una app de correo instalada")
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        vozAsistente.cerrar()
        super.onDestroy()
    }

    private companion object {
        const val MAX_ADJUNTOS = 5
    }
}
