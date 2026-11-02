package com.miasistente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.IrisScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var procesador: ProcesadorComandos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        procesador = ProcesadorComandos(this)
        AsistenteService.start(this)

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
                        onAvatarChanged = { enabled ->
                            avatarEnabled = enabled
                        },
                        onSend = { mensajeTexto ->
                            lifecycleScope.launch {
                                AsistenteService.updateAssistantState(AssistantState.THINKING)
                                procesador.procesar(mensajeTexto)
                                AsistenteService.updateAssistantState(AssistantState.IDLE)
                            }
                        },
                        onVoice = {
                            if (isServiceRunning) {
                                AsistenteService.stop(this)
                            } else {
                                AsistenteService.start(this)
                            }
                        },
                        onAttach = {
                            // Lógica para adjuntar
                        },
                        onAction = { destination ->
                            when (destination) {
                                "sign_in" -> { /* Iniciar sesión */ }
                                "search" -> { /* Buscar */ }
                                "gallery" -> { /* Galería */ }
                                "new_chat" -> { /* Nuevo chat */ }
                                "permissions" -> { /* Permisos */ }
                                "about" -> { /* Acerca de */ }
                                "privacy" -> { /* Privacidad */ }
                                "support" -> { /* Soporte */ }
                                "settings" -> { /* Ajustes */ }
                            }
                        }
                    )
                }
            }
        }
    }
}
