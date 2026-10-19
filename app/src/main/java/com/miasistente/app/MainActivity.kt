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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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

/**
 * Pantalla principal (Fase 1): un indicador de estado y un botón
 * para activar/desactivar el servicio en primer plano.
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

    // true/false según el estado real del servicio (se actualiza solo)
    val activo by AsistenteService.isRunning.collectAsStateWithLifecycle()

    // Mensaje de error visible (por ejemplo, si se niega el micrófono)
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

    // Verde cuando está activo, gris cuando está detenido (con transición suave)
    val colorIndicador by animateColorAsState(
        targetValue = if (activo) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
        label = "colorIndicador"
    )

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

            Text(
                text = if (activo) "Servicio activo" else "Servicio detenido",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (activo) "Listo para escuchar" else "Pulsa el botón para activarlo",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
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

            mensaje?.let {
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
