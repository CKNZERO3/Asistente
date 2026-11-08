package com.miasistente.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.miasistente.app.comandos.EjecutorLocal
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.ChatViewModel
import com.miasistente.app.ui.IrisScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var procesador: ProcesadorComandos
    private val chat: ChatViewModel by viewModels()

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) chat.addUser("📎 Archivo adjunto")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        procesador = ProcesadorComandos(this)
        AsistenteService.start(this)

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

                    var userName by remember { mutableStateOf<String?>(null) }
                    var avatarEnabled by remember { mutableStateOf(false) }

                    IrisScreen(
                        userName = userName,
                        profilePicture = null,
                        avatarEnabled = avatarEnabled,
                        messages = chat.messages,
                        isThinking = chat.isThinking,
                        onAvatarChanged = { avatarEnabled = it },
                        onSend = { enviar(it) },
                        onVoice = {
                            if (isServiceRunning) AsistenteService.stop(this@MainActivity)
                            else AsistenteService.start(this@MainActivity)
                        },
                        onAttach = { pickFile.launch("*/*") },
                        onAction = { destination -> manejarAccion(destination) }
                    )
                }
            }
        }
    }

    private fun enviar(texto: String) {
        chat.addUser(texto)
        chat.setThinking(true)

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
                chat.setThinking(false)
                AsistenteService.updateAssistantState(AssistantState.IDLE)
            }
        }
    }

    private fun manejarAccion(destination: String) {
        when (destination) {
            "new_chat" -> chat.clear()
            "permissions", "settings" -> abrirAjustesDeLaApp()
            "support" -> enviarCorreoSoporte()
            "sign_in" -> aviso("Inicio de sesión próximamente")
            "search" -> aviso("Búsqueda próximamente")
            "gallery" -> aviso("Galería próximamente")
            "about" -> aviso("Iris — tu asistente personal")
            "privacy" -> aviso("Privacidad y datos próximamente")
            "account" -> aviso("Cuenta próximamente")
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
            data = Uri.parse("mailto:soporte@tudominio.com") // <- cambia por tu correo
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
