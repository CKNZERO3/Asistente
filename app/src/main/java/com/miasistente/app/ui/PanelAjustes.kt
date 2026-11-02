package com.miasistente.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.data.PerfilUsuario

/**
 * Contenido del menú lateral: toda la configuración que antes ocupaba la pantalla principal.
 *
 *   Perfil · Permisos · Palabra clave · Avatar · Capacidades
 */
@Composable
fun PanelAjustes(
    nombre: String,
    onNombre: (String) -> Unit,
    microfonoOk: Boolean,
    superposicionOk: Boolean,
    onPedirMicrofono: () -> Unit,
    onPedirSuperposicion: () -> Unit,
    palabra: String,
    onPalabra: (String) -> Unit,
    palabraEditable: Boolean,
    avatarSeleccionado: String,
    imagenPersonalizada: ImageBitmap?,
    mensajeAvatar: String?,
    onSeleccionarAvatar: (String) -> Unit,
    onElegirGaleria: () -> Unit,
    tieneClave: Boolean,
    verificandoClave: Boolean,
    resultadoClave: ResultadoComando?,
    onGuardarClave: (String) -> Unit,
    onBorrarClave: () -> Unit,
    onVerificarClave: () -> Unit,
    onCerrar: () -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Encabezado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ajustes",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCerrar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar ajustes",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Perfil
        Tarjeta(titulo = "Tu nombre", emoji = "👤") {
            OutlinedTextField(
                value = nombre,
                onValueChange = { onNombre(it.take(PerfilUsuario.LARGO_MAXIMO)) },
                label = { Text("Cómo quieres que te llame") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Permisos (micrófono y superposición)
        SeccionPermisos(
            microfonoOk = microfonoOk,
            superposicionOk = superposicionOk,
            onPedirMicrofono = onPedirMicrofono,
            onPedirSuperposicion = onPedirSuperposicion
        )

        // Palabra clave
        Tarjeta(titulo = "Palabra clave", emoji = "🗝️") {
            OutlinedTextField(
                value = palabra,
                onValueChange = { onPalabra(it.take(30)) },
                enabled = palabraEditable,
                label = { Text("Palabra o frase de activación") },
                placeholder = { Text("Oye Asistente") },
                singleLine = true,
                supportingText = {
                    Text(
                        if (palabraEditable) {
                            "Palabras comunes del español, de 3 o más sílabas. Los nombres raros pueden no reconocerse."
                        } else {
                            "Apaga el asistente para cambiarla"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Avatar
        SeccionAvatar(
            seleccionado = avatarSeleccionado,
            imagenPersonalizada = imagenPersonalizada,
            mensaje = mensajeAvatar,
            onSeleccionar = onSeleccionarAvatar,
            onElegirGaleria = onElegirGaleria
        )

        // Capacidades + clave de Gemini (colapsable, al fondo)
        SeccionCapacidades(
            tieneClave = tieneClave,
            verificando = verificandoClave,
            resultadoVerificacion = resultadoClave,
            onGuardarClave = onGuardarClave,
            onBorrarClave = onBorrarClave,
            onVerificarClave = onVerificarClave
        )

        Spacer(Modifier.height(8.dp))
    }
}
