package com.miasistente.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.ui.IrisHomeScreen
import com.miasistente.app.ui.theme.MiAsistenteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var procesador: ProcesadorComandos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de componentes principales
        procesador = ProcesadorComandos(this)

        setContent {
            MiAsistenteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var ultimoMensajeRespuesta by remember { mutableStateOf("") }
                    var estaCargando by remember { mutableStateOf(false) }

                    IrisHomeScreen(
                        respuestaAsistente = ultimoMensajeRespuesta,
                        estaCargando = estaCargando,
                        onSendMessage = { mensajeTexto ->
                            lifecycleScope.launch {
                                estaCargando = true
                                val respuesta = procesador.procesar(mensajeTexto)
                                ultimoMensajeRespuesta = respuesta
                                estaCargando = false
                            }
                        }
                    )
                }
            }
        }
    }
}
