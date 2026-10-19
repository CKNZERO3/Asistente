package com.miasistente.app.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Se encarga del modelo de voz de Vosk descomprimiéndolo desde los assets de la app.
 */
object VoskModelManager {

    const val NOMBRE_MODELO = "vosk-model-small-es-0.42"
    private const val ARCHIVO_ASSET = "$NOMBRE_MODELO.zip"
    private const val MARCA_LISTO = ".listo"

    fun directorioModelo(context: Context): File =
        File(File(context.applicationContext.filesDir, "vosk"), NOMBRE_MODELO)

    fun estaInstalado(context: Context): Boolean =
        File(directorioModelo(context), MARCA_LISTO).exists()

    fun borrar(context: Context) {
        directorioModelo(context).deleteRecursively()
    }

    suspend fun instalar(context: Context, onProgreso: (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val destino = directorioModelo(appContext)

            if (estaInstalado(appContext)) {
                onProgreso(100)
                return@withContext
            }

            destino.deleteRecursively()
            destino.mkdirs()

            try {
                appContext.assets.open(ARCHIVO_ASSET).use { entrada ->
                    ZipInputStream(entrada).use { zip ->
                        var entradaZip = zip.nextEntry
                        var contador = 0

                        while (entradaZip != null) {
                            currentCoroutineContext().ensureActive()
                            val salida = File(destino, entradaZip.name)

                            if (entradaZip.isDirectory) {
                                salida.mkdirs()
                            } else {
                                salida.parentFile?.mkdirs()
                                salida.outputStream().use { fichero ->
                                    zip.copyTo(fichero)
                                }
                            }

                            contador++
                            if (contador % 5 == 0) {
                                onProgreso((contador % 90).coerceIn(10, 90))
                            }

                            zip.closeEntry()
                            entradaZip = zip.nextEntry
                        }
                    }
                }

                File(destino, MARCA_LISTO).writeText("ok")
                onProgreso(100)
            } catch (e: Exception) {
                destino.deleteRecursively()
                throw IOException("Error al extraer el modelo desde assets: ${e.message}", e)
            }
        }
}
