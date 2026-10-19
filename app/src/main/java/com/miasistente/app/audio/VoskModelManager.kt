herepackage com.miasistente.app.audio

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
 *
 * Por qué se descarga en vez de venir dentro de la app:
 *  - Subir un archivo de 39 MB a GitHub desde el navegador del celular es muy incómodo.
 *  - Así el APK pesa menos y el modelo se puede cambiar sin recompilar.
 *
 * La descarga ocurre UNA sola vez (conviene tener Wi-Fi). Después el modelo vive en el
 * almacenamiento privado de la app y todo funciona sin internet.
 */
object VoskModelManager {

    const val NOMBRE_MODELO = "vosk-model-small-es-0.42"

    private const val URL_MODELO = "https://alphacephei.com/vosk/models/$NOMBRE_MODELO.zip"
    private const val MARCA_LISTO = ".listo"

    // Tamaño aproximado, solo se usa si el servidor no informa el tamaño real
    private const val TAMANO_ESTIMADO_BYTES = 39_000_000L

    // El progreso total (0..100) se reparte: descargar = 0..85, descomprimir = 85..100
    private const val PESO_DESCARGA = 85

    /** Carpeta donde queda el modelo ya instalado. */
    fun directorioModelo(context: Context): File =
        File(File(context.applicationContext.filesDir, "vosk"), NOMBRE_MODELO)

    /** true si el modelo se descargó y descomprimió completo. */
    fun estaInstalado(context: Context): Boolean =
        File(directorioModelo(context), MARCA_LISTO).exists()

    /** Borra el modelo (por ejemplo, si quedó dañado) para volver a descargarlo. */
    fun borrar(context: Context) {
        directorioModelo(context).deleteRecursively()
    }

    /**
     * Descarga e instala el modelo. Se puede cancelar cancelando la corrutina.
     *
     * @param onProgreso se llama con un porcentaje 0..100 cada vez que cambia
     * @throws IOException si falla la red, no hay espacio o el archivo no es válido
     */
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

                // El zip trae una carpeta con el nombre del modelo
                val extraido = File(carpetaTemporal, NOMBRE_MODELO)
                if (!extraido.isDirectory || extraido.list().isNullOrEmpty()) {
                    throw IOException("El archivo del modelo está incompleto.")
                }
                File(extraido, MARCA_LISTO).writeText("ok")

                // Mover a su lugar definitivo solo cuando todo salió bien
                val destino = directorioModelo(appContext)
                destino.deleteRecursively()
                if (!extraido.renameTo(destino)) {
                    throw IOException("No se pudo guardar el modelo en el teléfono.")
                }
                onProgreso(100)
            } finally {
                // Pase lo que pase (éxito, error o cancelación) no quedan restos
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
                        currentCoroutineContext().ensureActive() // permite cancelar
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
        // Para comprobar que ningún archivo del zip intente salirse de la carpeta de destino
        val destinoCanonico = destino.canonicalPath + File.separator

        ZipFile(zip).use { archivo ->
            val total = archivo.size().coerceAtLeast(1)
            var hechos = 0
            val entradas = archivo.entries()

            while (entradas.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entrada = entradas.nextElement()
                val salida = File(destino, entrada.name)

                // Protección "zip-slip": rutas como ../../algo se rechazan
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
