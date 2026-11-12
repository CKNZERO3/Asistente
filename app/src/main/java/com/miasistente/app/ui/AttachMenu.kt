package com.miasistente.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class IrisSavedMedia(
    val id: String,
    val name: String,
    val uri: String,
    val type: String
)

fun loadIrisMedia(context: Context): List<IrisSavedMedia> {
    val prefs = context.getSharedPreferences("iris_user_media", Context.MODE_PRIVATE)
    val raw = prefs.getString("items", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            IrisSavedMedia(
                id = obj.getString("id"),
                name = obj.optString("name", "Archivo"),
                uri = obj.getString("uri"),
                type = obj.optString("type", "photo")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveUriToIrisMedia(context: Context, uri: Uri, type: String) {
    try {
        val dir = File(context.filesDir, "iris_media").apply { mkdirs() }
        val displayName = resolveDisplayName(context, uri)
        val safeFile = File(dir, "${System.currentTimeMillis()}_$displayName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            safeFile.outputStream().use { output -> input.copyTo(output) }
        }
        val storedUri = if (safeFile.exists() && safeFile.length() > 0) {
            Uri.fromFile(safeFile).toString()
        } else {
            uri.toString()
        }

        val current = loadIrisMedia(context).toMutableList()
        current.add(
            0,
            IrisSavedMedia(
                id = "media_${System.currentTimeMillis()}",
                name = displayName,
                uri = storedUri,
                type = type
            )
        )
        val arr = JSONArray()
        current.take(60).forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("uri", item.uri)
                    put("type", item.type)
                }
            )
        }
        context.getSharedPreferences("iris_user_media", Context.MODE_PRIVATE)
            .edit()
            .putString("items", arr.toString())
            .apply()
    } catch (_: Exception) {
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = cursor.getString(idx)
                }
            }
        } catch (_: Exception) {
        }
    }
    return result ?: uri.lastPathSegment?.substringAfterLast('/') ?: "archivo_${System.currentTimeMillis()}"
}

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
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val savedPhotos = remember { loadIrisMedia(context).filter { it.type == "photo" } }

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
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AttachBubble("Fotos", Icons.Outlined.Image) {
                    launchSafeIntent(
                        context,
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    )
                    onPhotos()
                }
                AttachBubble("Cámara", Icons.Outlined.PhotoCamera) {
                    launchSafeIntent(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    onCamera()
                }
                AttachBubble("Archivos", Icons.Outlined.AttachFile, onFiles)
                AttachBubble("Drive", Icons.Outlined.AddToDrive, onDrive)
                AttachBubble("Cuadernos", Icons.Outlined.Book, onNotebooks)
                AttachBubble("Mapa", Icons.Outlined.Map) {
                    val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (!launchSafeIntent(context, mapsIntent)) {
                        launchSafeIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")))
                    }
                    onMap()
                }
            }

            val cardShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(p.card)
                    .border(1.dp, p.outline, cardShape)
                    .padding(vertical = 4.dp)
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

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Subidos en Iris",
                color = p.text,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (savedPhotos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(p.card)
                        .border(1.dp, p.outline, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay nada aquí",
                        color = p.muted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    items(savedPhotos, key = { it.id }) { item ->
                        val uri = Uri.parse(item.uri)
                        val bitmapResult = rememberUriBitmap(item.uri, 240)
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
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

private fun launchSafeIntent(context: Context, intent: Intent): Boolean {
    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
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
                .size(56.dp)
                .clip(CircleShape)
                .background(p.card)
                .border(1.dp, p.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = p.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
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
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = p.text, fontSize = 15.sp)
            Text(subtitle, color = p.muted, fontSize = 13.sp)
        }
    }
}
