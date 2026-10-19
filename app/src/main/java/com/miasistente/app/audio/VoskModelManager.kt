package com.miasistente.app.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Se encarga del modelo de voz de Vosk (español, versión pequeña, ≈ 39 MB).
 */
object VoskModelManager {

    const val NOMBRE_MODELO = "vosk-model-small-es-0.42"

    private const val URL_MODELO = "[https://alphacephei.com/vosk/models/$NOMBRE_MODELO.zip](https://alphacephei.com/vosk/models/$NOMBRE_MODELO.zip)"
    private const val MARCA_LISTO = ".listo"

    private const val TAMANO_ESTIMADO_BYTES = 39_000_000L
    private const val PESO_DESCARGA = 85

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
            val raiz = File(appContext.filesDir, "vosk").apply { mkdirs() }
            val zipTemporal = File(appContext.cacheDir, "modelo-vosk.zip")
            val carpetaTemporal = File(raiz, ".instalando")

            try {
                descargar(zipTemporal, onProgreso)

                carpetaTemporal.deleteRecursively()
                carpetaTemporal.mkdirs()
                descomprimir(zipTemporal, carpetaTemporal, onProgreso)

                val extraido = File(carpetaTemporal, NOMBRE_MODELO)
                if (!extraido.isDirectory || extraido.list().isNullOrEmpty()) {
                    throw IOException("El archivo del modelo está incompleto.")
                }
                File(extraido, MARCA_LISTO).writeText("ok")

                val destino = directorioModelo(appContext)
                destino.deleteRecursively()
                if (!extraido.renameTo(destino)) {
                    throw IOException("No se pudo guardar el modelo en el teléfono.")
                }
                onProgreso(100)
            } finally {
                zipTemporal.delete()
                carpetaTemporal.deleteRecursively()
            }
        }

    private suspend fun descargar(destino: File, onProgreso: (Int) -> Unit) {
        val conexion = (URL(URL_MODELO).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("El servidor respondió con el código ${conexion.responseCode}.")
            }
            val total = conexion.contentLengthLong.takeIf { it > 0 } ?: TAMANO_ESTIMADO_BYTES

            conexion.inputStream.use { entrada ->
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(64 * 1024)
                    var leidos = 0L
                    var ultimoPorcentaje = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = entrada.read(buffer)
                        if (n < 0) break
                        salida.write(buffer, 0, n)
                        leidos += n

                        val porcentaje = ((leidos * PESO_DESCARGA) / total)
                            .toInt().coerceIn(0, PESO_DESCARGA)
                        if (porcentaje != ultimoPorcentaje) {
                            ultimoPorcentaje = porcentaje
                            onProgreso(porcentaje)
                        }
                    }
                }
            }
        } finally {
            conexion.disconnect()
        }
    }

    private suspend fun descomprimir(zip: File, destino: File, onProgreso: (Int) -> Unit) {
        val destinoCanonico = destino.canonicalPath + File.separator

        ZipFile(zip).use { archivo ->
            val total = archivo.size().coerceAtLeast(1)
            var hechos = 0
            val entradas = archivo.entries()

            while (entradas.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entrada = entradas.nextElement()
                val salida = File(destino, entrada.name)

                if (!salida.canonicalPath.startsWith(destinoCanonico)) {
                    throw IOException("El archivo del modelo no es válido.")
                }

                if (entrada.isDirectory) {
                    salida.mkdirs()
                } else {
                    salida.parentFile?.mkdirs()
                    archivo.getInputStream(entrada).use { origen ->
                        salida.outputStream().use { fichero -> origen.copyTo(fichero) }
                    }
                }

                hechos++
                onProgreso(PESO_DESCARGA + (hechos * (100 - PESO_DESCARGA)) / total)
            }
        }
    }
}
