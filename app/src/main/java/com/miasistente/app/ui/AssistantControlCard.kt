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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miasistente.app.service.AsistenteService
import com.miasistente.app.ui.avatar.AssistantAvatarView

@Composable
fun AssistantControlCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Única fuente de verdad para el estado visual del avatar.
    val currentAvatarState by
        AsistenteService.assistantState.collectAsStateWithLifecycle()

    val isRunning by
        AsistenteService.isRunning.collectAsStateWithLifecycle()

    val audioState by
        AsistenteService.estadoAudio.collectAsStateWithLifecycle()

    var permissionDenied by remember {
        mutableStateOf(false)
    }

    var pendingStart by remember {
        mutableStateOf(false)
    }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = requiredPermissions.all { permission ->
            permissions[permission] == true ||
                ContextCompat.checkSelfPermission(
                    context,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            permissionDenied = false

            if (pendingStart) {
                pendingStart = false
                AsistenteService.start(context)
            }
        } else {
            permissionDenied = true
            pendingStart = false
        }
    }

    fun triggerVoiceSessionToggle() {
        if (isRunning) {
            AsistenteService.stop(context)
            return
        }

        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            permissionDenied = false
            AsistenteService.start(context)
        } else {
            pendingStart = true
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Tarjeta(
        titulo = "Asistente Híbrido",
        emoji = "🤖",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                AssistantAvatarView(
                    currentState = currentAvatarState
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isRunning) {
                                Paleta.Verde
                            } else {
                                Paleta.TextoSuave
                            },
                            shape = CircleShape
                        )
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = when {
                        !isRunning -> "Sesión detenida"
                        audioState.hayVoz -> "Escuchando voz..."
                        else -> "Esperando palabra clave"
                    },
                    color = if (isRunning) {
                        Paleta.Verde
                    } else {
                        Paleta.TextoSuave
                    },
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    triggerVoiceSessionToggle()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) {
                        Paleta.Rojo
                    } else {
                        Paleta.Primario
                    },
                    contentColor = if (isRunning) {
                        Color.White
                    } else {
                        Paleta.SobrePrimario
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isRunning) {
                        "Detener Asistente"
                    } else {
                        "Iniciar Sesión de Voz"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (permissionDenied) {
                Text(
                    text = "Se requieren permisos de micrófono y notificaciones para iniciar el asistente.",
                    color = Paleta.Rojo,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
