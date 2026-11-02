package com.miasistente.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.data.PerfilUsuario
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.ui.IrisHomeScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val procesador = ProcesadorComandos(this)
        val perfil = PerfilUsuario(this)

        setContent {
            val isServiceActive by AsistenteService.isRunning.collectAsStateWithLifecycle()

            IrisHomeScreen(
                userName = perfil.nombre.ifBlank { "Chris" },
                temperature = null,
                weatherLabel = null,
                onSendMessage = { mensajeTexto ->
                    procesador.procesar(mensajeTexto)
                },
                onMicrophoneClick = {
                    if (isServiceActive) {
                        AsistenteService.stop(this)
                    } else {
                        AsistenteService.start(this)
                    }
                }
            )
        }
    }
}
 
