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
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.IrisHomeScreen
import kotlinx.coroutines.flow.collectAsState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var procesador: ProcesadorComandos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        procesador = ProcesadorComandos(this)

        // Iniciar servicio en segundo plano automáticamente si se desea
        AsistenteService.start(this)

        setContent {
            AsistenteTema {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val assistantState by AsistenteService.assistantState.collectAsState()
                    val isServiceRunning by AsistenteService.isRunning.collectAsState()

                    var ultimoComando by remember { mutableStateOf("") }
                    var resultadoComando by remember { mutableStateOf<ResultadoComando?>(null) }
                    var estaProcesando by remember { mutableStateOf(false) }

                    IrisHomeScreen(
                        assistantState = assistantState,
                        ultimoComando = ultimoComando,
                        resultadoComando = resultadoComando,
                        estaProcesando = estaProcesando,
                        isServiceRunning = isServiceRunning,
                        onSendMessage = { mensajeTexto ->
                            ultimoComando = mensajeTexto
                            resultadoComando = null
                            lifecycleScope.launch {
                                estaProcesando = true
                                AsistenteService.updateAssistantState(AssistantState.THINKING)
                                
                                val res = procesador.procesar(mensajeTexto)
                                resultadoComando = res
                                
                                estaProcesando = false
                                AsistenteService.updateAssistantState(AssistantState.IDLE)
                            }
                        },
                        onAlternarServicio = {
                            if (isServiceRunning) {
                                AsistenteService.stop(this)
                            } else {
                                AsistenteService.start(this)
                            }
                        }
                    )
                }
            }
        }
    }
}
