package com.miasistente.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.miasistente.app.comandos.ProcesadorComandos
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.AvatarRepositorio
import com.miasistente.app.data.Permisos
import com.miasistente.app.data.SecureStorage
import com.miasistente.app.service.AsistenteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Permisos que hacen falta para arrancar el servicio de voz. */
private fun permisosParaServicio(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    // Android 13+ exige permiso explícito para mostrar notificaciones
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Pantalla de ajustes del asistente. Orden de las secciones:
 *   1. Encabezado "Asistente IA" con interruptor de encendido
 *   2. Permisos (micrófono, superposición)
 *   3. Palabra clave
 *   4. Avatar
 *   5. Capacidades (offline / online)
 */
@Composable
fun PantallaPrincipal() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ajustes = remember { Ajustes(context) }
    val almacen = remember { SecureStorage(context) }
    val procesador = remember { ProcesadorComandos(context) }

    // --- Estado del servicio de voz (viene de AsistenteService, sin cambios) -------------
    val activo by AsistenteService.isRunning.collectAsStateWithLifecycle()
    val estado by AsistenteService.estadoAudio.collectAsStateWithLifecycle()

    // --- Permisos -------------------------------------------------------------------------
    var microfonoOk by remember { mutableStateOf(Permisos.microfono(context)) }
    var superposicionOk by remember { mutableStateOf(Permisos.superposicion(context)) }
    var microfonoDenegado by remember { mutableStateOf(false) }
    var iniciarTrasPermisos by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Al volver de Ajustes (superposición) hay que volver a comprobar
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        microfonoOk = Permisos.microfono(context)
        superposicionOk = Permisos.superposicion(context)
    }

    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        microfonoOk = Permisos.microfono(context)
        if (!microfonoOk) microfonoDenegado = true
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

    // --- Palabra clave ---------------------------------------------------------------------
    var palabraEscrita by remember { mutableStateOf(ajustes.palabraClave) }

    fun activarServicio() {
        val normalizada = Ajustes.normalizar(palabraEscrita)
        if (normalizada.isEmpty()) {
            mensaje = "Escribe una palabra clave (solo letras)."
            return
        }
        ajustes.palabraClave = normalizada
        palabraEscrita = normalizada

        val faltantes = permisosParaServicio().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (faltantes.isEmpty()) {
            mensaje = null
            AsistenteService.start(context)
        } else {
            iniciarTrasPermisos = true
            lanzadorPermisos.launch(faltantes.toTypedArray())
        }
    }

    // --- Avatar ----------------------------------------------------------------------------
    var avatar by remember { mutableStateOf(ajustes.avatar) }
    var imagenPersonalizada by remember { mutableStateOf<ImageBitmap?>(null) }
    var mensajeAvatar by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { imagenPersonalizada = AvatarRepositorio.cargar(context) }

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

    // --- Gemini y comandos -------------------------------------------------------------------
    var tieneClave by remember { mutableStateOf(false) }
    var probando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<ResultadoComando?>(null) }

    // Leer el almacén cifrado puede tardar un poco: fuera del hilo principal
    LaunchedEffect(Unit) { tieneClave = withContext(Dispatchers.IO) { almacen.tieneApiKey() } }

    // --- Aviso de "palabra clave detectada" (3 segundos) -----------------------------------
    var mostrarActivacion by remember { mutableStateOf(false) }
    LaunchedEffect(estado.activaciones) {
        if (estado.activaciones > 0) {
            mostrarActivacion = true
            delay(3_000)
            mostrarActivacion = false
        } else {
            mostrarActivacion = false
        }
    }

    // --- Textos y colores de estado --------------------------------------------------------
    val preparando = activo && estado.preparandoModelo
    val capturando = activo && estado.capturando
    val palabra = estado.palabraClave.ifEmpty { ajustes.palabraClave }

    val textoEstado = when {
        !activo -> "Detenido"
        mostrarActivacion -> "¡Te escuché!"
        preparando && estado.progresoModelo >= 0 -> "Descargando modelo… ${estado.progresoModelo} %"
        preparando -> "Cargando modelo de voz…"
        estado.hayVoz -> "Oyendo una voz"
        estado.capturando -> "Esperando «$palabra»"
        else -> "Abriendo micrófono…"
    }
    val colorEstado = when {
        !activo -> Paleta.TextoSuave
        mostrarActivacion -> Paleta.Morado
        preparando -> Paleta.Ambar
        estado.hayVoz -> Paleta.Azul
        estado.capturando -> Paleta.Verde
        else -> Paleta.Ambar
    }
    val textoError = mensaje ?: estado.error

    // ==========================================================================================
    // Interfaz
    // ==========================================================================================
    Box(
        Modifier
            .fillMaxSize()
            .background(Paleta.Fondo)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1) Encabezado con interruptor
            Tarjeta(titulo = "Asistente IA", emoji = "🎙️") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(colorEstado, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = textoEstado,
                        color = colorEstado,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = activo,
                        onCheckedChange = { encender ->
                            if (encender) activarServicio() else AsistenteService.stop(context)
                        }
                    )
                }

                if (preparando) {
                    Spacer(Modifier.height(12.dp))
                    if (estado.progresoModelo >= 0) {
                        LinearProgressIndicator(
                            progress = { estado.progresoModelo / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Solo la primera vez (≈ 39 MB). Mantén la app abierta.",
                            color = Paleta.TextoSuave,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                if (capturando) {
                    val nivel = ((estado.nivelDb + 70f) / 60f).coerceIn(0f, 1f)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { nivel }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Nivel: ${estado.nivelDb.roundToInt()} dB · Reconocido: " +
                            (if (estado.ultimoTexto.isEmpty()) "—" else "«${estado.ultimoTexto}»") +
                            " · Activaciones: ${estado.activaciones}",
                        color = Paleta.TextoSuave,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                textoError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Paleta.Rojo, style = MaterialTheme.typography.bodyMedium)
                }
            }

                        // 1) Encabezado con interruptor
           if (activo && (estado.comando.isNotEmpty() || estado.respuesta.isNotEmpty())) {
    Tarjeta(titulo = "Última interacción", emoji = "💬") {
        if (estado.comando.isNotEmpty()) {
            Text("Dijiste: «${estado.comando}»", color = Paleta.TextoSuave)
        }
        if (estado.respuesta.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                (if (estado.respuestaOk) "✔ " else "✖ ") + estado.respuesta,
                color = if (estado.respuestaOk) Paleta.Verde else Paleta.Rojo
            )
                    }
                }
            }
            // 2) Permisos
            SeccionPermisos(
                microfonoOk = microfonoOk,
                superposicionOk = superposicionOk,
                onPedirMicrofono = {
                    if (microfonoDenegado) {
                        context.startActivity(Permisos.intentAjustesApp(context))
                    } else {
                        lanzadorPermisos.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                },
                onPedirSuperposicion = {
                    context.startActivity(Permisos.intentSuperposicion(context))
                }
            )

            // 3) Palabra clave
            Tarjeta(titulo = "Palabra clave", emoji = "🗝️") {
                OutlinedTextField(
                    value = palabraEscrita,
                    onValueChange = { palabraEscrita = it.take(30) },
                    enabled = !activo,
                    label = { Text("Palabra o frase de activación") },
                    placeholder = { Text("Oye Asistente") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (activo) "Apaga el asistente para cambiarla"
                            else "Palabras comunes del español, de 3 o más sílabas. Los nombres raros pueden no reconocerse."
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4) Avatar
            SeccionAvatar(
                seleccionado = avatar,
                imagenPersonalizada = imagenPersonalizada,
                mensaje = mensajeAvatar,
                onSeleccionar = {
                    avatar = it
                    ajustes.avatar = it
                },
                onElegirGaleria = { lanzadorGaleria.launch("image/*") }
            )

            // 5) Capacidades
            SeccionCapacidades(
                tieneClave = tieneClave,
                probando = probando,
                resultado = resultado,
                onGuardarClave = { clave ->
                    scope.launch {
                        withContext(Dispatchers.IO) { almacen.guardarApiKey(clave) }
                        tieneClave = true
                    }
                },
                onProbar = { texto ->
                    scope.launch {
                        probando = true
                        resultado = procesador.procesar(texto)
                        probando = false
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
