package com.miasistente.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miasistente.app.data.Ajustes

/** Avatares predeterminados (emojis sobre fondo negro, sin archivos de imagen). */
val PresetsAvatar = listOf("🤖", "🦊", "🐱", "👾")

fun idPreset(indice: Int) = "preset:$indice"

/**
 * Dibuja un avatar sobre fondo negro. Es el mismo dibujo que usará la ventana flotante.
 * @param id "preset:N" o "custom"
 */
@Composable
fun AvatarVista(
    id: String,
    imagenPersonalizada: ImageBitmap?,
    tamanoEmoji: TextUnit,
    modifier: Modifier = Modifier
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (id == Ajustes.AVATAR_PERSONALIZADO && imagenPersonalizada != null) {
            Image(
                bitmap = imagenPersonalizada,
                contentDescription = "Avatar personalizado",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val indice = id.removePrefix("preset:").toIntOrNull() ?: 0
            Text(PresetsAvatar.getOrElse(indice) { PresetsAvatar[0] }, fontSize = tamanoEmoji)
        }
    }
}

@Composable
fun SeccionAvatar(
    seleccionado: String,
    imagenPersonalizada: ImageBitmap?,
    mensaje: String?,
    onSeleccionar: (String) -> Unit,
    onElegirGaleria: () -> Unit
) {
    Tarjeta(titulo = "Avatar", emoji = "🎭") {

        // Vista previa grande del avatar elegido
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AvatarVista(
                id = seleccionado,
                imagenPersonalizada = imagenPersonalizada,
                tamanoEmoji = 56.sp,
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(2.dp, Paleta.Primario, CircleShape)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Cuadrícula: 4 predeterminados + 1 personalizado
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PresetsAvatar.indices.forEach { i ->
                val id = idPreset(i)
                TileAvatar(
                    id = id,
                    imagenPersonalizada = null,
                    elegido = seleccionado == id,
                    onClick = { onSeleccionar(id) }
                )
            }
            TileAvatar(
                id = Ajustes.AVATAR_PERSONALIZADO,
                imagenPersonalizada = imagenPersonalizada,
                elegido = seleccionado == Ajustes.AVATAR_PERSONALIZADO,
                onClick = {
                    if (imagenPersonalizada != null) onSeleccionar(Ajustes.AVATAR_PERSONALIZADO)
                    else onElegirGaleria()
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        FilledTonalButton(onClick = onElegirGaleria, modifier = Modifier.fillMaxWidth()) {
            Text("📁  Elegir imagen de la galería")
        }

        mensaje?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Paleta.Rojo, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        // Nota de guía
        Row(
            Modifier
                .fillMaxWidth()
                .background(Paleta.Ambar.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text("💡", fontSize = 18.sp)
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    "Recomendación",
                    color = Paleta.Ambar,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Usa avatares con fondo negro y calidad FHD o SD. Las imágenes con " +
                        "transparencias pueden verse con errores en la ventana flotante.",
                    color = Paleta.Texto,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RowScope.TileAvatar(
    id: String,
    imagenPersonalizada: ImageBitmap?,
    elegido: Boolean,
    onClick: () -> Unit
) {
    val forma = RoundedCornerShape(16.dp)
    val esPersonalizadoVacio = id == Ajustes.AVATAR_PERSONALIZADO && imagenPersonalizada == null

    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(forma)
            .border(
                width = if (elegido) 3.dp else 1.dp,
                color = if (elegido) Paleta.Primario else Paleta.Contorno,
                shape = forma
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (esPersonalizadoVacio) {
            Box(Modifier.fillMaxSize().background(Paleta.TarjetaAlta), contentAlignment = Alignment.Center) {
                Text("＋", fontSize = 26.sp, color = Paleta.TextoSuave)
            }
        } else {
            AvatarVista(
                id = id,
                imagenPersonalizada = imagenPersonalizada,
                tamanoEmoji = 26.sp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
