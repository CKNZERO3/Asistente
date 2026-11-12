package com.miasistente.app.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachBottomSheet(
    onDismissRequest: () -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
    onDrive: () -> Unit,
    onNotebooks: () -> Unit,
    onMap: () -> Unit,
    onQuickAction: (String) -> Unit,
    onPhotoSelected: (Uri) -> Unit
) {
    val p = LocalIrisPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.muted) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AttachBubble("Fotos", Icons.Outlined.Image, onPhotos)
                AttachBubble("Cámara", Icons.Outlined.PhotoCamera, onCamera)
                AttachBubble("Archivos", Icons.Outlined.AttachFile, onFiles)
                AttachBubble("Drive", Icons.Outlined.AddToDrive, onDrive)
                AttachBubble("Cuadernos", Icons.Outlined.Book, onNotebooks)
                AttachBubble("Mapa", Icons.Outlined.Map, onMap)
            }

            val cardShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(p.card)
                    .border(1.dp, p.outline, cardShape)
                    .padding(vertical = 8.dp)
            ) {
                AttachRowAction("Crea imágenes", "Visualiza y edita", Icons.Outlined.Image) {
                    onQuickAction("Crea una imagen de ")
                }
                AttachRowAction("Crear videos", "Haz que las ideas cobren vida", Icons.Outlined.Videocam) {
                    onQuickAction("Crea un video de ")
                }
                AttachRowAction("Crear música", "Crea pistas de audio", Icons.Outlined.MusicNote) {
                    onQuickAction("Crea una canción sobre ")
                }
                AttachRowAction("Canvas", "Programa, escribe o crea diapositivas.", Icons.Outlined.AddBox) {
                    onQuickAction("Abre un canvas para ")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Recientes en galería",
                color = p.text,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val context = LocalContext.current
            var recentPhotos by remember { mutableStateOf<List<Uri>>(emptyList()) }

            LaunchedEffect(Unit) {
                recentPhotos = getRecentPhotos(context)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                items(recentPhotos) { uri ->
                    val bitmapResult = rememberUriBitmap(uri.toString(), 300)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.card)
                            .clickable { onPhotoSelected(uri) }
                    ) {
                        bitmapResult.bitmap?.let { btm ->
                            Image(
                                bitmap = btm,
                                contentDescription = "Foto reciente",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun AttachBubble(label: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalIrisPalette.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(p.card)
                .border(1.dp, p.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = p.text, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = p.text, fontSize = 12.sp)
    }
}

@Composable
private fun AttachRowAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalIrisPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = p.text, fontSize = 16.sp)
            Text(subtitle, color = p.muted, fontSize = 14.sp)
        }
    }
}

suspend fun getRecentPhotos(context: Context): List<Uri> = withContext(Dispatchers.IO) {
    val uris = mutableListOf<Uri>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && uris.size < 30) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                uris.add(contentUri)
            }
        }
    } catch (_: SecurityException) {
        // Evita cierres si el usuario aún no ha otorgado permisos de lectura multimedia
    }
    uris
}
