package com.miasistente.app.audio

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Se encarga del modelo de voz de Vosk (español, versión pequeña, ≈ 39 MB).
 * Descarga bajo demanda una sola vez y lo guarda en el almacenamiento privado de la app.
 */
object VoskModelManager {

    const val NOMBRE_MODELO = "vosk-model-small-es-0.42"

    // Puedes anteponer aquí una URL propia (ej. Cloudflare R2 o GitHub Release) como primer elemento.
    private val URLS_DESCARGA = listOf(
        "https://alphacephei.com/vosk/models/$NOMBRE_MODELO.zip"
    )

    private const val MARCA_LISTO = ".listo"
    private const val TAMANO_ESTIMADO_BYTES = 39_000_000L
    private const val TAMANO_MINIMO_ZIP_BYTES = 25_000_000L
    private const val ESPACIO_MINIMO_LIBRE_BYTES = 120_000_000L
    private const val PESO_DESCARGA = 85

    private val mutexInstalacion = Mutex()

    /** Carpeta donde queda el modelo ya instalado. */
    fun directorioModelo(context: Context): File =
        File(File(context.applicationContext.filesDir, "vosk"), NOMBRE_MODELO)

    /** true si el modelo se descargó, verificó y descomprimió completo. */
    fun estaInstalado(context: Context): Boolean {
        val dir = directorioModelo(context)
        return File(dir, MARCA_LISTO).exists() && esEstructuraValida(dir)
    }

    /** Borra el modelo (por ejemplo, si quedó dañado) para volver a descargarlo. */
    fun borrar(context: Context) {
        directorioModelo(context).deleteRecursively()
    }

    /**
     * Descarga e instala el modelo de forma atómica y segura frente a concurrencia.
     */
    suspend fun instalar(context: Context, onProgreso: (Int) -> Unit) =
        mutexInstalacion.withLock {
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                if (estaInstalado(appContext)) {
                    onProgreso(100)
                    return@withContext
                }

                val raiz = File(appContext.filesDir, "vosk").apply { mkdirs() }
                verificarEspacioLibre(raiz)

                val zipTemporal = File(appContext.cacheDir, "modelo-vosk.zip")
                val carpetaTemporal = File(raiz, ".instalando")

                try {
                    descargarConRespaldo(zipTemporal, onProgreso)

                    if (zipTemporal.length() < TAMANO_MINIMO_ZIP_BYTES) {
                        throw IOException("La descarga del modelo quedó incompleta.")
                    }

                    carpetaTemporal.deleteRecursively()
                    carpetaTemporal.mkdirs()
                    descomprimir(zipTemporal, carpetaTemporal, onProgreso)

                    val extraido = File(carpetaTemporal, NOMBRE_MODELO)
                    if (!esEstructuraValida(extraido)) {
                        throw IOException("El archivo del modelo está dañado o incompleto.")
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
        }

    private fun esEstructuraValida(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val am = File(dir, "am")
        val conf = File(dir, "conf")
        val graph = File(dir, "graph")
        return am.isDirectory && !am.list().isNullOrEmpty() &&
            conf.isDirectory && !conf.list().isNullOrEmpty() &&
            graph.isDirectory && !graph.list().isNullOrEmpty()
    }

    private fun verificarEspacioLibre(carpeta: File) {
        val disponibles = runCatching { StatFs(carpeta.absolutePath).availableBytes }
            .getOrDefault(ESPACIO_MINIMO_LIBRE_BYTES)
        if (disponibles < ESPACIO_MINIMO_LIBRE_BYTES) {
            throw IOException("Necesitas al menos 120 MB libres para instalar el modelo de voz.")
        }
    }

    private suspend fun descargarConRespaldo(destino: File, onProgreso: (Int) -> Unit) {
        var ultimoError: IOException? = null
        for (url in URLS_DESCARGA) {
            try {
                destino.delete()
                descargar(url, destino, onProgreso)
                return
            } catch (e: IOException) {
                ultimoError = e
            }
        }
        throw ultimoError ?: IOException("No se pudo descargar el modelo de voz.")
    }

    private suspend fun descargar(urlInicial: String, destino: File, onProgreso: (Int) -> Unit) {
        val conexion = abrirConexionConRedirecciones(urlInicial)
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

    private fun abrirConexionConRedirecciones(urlInicial: String): HttpURLConnection {
        var actual = URL(urlInicial)
        repeat(5) {
            val conn = (actual.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "IrisAndroid/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirección inválida al descargar el modelo.")
                }
                actual = URL(actual, location)
            } else {
                return conn
            }
        }
        throw IOException("Demasiadas redirecciones al descargar el modelo.")
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
