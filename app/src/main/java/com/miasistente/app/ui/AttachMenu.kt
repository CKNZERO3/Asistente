package com.miasistente.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AttachMenu(
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    onQuickAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(24.dp)

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AttachBubble("Fotos", Icons.Default.PhotoLibrary, onPhotos)
            AttachBubble("Cámara", Icons.Default.PhotoCamera, onCamera)
            AttachBubble("Archivos", Icons.Default.Folder, onFiles)
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .width(236.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.outline, shape)
                .padding(vertical = 6.dp)
        ) {
            AttachRow("Crea imágenes", Icons.Default.Image) { onQuickAction("Crea una imagen de ") }
            AttachRow("Video", Icons.Default.Videocam) { onQuickAction("Crea un video de ") }
            AttachRow("Crear música", Icons.Default.MusicNote) { onQuickAction("Crea una canción sobre ") }
            AttachRow("Canvas", Icons.Default.Brush) { onQuickAction("Abre un canvas para ") }
        }
    }
}

@Composable
private fun AttachBubble(label: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalIrisPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(p.card)
                .border(1.dp, p.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = p.accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = p.text, fontSize = 12.sp)
    }
}

@Composable
private fun AttachRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalIrisPalette.current
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .width(200.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = p.text, fontSize = 14.sp)
    }
}
