package com.miasistente.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PermissionKind { MICROPHONE }

/** goToSettings = true cuando el sistema ya no deja volver a preguntar. */
data class PermissionPrompt(
    val kind: PermissionKind,
    val goToSettings: Boolean = false
)

/**
 * Tarjeta pequeña y discreta que explica para qué se necesita un permiso.
 * No bloquea la pantalla: el permiso del sistema solo se pide si el usuario pulsa el botón principal.
 */
@Composable
fun PermissionCard(
    prompt: PermissionPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(22.dp)

    val (title, body) = when (prompt.kind) {
        PermissionKind.MICROPHONE ->
            if (prompt.goToSettings) {
                "Micrófono desactivado" to
                    "Para usar la voz, actívalo en los ajustes de la app. Solo te tomará un momento."
            } else {
                "Activar el micrófono" to
                    "Iris lo usa únicamente para escuchar tus comandos mientras la escucha esté activa. " +
                    "Puedes apagarlo cuando quieras."
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.outline, shape)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(p.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = p.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 15.sp)
                Spacer(Modifier.size(2.dp))
                Text(body, color = p.muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Ahora no", color = p.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(IrisAccentGradient)
                    .clickable(role = Role.Button, onClick = onConfirm)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    if (prompt.goToSettings) "Abrir ajustes" else "Permitir",
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}
