package com.miasistente.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.Locale

internal data class BitmapResult(val bitmap: ImageBitmap?, val done: Boolean)

/** Carga una imagen (content://, file:// o https://) fuera del hilo principal. */
@Composable
internal fun rememberUriBitmap(uri: String?, maxPx: Int): BitmapResult {
    val context = LocalContext.current
    val state = produceState(
        initialValue = BitmapResult(null, uri == null),
        uri,
        maxPx
    ) {
        value = if (uri == null) {
            BitmapResult(null, true)
        } else {
            BitmapResult(withContext(Dispatchers.IO) { decodeBitmap(context, uri, maxPx) }, true)
        }
    }
    return state.value
}

private fun decodeBitmap(context: Context, uriString: String, maxPx: Int): ImageBitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val remote = uri.scheme == "http" || uri.scheme == "https"

        fun open(): InputStream? =
            if (remote) URL(uriString).openStream()
            else context.contentResolver.openInputStream(uri)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = if (remote) 0 else leerRotacion(context, uri)
        val finalBitmap = if (rotation == 0) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true
            )
        }
        finalBitmap.asImageBitmap()
    } catch (e: Throwable) {
        null
    }
}

private fun leerRotacion(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }
}

/** Previsualización dentro de la burbuja: miniatura para imágenes, tarjeta para otros archivos. */
@Composable
internal fun AttachmentPreview(attachment: ChatAttachment, onAccent: Boolean) {
    if (attachment.isImage) {
        val result = rememberUriBitmap(attachment.uri, 720)
        val shape = RoundedCornerShape(16.dp)
        val bitmap = result.bitmap
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 220.dp, height = 180.dp).clip(shape)
            )
            !result.done -> Box(
                modifier = Modifier
                    .size(width = 220.dp, height = 180.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> FileCard(attachment, onAccent)
        }
    } else {
        FileCard(attachment, onAccent)
    }
}

@Composable
private fun FileCard(attachment: ChatAttachment, onAccent: Boolean) {
    val palette = LocalIrisPalette.current
    val textColor = if (onAccent) Color.White else palette.text
    val subColor = if (onAccent) Color.White.copy(alpha = 0.75f) else palette.muted
    val tipo = attachment.name.substringAfterLast('.', "").uppercase().ifBlank { "ARCHIVO" }
    val detalle = listOfNotNull(tipo, attachment.sizeBytes?.let(::formatearTamano)).joinToString(" · ")

    Row(
        modifier = Modifier.widthIn(max = 240.dp).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (onAccent) Color.White.copy(alpha = 0.2f) else palette.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (onAccent) Color.White else palette.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                attachment.name,
                color = textColor,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(detalle, color = subColor, fontSize = 11.sp)
        }
    }
}

private fun formatearTamano(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
