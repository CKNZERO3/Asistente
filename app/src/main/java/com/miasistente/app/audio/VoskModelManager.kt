package com.miasistente.app.audio

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object VoskModelManager {

    const val NOMBRE_MODELO = "vosk-model-small-es-0.42"
    private const val MARCA_LISTO = ".listo"

    fun directorioModelo(context: Context): File {
        val descargas = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val raizAsistente = File(descargas, "AsistenteVosk")
        return File(raizAsistente, NOMBRE_MODELO)
    }

    fun estaInstalado(context: Context): Boolean =
        File(directorioModelo(context), MARCA_LISTO).exists()

    fun borrar(context: Context) {
        directorioModelo(context).deleteRecursively()
    }

    suspend fun instalar(context: Context, onProgreso: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            onProgreso(100)
            if (!estaInstalado(context)) {
                throw IOException("Coloca la carpeta en Download/AsistenteVosk/")
            }
        }
}
