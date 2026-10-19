package com.miasistente.app.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.SequenceInputStream
import java.util.zip.ZipInputStream

object VoskModelManager {

    const val NOMBRE_MODELO = "vosk-model-small-es-0.42"
    private const val MARCA_LISTO = ".listo"

    private const val ASSET_PARTE_1 = "vosk_part1.bin"
    private const val ASSET_PARTE_2 = "vosk_part2.bin"

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
                val part1 = appContext.assets.open(ASSET_PARTE_1)
                val part2 = appContext.assets.open(ASSET_PARTE_2)
                val combinedStream = SequenceInputStream(part1, part2)

                ZipInputStream(combinedStream).use { zip ->
                    var entradaZip = zip.nextEntry
                    var contador = 0

                    while (entradaZip != null) {
                        currentCoroutineContext().ensureActive()
                        val salida = File(destino, entradaZip.name)

                        if (entradaZip.isDirectory) {
                            salida.mkdirs()
                        } else {
                            salida.parentFile?.mkdirs()
                            FileOutputStream(salida).use { fichero ->
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

                File(destino, MARCA_LISTO).writeText("ok")
                onProgreso(100)
            } catch (e: Exception) {
                destino.deleteRecursively()
                throw IOException("Error al descomprimir el modelo desde assets: ${e.message}", e)
            }
        }
}
