package com.miasistente.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miasistente.app.service.AsistenteService
import kotlin.math.roundToInt

/**
 * Pantalla principal (Fase 2): botón para activar/desactivar el servicio, indicador de
 * estado del micrófono y barra de volumen en vivo.
 *
 * Colores del indicador:
 *   gris   = servicio detenido
 *   ámbar  = servicio activo, abriendo el micrófono
 *   verde  = micrófono capturando audio (silencio o ruido de fondo)
 *   azul   = voz detectada
 *
 * Más adelante la interfaz se moverá a la carpeta ui/.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AsistenteTheme {
                PantallaPrincipal()
            }
        }
    }
}

/** Permisos que hay que pedir antes de iniciar el servicio. */
private fun permisosNecesarios(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    // Android 13+ exige permiso explícito para mostrar notificaciones
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AsistenteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
private fun PantallaPrincipal() {
    val context = LocalContext.current

    // Estado real del servicio y del audio (se actualizan solos)
    val activo by AsistenteService.isRunning.collectAsStateWithLifecycle()
    val estado by AsistenteService.estadoAudio.collectAsStateWithLifecycle()

    // Mensaje de error de permisos (por ejemplo, si se niega el micrófono)
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Lanzador del cuadro de permisos del sistema
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Lo único imprescindible es el micrófono; las notificaciones son opcionales
        val microfonoOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (microfonoOk) {
            mensaje = null
            AsistenteService.start(context)
        } else {
            mensaje = "Sin permiso de micrófono no puedo escuchar."
        }
    }

    // --- Qué mostrar según el estado ---------------------------------------------------
    val colorObjetivo = when {
        !activo -> Color(0xFF9E9E9E)          // gris
        estado.hayVoz -> Color(0xFF1565C0)    // azul
        estado.capturando -> Color(0xFF2E7D32) // verde
        else -> Color(0xFFF9A825)             // ámbar
    }
    val colorIndicador by animateColorAsState(colorObjetivo, label = "colorIndicador")

    val titulo = when {
        !activo -> "Servicio detenido"
        estado.hayVoz -> "Voz detectada"
        estado.capturando -> "Escuchando…"
        else -> "Abriendo micrófono…"
    }
    val subtitulo = when {
        !activo -> "Pulsa el botón para activarlo"
        estado.hayVoz -> "Estoy oyendo una voz"
        estado.capturando -> "Micrófono capturando audio"
        else -> "Un momento"
    }

    // Volumen: de -70 dB (muy bajo) a -10 dB (muy alto) convertido a 0..1 para la barra
    val capturandoDeVerdad = activo && estado.capturando
    val nivelNormalizado = ((estado.nivelDb + 70f) / 60f).coerceIn(0f, 1f)
    val nivelAnimado by animateFloatAsState(
        targetValue = if (capturandoDeVerdad) nivelNormalizado else 0f,
        label = "nivelVolumen"
    )

    // El error de permisos tiene prioridad; si no hay, se muestra el error de audio
    val textoError = mensaje ?: estado.error

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Indicador visual de estado
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(colorIndicador, CircleShape)
            )

            Spacer(Modifier.height(24.dp))

            Text(text = titulo, style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(8.dp))

            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Barra de volumen en vivo
            LinearProgressIndicator(
                progress = { nivelAnimado },
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (capturandoDeVerdad) "Nivel: ${estado.nivelDb.roundToInt()} dB" else "Nivel: —",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (activo) {
                        AsistenteService.stop(context)
                    } else {
                        // Pedir solo los permisos que aún no están concedidos
                        val faltantes = permisosNecesarios().filter {
                            ContextCompat.checkSelfPermission(context, it) !=
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (faltantes.isEmpty()) {
                            mensaje = null
                            AsistenteService.start(context)
                        } else {
                            lanzadorPermisos.launch(faltantes.toTypedArray())
                        }
                    }
                }
            ) {
                Text(if (activo) "Desactivar" else "Activar")
            }

            textoError?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
