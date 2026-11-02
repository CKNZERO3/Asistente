package com.miasistente.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.AvatarRepositorio
import com.miasistente.app.data.Permisos
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IrisHomeScreen(
    assistantState: AssistantState,
    ultimoComando: String,
    resultadoComando: ResultadoComando?,
    estaProcesando: Boolean,
    onSendMessage: (String) -> Unit,
    onAlternarServicio: () -> Unit,
    isServiceRunning: Boolean
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val ajustes = remember { Ajustes(context) }
    var avatarId by remember { mutableStateOf(ajustes.avatar) }
    var imagenPersonalizada by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(Unit) {
        imagenPersonalizada = AvatarRepositorio.cargar(context)
    }

    val formatoFecha = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")) }
    val formatoHora = remember { SimpleDateFormat("HH:mm", Locale("es", "ES")) }
    val fechaActual = remember { formatoFecha.format(Date()).replaceFirstChar { it.uppercase() } }
    val horaActual = remember { formatoHora.format(Date()) }

    val microfonoOk = Permisos.microfono(context)
    val superposicionOk = Permisos.superposicion(context)
    val hayAvisoPermisos = !microfonoOk || !superposicionOk

    var textoInput by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PaletaIris.PanelLateral
            ) {
                IrisDrawerContent(
                    context = context,
                    onCerrar = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        FondoNoche(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                BarraSuperiorIris(
                    activo = isServiceRunning,
                    avatarId = avatarId,
                    imagenPersonalizada = imagenPersonalizada,
                    hayAviso = hayAvisoPermisos,
                    onAbrirAjustes = { scope.launch { drawerState.open() } }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        CabeceraFechaClima(fecha = fechaActual, hora = horaActual)
                        Spacer(Modifier.height(24.dp))
                        SaludoPrincipal(saludo = "Hola, Cristian")
                        
                        Spacer(Modifier.height(24.dp))

                        ChipEstado(
                            texto = when (assistantState) {
                                AssistantState.IDLE -> if (isServiceRunning) "Iris escuchando..." else "Iris pausada"
                                AssistantState.LISTENING -> "Escuchando tu voz..."
                                AssistantState.THINKING -> "Pensando..."
                                AssistantState.SPEAKING -> "Respondiendo..."
                            },
                            color = if (isServiceRunning) PaletaIris.Acento else Color.Gray
                        )

                        if (ultimoComando.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            TarjetaConversacion(
                                comando = ultimoComando,
                                resultado = resultadoComando,
                                procesando = estaProcesando,
                                onCerrar = { }
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        BarraEntradaIris(
                            texto = textoInput,
                            onTexto = { textoInput = it },
                            onEnviar = {
                                if (textoInput.isNotBlank()) {
                                    onSendMessage(textoInput)
                                    textoInput = ""
                                }
                            },
                            estado = assistantState,
                            activo = isServiceRunning,
                            onMicrofono = {
                                onAlternarServicio()
                            }
                        )
                    }
                }
            }
        }
    }
}
