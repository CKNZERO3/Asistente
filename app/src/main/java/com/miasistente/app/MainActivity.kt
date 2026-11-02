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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var procesador: ProcesadorComandos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        procesador = ProcesadorComandos(this)

        setContent {
            // Usamos MaterialTheme directamente si el tema general está en el root o ajusta tu paquete de tema
            MaterialTheme {
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
                                val resultado = procesador.procesar(mensajeTexto)
                                // Convertimos el resultado a texto dependiendo de cómo esté estructurado tu objeto
                                ultimoMensajeRespuesta = resultado.toString() 
                                estaCargando = false
                            }
                        }
                    )
                }
            }
        }
    }
}
