package com.miasistente.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda la imagen de avatar que el usuario elige de la galería.
 *
 * Por qué se copia: el permiso que da la galería sobre la imagen es temporal. Si solo
 * guardáramos su dirección (Uri), dejaría de funcionar. Se copia a la carpeta privada de
 * la app, reducida a un máximo de 768 px para no gastar memoria. Como se copia, NO hace
 * falta takePersistableUriPermission.
 *
 * La futura ventana flotante leerá el mismo archivo con [cargar].
 */
object AvatarRepositorio {

    private const val NOMBRE_ARCHIVO = "avatar_personalizado.png"
    private const val LADO_MAXIMO = 768

    fun archivo(context: Context): File =
        File(context.applicationContext.filesDir, NOMBRE_ARCHIVO)

    /** Copia la imagen elegida. @return true si se guardó bien. */
    suspend fun guardarDesdeUri(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.applicationContext.contentResolver

                // 1) Medir sin cargar la imagen completa.
                //    OJO: con inJustDecodeBounds, decodeStream devuelve null A PROPÓSITO
                //    (solo rellena las medidas). Por eso aquí NO se comprueba su resultado.
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val abrio = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, medidas)
                    true
                } ?: false
                if (!abrio || medidas.outWidth <= 0 || medidas.outHeight <= 0) {
                    return@withContext false
                }

                // 2) Cargar ya reducida (potencias de 2) para no agotar la memoria con fotos enormes
                var muestreo = 1
                while (medidas.outWidth / muestreo > LADO_MAXIMO * 2 ||
                    medidas.outHeight / muestreo > LADO_MAXIMO * 2
                ) {
                    muestreo *= 2
                }
                val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
                val original = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opciones)
                } ?: return@withContext false

                // 3) Corregir la rotación de las fotos hechas con la cámara
                val grados = resolver.openInputStream(uri)?.use { leerRotacion(it) } ?: 0
                val girada = if (grados != 0) {
                    val m = Matrix().apply { postRotate(grados.toFloat()) }
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true)
                } else {
                    original
                }

                // 4) Ajuste fino al tamaño máximo
                val escala = minOf(1f, LADO_MAXIMO.toFloat() / maxOf(girada.width, girada.height))
                val final = if (escala < 1f) {
                    Bitmap.createScaledBitmap(
                        girada,
                        (girada.width * escala).toInt().coerceAtLeast(1),
                        (girada.height * escala).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    girada
                }

                archivo(context).outputStream().use { final.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            } catch (e: Exception) {
                false
            } catch (e: OutOfMemoryError) {
                false
            }
        }

    private fun leerRotacion(flujo: java.io.InputStream): Int = try {
        when (ExifInterface(flujo).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    /** Lee el avatar guardado, o null si no hay ninguno. */
    suspend fun cargar(context: Context): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val f = archivo(context)
            if (!f.exists()) {
                null
            } else {
                try {
                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
}
