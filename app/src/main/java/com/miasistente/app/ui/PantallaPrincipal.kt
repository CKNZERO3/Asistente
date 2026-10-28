
package com.miasistente.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miasistente.app.comandos.Nota
import com.miasistente.app.comandos.NotasRepositorio
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.AvatarRepositorio
import com.miasistente.app.data.Permisos
import com.miasistente.app.data.SecureStorage
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.service.OverlayAvatarService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private fun permisosParaServicio(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun PantallaPrincipal() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ajustes = remember { Ajustes(context) }
    val almacen = remember { SecureStorage(context) }
    val procesador = remember { ProcesadorComandos(context) }
    val notasRepositorio = remember { NotasRepositorio(context) }

    val activo by AsistenteService.isRunning.collectAsStateWithLifecycle()
    val estado by AsistenteService.estadoAudio.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    text = "¡Hola! Soy tu asistente inteligente. ¿En qué puedo ayudarte?",
                    isUser = false
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var notasList by remember { mutableStateOf(emptyList<Nota>()) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            notasList = withContext(Dispatchers.IO) {
                notasRepositorio.listar()
            }
        }
    }

    var microfonoOk by remember {
        mutableStateOf(Permisos.microfono(context))
    }
    var superposicionOk by remember {
        mutableStateOf(Permisos.superposicion(context))
    }
    var microfonoDenegado by remember { mutableStateOf(false) }
    var iniciarTrasPermisos by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Actualizar los permisos al regresar desde Ajustes del sistema.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        microfonoOk = Permisos.microfono(context)
        superposicionOk = Permisos.superposicion(context)
    }

    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        microfonoOk = Permisos.microfono(context)

        if (!microfonoOk) {
            microfonoDenegado = true
        }

        if (iniciarTrasPermisos) {
            iniciarTrasPermisos = false

            if (microfonoOk) {
                mensaje = null
                AsistenteService.start(context)
            } else {
                mensaje = "Sin permiso de micrófono no puedo escuchar."
            }
        }
    }

    var palabraEscrita by remember {
        mutableStateOf(ajustes.palabraClave)
    }

    fun activarServicio() {
        val normalizada = Ajustes.normalizar(palabraEscrita)

        if (normalizada.isEmpty()) {
            mensaje = "Escribe una palabra clave (solo letras)."
            return
        }

        ajustes.palabraClave = normalizada
        palabraEscrita = normalizada

        val faltantes = permisosParaServicio().filter {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (faltantes.isEmpty()) {
            mensaje = null
            AsistenteService.start(context)
        } else {
            iniciarTrasPermisos = true
            lanzadorPermisos.launch(faltantes.toTypedArray())
        }
    }

    var avatar by remember { mutableStateOf(ajustes.avatar) }
    var imagenPersonalizada by remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    var mensajeAvatar by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        imagenPersonalizada = AvatarRepositorio.cargar(context)
    }

    val lanzadorGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (AvatarRepositorio.guardarDesdeUri(context, uri)) {
                    imagenPersonalizada = AvatarRepositorio.cargar(context)
                    avatar = Ajustes.AVATAR_PERSONALIZADO
                    ajustes.avatar = avatar
                    mensajeAvatar = null
                } else {
                    mensajeAvatar = "No pude leer esa imagen. Prueba con otra."
                }
            }
        }
    }

    var tieneClave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        tieneClave = withContext(Dispatchers.IO) {
            almacen.tieneApiKey()
        }
    }

    var overlayActivo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paleta.Fondo)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            Surface(
                color = Paleta.Tarjeta,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🤖 Asistente IA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Paleta.Texto,
                            modifier = Modifier.weight(1f)
                        )

                        FilledTonalButton(
                            onClick = {
                                // Verificar el permiso real cada vez que se pulsa.
                                superposicionOk =
                                    Permisos.superposicion(context)

                                if (!superposicionOk) {
                                    context.startActivity(
                                        Permisos.intentSuperposicion(context)
                                    )
                                } else if (overlayActivo) {
                                    OverlayAvatarService.stop(context)
                                    overlayActivo = false
                                } else {
                                    OverlayAvatarService.start(context)
                                    overlayActivo = true
                                }
                            }
                        ) {
                            Text(
                                if (overlayActivo) {
                                    "🪟 Ocultar Flotante"
                                } else {
                                    "🪟 Avatar Flotante"
                                }
                            )
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Paleta.Tarjeta,
                contentColor = Paleta.Primario
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("💬 Chat") }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("📝 Notas") }
                )

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("⚙️ Controles") }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> ChatTabContent(
                        messages = chatMessages,
                        inputText = inputText,
                        onInputChanged = { inputText = it },
                        isSending = isSending,
                        onSend = { query ->
                            if (query.isBlank() || isSending) {
                                return@ChatTabContent
                            }

                            chatMessages = chatMessages + ChatMessage(
                                text = query,
                                isUser = true
                            )

                            inputText = ""
                            isSending = true

                            scope.launch {
                                try {
                                    val resultado = procesador.procesar(query)

                                    chatMessages = chatMessages + ChatMessage(
                                        text = resultado.mensaje,
                                        isUser = false
                                    )
                                } catch (exception: Exception) {
                                    chatMessages = chatMessages + ChatMessage(
                                        text = "No pude procesar el comando. Inténtalo de nuevo.",
                                        isUser = false
                                    )
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    )

                    1 -> NotesTabContent(notes = notasList)

                    2 -> ControlsTabContent(
                        activo = activo,
                        estado = estado,
                        microfonoOk = microfonoOk,
                        superposicionOk = superposicionOk,
                        palabraEscrita = palabraEscrita,
                        onPalabraChanged = { palabraEscrita = it },
                        avatar = avatar,
                        imagenPersonalizada = imagenPersonalizada,
                        mensajeAvatar = mensajeAvatar,
                        tieneClave = tieneClave,
                        onActivarServicio = { activarServicio() },
                        onPedirMicrofono = {
                            if (microfonoDenegado) {
                                context.startActivity(
                                    Permisos.intentAjustesApp(context)
                                )
                            } else {
                                lanzadorPermisos.launch(
                                    arrayOf(Manifest.permission.RECORD_AUDIO)
                                )
                            }
                        },
                        onPedirSuperposicion = {
                            context.startActivity(
                                Permisos.intentSuperposicion(context)
                            )
                        },
                        onSeleccionarAvatar = {
                            avatar = it
                            ajustes.avatar = it
                        },
                        onElegirGaleria = {
                            lanzadorGaleria.launch("image/*")
                        },
                        onGuardarClave = { clave ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    almacen.guardarApiKey(clave)
                                }
                                tieneClave = true
                            }
                        },
                        onProbarComando = { texto ->
                            scope.launch {
                                try {
                                    val res = procesador.procesar(texto)

                                    chatMessages = chatMessages + ChatMessage(
                                        text = "Prueba [$texto]: ${res.mensaje}",
                                        isUser = false
                                    )
                                    selectedTab = 0
                                } catch (exception: Exception) {
                                    chatMessages = chatMessages + ChatMessage(
                                        text = "Error al probar el comando. Inténtalo de nuevo.",
                                        isUser = false
                                    )
                                    selectedTab = 0
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatTabContent(
    messages: List<ChatMessage>,
    inputText: String,
    onInputChanged: (String) -> Unit,
    isSending: Boolean,
    onSend: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(message = msg)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text("Escribe un mensaje o comando...")
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSend(inputText) },
                enabled = inputText.isNotBlank() && !isSending
            ) {
                Text(if (isSending) "..." else "Enviar")
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment =
        if (message.isUser) Alignment.End else Alignment.Start

    val backgroundColor =
        if (message.isUser) Paleta.Primario else Paleta.TarjetaAlta

    val textColor =
        if (message.isUser) Paleta.SobrePrimario else Paleta.Texto

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun NotesTabContent(notes: List<Nota>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (notes.isEmpty()) {
            Text(
                text = "No hay notas guardadas aún.",
                color = Paleta.TextoSuave,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            notes.forEach { note ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Paleta.Tarjeta,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = note.texto,
                            color = Paleta.Texto,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.height(4.dp))

                        val fechaStr = SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            Locale.getDefault()
                        ).format(Date(note.fechaMs))

                        Text(
                            text = fechaStr,
                            color = Paleta.TextoSuave,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlsTabContent(
    activo: Boolean,
    estado: com.miasistente.app.service.EstadoAudio,
    microfonoOk: Boolean,
    superposicionOk: Boolean,
    palabraEscrita: String,
    onPalabraChanged: (String) -> Unit,
    avatar: String,
    imagenPersonalizada: ImageBitmap?,
    mensajeAvatar: String?,
    tieneClave: Boolean,
    onActivarServicio: () -> Unit,
    onPedirMicrofono: () -> Unit,
    onPedirSuperposicion: () -> Unit,
    onSeleccionarAvatar: (String) -> Unit,
    onElegirGaleria: () -> Unit,
    onGuardarClave: (String) -> Unit,
    onProbarComando: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AssistantControlCard()

        SeccionPermisos(
            microfonoOk = microfonoOk,
            superposicionOk = superposicionOk,
            onPedirMicrofono = onPedirMicrofono,
            onPedirSuperposicion = onPedirSuperposicion
        )

        Tarjeta(
            titulo = "Palabra clave",
            emoji = "🗝️"
        ) {
            OutlinedTextField(
                value = palabraEscrita,
                onValueChange = onPalabraChanged,
                enabled = !activo,
                label = {
                    Text("Palabra o frase de activación")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SeccionAvatar(
            seleccionado = avatar,
            imagenPersonalizada = imagenPersonalizada,
            mensaje = mensajeAvatar,
            onSeleccionar = onSeleccionarAvatar,
            onElegirGaleria = onElegirGaleria
        )

        SeccionCapacidades(
            tieneClave = tieneClave,
            probando = false,
            resultado = null,
            onGuardarClave = onGuardarClave,
            onProbar = onProbarComando
        )
    }
}
