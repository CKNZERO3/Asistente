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
import com.miasistente.app.comandos.EjecutorLocal
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
    private var pendingAttachment by mutableStateOf<ChatAttachment?>(null)

    private var conversations by mutableStateOf<List<SavedConversation>>(emptyList())
    private var activeConversationId by mutableStateOf("conv_${System.currentTimeMillis()}")
    private val thinkingConversations = mutableStateMapOf<String, Boolean>()

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) prepararAdjunto(uri, esFoto = false)
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) prepararAdjunto(uri, esFoto = true)
    }

    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            lifecycleScope.launch {
                val adjunto = withContext(Dispatchers.IO) { guardarCapturaCamara(bitmap) }
                if (adjunto != null) {
                    pendingAttachment = adjunto
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
                    if (targetConvId == activeConversationId) {
                        vozAsistente.hablar(evento.respuesta)
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
                        micActive = isServiceRunning,
                        micPulse = isServiceRunning && audio.hayVoz,
                        voiceStatus = voiceStatus,
                        permissionPrompt = permissionPrompt,
                        pendingAttachment = pendingAttachment,
                        onClearAttachment = { pendingAttachment = null },
                        onPermissionConfirm = { confirmarPermiso() },
                        onPermissionDismiss = { permissionPrompt = null },
                        onAvatarChanged = { avatarEnabled = it },
                        onSend = { enviar(it) },
                        onVoice = { alternarMicrofono(isServiceRunning) },
                        onStopSpeaking = { vozAsistente.detener() },
                        onAttach = { pickFile.launch("*/*") },
                        onPhotoSelected = { uri -> prepararAdjunto(uri, esFoto = true) },
                        onAction = { destination -> manejarAccion(destination) }
                    )
                }
            }
        }
    }

    private fun iniciarNuevaConversacion() {
        vozAsistente.detener()
        pendingAttachment = null
        chat.clear()
        chat.updateThinking(false)
        activeConversationId = "conv_${System.currentTimeMillis()}"
    }

    private fun seleccionarConversacion(conv: SavedConversation) {
        vozAsistente.detener()
        pendingAttachment = null
        activeConversationId = conv.id
        chat.clear()
        conv.messages.forEach { saved ->
            if (saved.attachment != null) {
                chat.addAttachment(
                    ChatAttachment(
                        uri = saved.attachment.uri,
                        name = saved.attachment.name,
                        mime = saved.attachment.mime,
                        sizeBytes = saved.attachment.sizeBytes
                    )
                )
            }
            if (saved.text.isNotBlank()) {
                if (saved.fromUser) chat.addUser(saved.text)
                else chat.addAssistant(saved.text)
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
            ?: mensajes.firstOrNull { it.attachment != null }?.attachment?.name
            ?: "Nueva conversación"
        return if (base.length > 34) base.take(34) + "…" else base
    }

    private fun enviar(texto: String) {
        val adjuntoPendiente = pendingAttachment
        if (texto.isBlank() && adjuntoPendiente == null) return

        val targetConvId = activeConversationId

        if (adjuntoPendiente != null) {
            chat.addAttachment(adjuntoPendiente)
            pendingAttachment = null
            guardarMensajeEnHistorial(
                convId = targetConvId,
                nuevoMensaje = SavedChatMessage(
                    text = "",
                    fromUser = true,
                    attachment = SavedChatAttachment(
                        uri = adjuntoPendiente.uri,
                        name = adjuntoPendiente.name,
                        mime = adjuntoPendiente.mime,
                        sizeBytes = adjuntoPendiente.sizeBytes
                    )
                )
            )
        }

        if (texto.isNotBlank()) {
            chat.addUser(texto)
            guardarMensajeEnHistorial(
                convId = targetConvId,
                nuevoMensaje = SavedChatMessage(text = texto, fromUser = true)
            )
        }

        thinkingConversations[targetConvId] = true
        if (targetConvId == activeConversationId) {
            chat.updateThinking(true)
        }

        val adjuntoObjetivo = adjuntoPendiente ?: if (PoliticaArchivos.pideResumirArchivo(texto)) {
            chat.messages.lastOrNull { it.attachment != null && !it.attachment.isImage }?.attachment
        } else {
            null
        }

        lifecycleScope.launch {
            AsistenteService.updateAssistantState(AssistantState.THINKING)
            try {
                val resultado = withTimeoutOrNull(30_000L) {
                    withContext(Dispatchers.IO) {
                        when {
                            adjuntoObjetivo != null && !adjuntoObjetivo.isImage -> {
                                val lectura = PoliticaArchivos.leer(
                                    contentResolver,
                                    Uri.parse(adjuntoObjetivo.uri),
                                    adjuntoObjetivo.name,
                                    adjuntoObjetivo.mime,
                                    adjuntoObjetivo.sizeBytes
                                )
                                procesador.resumirArchivo(
                                    nombre = adjuntoObjetivo.name,
                                    lectura = lectura,
                                    instruccionUsuario = texto.takeIf { it.isNotBlank() }
                                )
                            }
                            adjuntoObjetivo != null && adjuntoObjetivo.isImage -> {
                                val consultaImagen = if (texto.isBlank()) {
                                    "El usuario adjuntó la imagen «${adjuntoObjetivo.name}». Indícale brevemente cómo puedes ayudarle con ella."
                                } else {
                                    "$texto (imagen adjunta: ${adjuntoObjetivo.name})"
                                }
                                procesador.procesar(consultaImagen)
                            }
                            else -> procesador.procesar(texto)
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
                    if (resultado.exito) {
                        vozAsistente.hablar(respuestaFinal)
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

    private fun prepararAdjunto(uri: Uri, esFoto: Boolean) {
        lifecycleScope.launch {
            val adjunto = withContext(Dispatchers.IO) {
                val item = leerAdjunto(uri)
                val tipo = if (esFoto || item.isImage) "photo" else "file"
                val uriPersistente = copiarArchivoLocalmente(uri, item.name)
                saveUriToIrisMedia(applicationContext, uri, tipo)
                if (uriPersistente != null) {
                    item.copy(uri = uriPersistente)
                } else {
                    item
                }
            }
            pendingAttachment = adjunto
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
            "stop_speaking", "stop_external_agent_voice" -> vozAsistente.detener()
            "search" -> aviso("Búsqueda próximamente")
            "gallery" -> aviso("Galería próximamente")
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
}
