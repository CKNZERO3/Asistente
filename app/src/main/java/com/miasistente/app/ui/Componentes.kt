package com.miasistente.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tarjeta con título y emoji, base de cada sección de la pantalla. */
@Composable
fun Tarjeta(
    titulo: String,
    emoji: String,
    modifier: Modifier = Modifier,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Paleta.Tarjeta
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Paleta.Texto
                )
            }
            Spacer(Modifier.height(16.dp))
            contenido()
        }
    }
}

/** Etiqueta de color en forma de píldora (por ejemplo "OFFLINE"). */
@Composable
fun Insignia(texto: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = texto,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Una fila de permiso con interruptor y estado en verde/rojo.
 * Android no permite revocar un permiso desde la app: el interruptor solo sirve para
 * pedirlo. Si ya está concedido, se queda encendido.
 */
@Composable
fun FilaPermiso(
    emoji: String,
    nombre: String,
    descripcion: String,
    concedido: Boolean,
    onPedir: () -> Unit
) {
    val color = if (concedido) Paleta.Verde else Paleta.Rojo
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .background(Paleta.TarjetaAlta, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 20.sp) }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(nombre, color = Paleta.Texto, fontWeight = FontWeight.Medium)
            Text(
                descripcion,
                color = Paleta.TextoSuave,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(
                checked = concedido,
                onCheckedChange = { if (!concedido) onPedir() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Paleta.Verde,
                    checkedThumbColor = Color.Black,
                    uncheckedTrackColor = Paleta.TarjetaAlta,
                    uncheckedBorderColor = Paleta.Rojo,
                    uncheckedThumbColor = Paleta.Rojo
                )
            )
            Text(
                text = if (concedido) "Concedido" else "Denegado",
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Sección "Permisos": micrófono y superposición. */
@Composable
fun SeccionPermisos(
    microfonoOk: Boolean,
    superposicionOk: Boolean,
    onPedirMicrofono: () -> Unit,
    onPedirSuperposicion: () -> Unit
) {
    Tarjeta(titulo = "Permisos", emoji = "🔐") {
        FilaPermiso(
            emoji = "🎤",
            nombre = "Micrófono",
            descripcion = "Para escuchar la palabra clave",
            concedido = microfonoOk,
            onPedir = onPedirMicrofono
        )
        Spacer(Modifier.height(16.dp))
        FilaPermiso(
            emoji = "🪟",
            nombre = "Superposición",
            descripcion = "Para mostrar el avatar flotante",
            concedido = superposicionOk,
            onPedir = onPedirSuperposicion
        )
        if (!superposicionOk) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Se abrirá Ajustes: activa «Mostrar sobre otras apps» y vuelve.",
                color = Paleta.TextoSuave,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
