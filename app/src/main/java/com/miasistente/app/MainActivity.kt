package com.miasistente.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.ChatAttachment
import com.miasistente.app.ui.ChatViewModel
import com.miasistente.app.ui.IrisScreen
import com.miasistente.app.ui.PermissionKind
import com.miasistente.app.ui.PermissionPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var procesador: ProcesadorComandos
    private val chat: ChatViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences("iris_prefs", MODE_PRIVATE) }
    private val googleSignIn by lazy { GoogleSignInManager(this) }

    // Estado de la interfaz que vive en la actividad
    private var userName by mutableStateOf<String?>(null)
    private var userEmail by mutableStateOf<String?>(null)
    private var userPhoto by mutableStateOf<String?>(null)
    private var avatarEnabled by mutableStateOf(false)
    private var darkTheme by mutableStateOf(true)
    private var permissionPrompt by mutableStateOf<PermissionPrompt?>(null)

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) agregarAdjunto(uri)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla completa real: evita que el teclado "empuje" toda la ventana
        WindowCompat.setDecorFitsSystemWindows(window, false)

        procesador = ProcesadorComandos(this)

        // Restaurar preferencias
        darkTheme = prefs.getBoolean("dark_theme", true)
        userName = prefs.getString("user_name", null)
        userEmail = prefs.getString("user_email", null)
        userPhoto = prefs.getString("user_photo", null)

        // Solo reanuda la escucha si el usuario la había activado y ya hay permiso
        if (prefs.getBoolean("mic_enabled", false) && tienePermisoMicrofono()) {
            AsistenteService.start(this)
        }

        // Construye el índice de apps en segundo plano
        lifecycleScope.launch(Dispatchers.Default) {
            EjecutorLocal(applicationContext).precalentar()
        }

        // Los comandos por voz también aparecen en el chat
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AsistenteService.eventosComando.collect { evento ->
                    chat.addUser(evento.comandoUsuario)
                    chat.addAssistant(evento.respuesta)
                }
            }
        }

        setContent {
            AsistenteTema {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val isServiceRunning by AsistenteService.isRunning.collectAsState()
                    val audio by AsistenteService.estadoAudio.collectAsState()

                    // Iconos de la barra de estado legibles en modo claro y oscuro
                    val view = LocalView.current
                    SideEffect {
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }

                    // Si el servicio de voz falla, se le avisa al usuario
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
                        isThinking = chat.isThinking,
                        micActive = isServiceRunning,
                        micPulse = isServiceRunning && audio.hayVoz,
                        voiceStatus = voiceStatus,
                        permissionPrompt = permissionPrompt,
                        onPermissionConfirm = { confirmarPermiso() },
                        onPermissionDismiss = { permissionPrompt = null },
                        onAvatarChanged = { avatarEnabled = it },
                        onSend = { enviar(it) },
                        onVoice = { alternarMicrofono(isServiceRunning) },
                        onAttach = { pickFile.launch("*/*") },
                        onAction = { destination -> manejarAccion(destination) }
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------------------------

    private fun enviar(texto: String) {
        chat.addUser(texto)
        chat.updateThinking(true)

        lifecycleScope.launch {
            AsistenteService.updateAssistantState(AssistantState.THINKING)
            try {
                val resultado = withContext(Dispatchers.IO) { procesador.procesar(texto) }
                chat.addAssistant(resultado.mensaje.ifBlank { "Listo." })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                chat.addAssistant("No pude completar eso: ${e.message ?: "error desconocido"}")
            } finally {
                chat.updateThinking(false)
                AsistenteService.updateAssistantState(AssistantState.IDLE)
            }
        }
    }

    private fun agregarAdjunto(uri: Uri) {
        lifecycleScope.launch {
            val adjunto = withContext(Dispatchers.IO) { leerAdjunto(uri) }
            chat.addAttachment(adjunto)
        }
    }

    private fun leerAdjunto(uri: Uri): ChatAttachment {
        val mime = contentResolver.getType(uri)
        var nombre = uri.lastPathSegment ?: "archivo"
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
        } catch (e: Exception) {
            // Si no se puede leer el nombre, se usa el valor por defecto
        }
        return ChatAttachment(uri.toString(), nombre, mime, tamano)
    }

    // ---------------------------------------------------------------------------------
    // Micrófono
    // ---------------------------------------------------------------------------------

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
        // Primero se explica en una tarjeta discreta; el permiso del sistema solo se pide
        // si el usuario acepta. Si Android ya no deja preguntar, se ofrece abrir los ajustes.
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

    // ---------------------------------------------------------------------------------
    // Tema y sesión
    // ---------------------------------------------------------------------------------

    private fun cambiarTema(oscuro: Boolean) {
        darkTheme = oscuro
        prefs.edit().putBoolean("dark_theme", oscuro).apply()
    }

    /** Inicio de sesión con Google (Credential Manager). El menú cambia solo al completarse. */
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

    // ---------------------------------------------------------------------------------
    // Acciones del menú y de Ajustes
    // (ajustes, about, privacy y account las resuelve la propia IrisScreen)
    // ---------------------------------------------------------------------------------

    private fun manejarAccion(destination: String) {
        when (destination) {
            "new_chat" -> chat.clear()
            "permissions" -> abrirAjustesDeLaApp()
            "support" -> enviarCorreoSoporte()
            "sign_out" -> cerrarSesion()
            "sign_in" -> iniciarSesionGoogle()
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
        } catch (e: ActivityNotFoundException) {
            aviso("No se pudo abrir los ajustes")
        }
    }

    private fun enviarCorreoSoporte() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:contactirisasistant@gmail.com") // <- cambia por tu correo
            putExtra(Intent.EXTRA_SUBJECT, "Ayuda y sugerencias - Iris")
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            aviso("No hay una app de correo instalada")
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }
}
