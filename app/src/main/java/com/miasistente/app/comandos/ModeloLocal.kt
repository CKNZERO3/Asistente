package com.miasistente.app.comandos

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.miasistente.app.data.InteligenciaConfig
import com.miasistente.app.data.PerfilModeloLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class EstadoDescargaModelo(
    val descargando: Boolean = false,
    val progreso: Int = 0,
    val perfilNombre: String = "",
    val completado: Boolean = false,
    val error: String? = null
)

/**
 * IA que corre en el propio teléfono (LiteRT-LM, procesador). Se encarga de las tareas ligeras
 * para no gastar la cuota de Gemini y recibe contexto compacto de [MemoriaLocal] e instrucciones
 * personalizadas del usuario para conocerlo sin conexión.
 */
class ModeloLocal(context: Context) {

    private val appContext = context.applicationContext
    private val carpeta = File(appContext.filesDir, "modelo_local")
    private val archivo = File(carpeta, NOMBRE_ARCHIVO)

    fun disponible(): Boolean = archivo.isFile && archivo.length() >= TAMANO_MINIMO

    fun tamanoBytes(): Long = if (archivo.isFile) archivo.length() else 0L

    fun tamanoLegible(): String {
        val bytes = tamanoBytes()
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB", mb)
        }
    }

    fun eliminar() {
        cerrarMotor()
        archivo.delete()
        File(carpeta, "$NOMBRE_ARCHIVO.part").delete()
    }

    /** Descarga un archivo .litertlm desde una URL directa con progreso y reanudación HTTP Range. */
    fun iniciarDescarga(urlRaw: String, perfil: PerfilModeloLocal) {
        val url = urlRaw.trim()
        if (url.isEmpty()) return

        trabajoDescarga?.cancel()
        trabajoDescarga = ambito.launch(Dispatchers.IO) {
            _estadoDescarga.value = EstadoDescargaModelo(
                descargando = true,
                progreso = 0,
                perfilNombre = perfil.titulo,
                completado = false,
                error = null
            )
            carpeta.mkdirs()
            val parcial = File(carpeta, "$NOMBRE_ARCHIVO.part")
            var conexion: HttpURLConnection? = null

            try {
                val yaDescargado = if (parcial.exists()) parcial.length() else 0L
                conexion = abrirConexionConRedirecciones(url, yaDescargado)

                val codigo = conexion.responseCode
                if (codigo !in 200..299 && codigo != HttpURLConnection.HTTP_PARTIAL) {
                    error("El servidor respondió HTTP $codigo.")
                }

                val reanudando = codigo == HttpURLConnection.HTTP_PARTIAL && yaDescargado > 0L
                if (!reanudando && parcial.exists()) {
                    parcial.delete()
                }

                val baseBytes = if (reanudando) yaDescargado else 0L
                val restante = conexion.contentLengthLong
                val totalBytes = if (restante > 0L) baseBytes + restante else -1L

                if (totalBytes > 0L && carpeta.usableSpace < totalBytes - baseBytes + MARGEN_ESPACIO) {
                    error("No hay espacio suficiente en el teléfono para descargar el modelo.")
                }

                conexion.inputStream.use { entrada ->
                    FileOutputStream(parcial, reanudando).use { salida ->
                        val buffer = ByteArray(64 * 1024)
                        var acumulados = baseBytes
                        var ultimoPct = -1

                        while (isActive) {
                            val leidos = entrada.read(buffer)
                            if (leidos < 0) break
                            salida.write(buffer, 0, leidos)
                            acumulados += leidos

                            val pct = if (totalBytes > 0L) {
                                ((acumulados * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            } else {
                                0
                            }
                            if (pct != ultimoPct) {
                                ultimoPct = pct
                                _estadoDescarga.value = _estadoDescarga.value.copy(progreso = pct)
                            }
                        }
                    }
                }

                if (!isActive) return@launch

                if (parcial.length() < TAMANO_MINIMO) {
                    parcial.delete()
                    error("El archivo descargado es demasiado pequeño para ser un modelo .litertlm válido.")
                }

                cerrarMotor()
                archivo.delete()
                if (!parcial.renameTo(archivo)) {
                    error("No pude guardar el modelo descargado en el dispositivo.")
                }

                val nombreArchivo = Uri.parse(url).lastPathSegment
                    ?.substringAfterLast('/')
                    ?.takeIf { it.endsWith(".litertlm", ignoreCase = true) }
                    ?: "${perfil.id}.litertlm"

                InteligenciaConfig(appContext).nombreModeloLocalInstalado = nombreArchivo

                _estadoDescarga.value = EstadoDescargaModelo(
                    descargando = false,
                    progreso = 100,
                    perfilNombre = perfil.titulo,
                    completado = true,
                    error = null
                )
            } catch (_: CancellationException) {
                _estadoDescarga.value = EstadoDescargaModelo(descargando = false)
            } catch (e: Exception) {
                _estadoDescarga.value = EstadoDescargaModelo(
                    descargando = false,
                    progreso = 0,
                    perfilNombre = perfil.titulo,
                    completado = false,
                    error = e.message ?: "No se pudo completar la descarga."
                )
            } finally {
                conexion?.disconnect()
            }
        }
    }

    fun cancelarDescarga() {
        trabajoDescarga?.cancel()
        trabajoDescarga = null
        _estadoDescarga.value = EstadoDescargaModelo(descargando = false)
    }

    private fun abrirConexionConRedirecciones(urlInicial: String, byteOffset: Long): HttpURLConnection {
        var actual = URL(urlInicial)
        repeat(5) {
            val conn = (actual.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "IrisAndroid/1.0")
                if (byteOffset > 0L) {
                    setRequestProperty("Range", "bytes=$byteOffset-")
                }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    error("Redirección inválida al descargar el modelo.")
                }
                actual = URL(actual, location)
            } else {
                return conn
            }
        }
        error("Demasiadas redirecciones al descargar el modelo.")
    }

    /** Copia el modelo elegido por el usuario a almacenamiento privado de la app. */
    suspend fun importar(uri: Uri, tamanoDeclarado: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (tamanoDeclarado != null && tamanoDeclarado < TAMANO_MINIMO) {
                error("Ese archivo es demasiado pequeño para ser un modelo.")
            }
            carpeta.mkdirs()
            if (tamanoDeclarado != null && carpeta.usableSpace < tamanoDeclarado + MARGEN_ESPACIO) {
                error("No hay espacio suficiente en el teléfono para el modelo.")
            }
            val temporal = File(carpeta, "$NOMBRE_ARCHIVO.tmp")
            try {
                val entrada = appContext.contentResolver.openInputStream(uri)
                    ?: error("No pude abrir el archivo elegido.")
                entrada.use { origen ->
                    temporal.outputStream().use { destino -> origen.copyTo(destino, 1 shl 20) }
                }
                if (temporal.length() < TAMANO_MINIMO) {
                    error("Ese archivo es demasiado pequeño para ser un modelo.")
                }
                cerrarMotor()
                archivo.delete()
                if (!temporal.renameTo(archivo)) error("No pude guardar el modelo.")
            } finally {
                if (temporal.exists()) temporal.delete()
            }
        }
    }

    /** Genera una respuesta con el modelo local. Falla (sin lanzar) si no está instalado. */
    suspend fun generar(instruccionSistema: String, pregunta: String): Result<String> =
        withContext(Dispatchers.Default) {
            if (!disponible()) {
                return@withContext Result.failure(IllegalStateException("El modelo local no está instalado."))
            }
            mutex.withLock {
                val resultado = runCatching {
                    val motor = motorCargado ?: crearMotor().also { motorCargado = it }
                    val configuracion = ConversationConfig(
                        systemInstruction = Contents.of(instruccionSistema),
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.4)
                    )
                    motor.createConversation(configuracion).use { conversacion ->
                        conversacion
                            .sendMessage(Message.user(pregunta), maxOutputToken = MAX_TOKENS_SALIDA)
                            .toString()
                            .trim()
                    }.also { if (it.isEmpty()) error("El modelo local no devolvió texto.") }
                }
                programarCierre()
                resultado
            }
        }

    private fun crearMotor(): Engine {
        val configuracion = EngineConfig(
            modelPath = archivo.absolutePath,
            backend = Backend.CPU(threadCount = HILOS),
            cacheDir = appContext.cacheDir.absolutePath
        )
        return Engine(configuracion).also { it.initialize() }
    }

    companion object {
        private const val NOMBRE_ARCHIVO = "modelo_local.litertlm"
        private const val TAMANO_MINIMO = 50L * 1024 * 1024
        private const val MARGEN_ESPACIO = 200L * 1024 * 1024
        private const val MAX_TOKENS_SALIDA = 512
        private const val HILOS = 4
        private const val INACTIVIDAD_MS = 3L * 60 * 1000

        private val mutex = Mutex()
        private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        private val _estadoDescarga = MutableStateFlow(EstadoDescargaModelo())
        val estadoDescarga: StateFlow<EstadoDescargaModelo> = _estadoDescarga.asStateFlow()

        @Volatile private var trabajoDescarga: Job? = null
        @Volatile private var motorCargado: Engine? = null
        @Volatile private var cierre: Job? = null

        private fun cerrarMotor() {
            cierre?.cancel()
            val motor = motorCargado
            motorCargado = null
            try {
                motor?.close()
            } catch (_: Exception) {
            }
        }

        private fun programarCierre() {
            cierre?.cancel()
            cierre = ambito.launch {
                delay(INACTIVIDAD_MS)
                mutex.withLock { cerrarMotor() }
            }
        }
    }
}

/** Instrucción base de sistema para el modelo local. */
const val INSTRUCCION_LOCAL =
    "Eres Iris, una asistente en español. Responde de forma breve, clara y directa. " +
        "Si no sabes algo, dilo; no inventes datos."

/** Combina la instrucción base con las reglas personalizadas y el resumen de la memoria local. */
fun construirInstruccionLocal(
    contextoUsuario: String,
    instruccionesPersonalizadas: String = ""
): String {
    val partes = mutableListOf(INSTRUCCION_LOCAL)
    val reglas = instruccionesPersonalizadas.trim()
    if (reglas.isNotEmpty()) {
        partes += "Instrucciones personalizadas del usuario (aplícalas en tus respuestas):\n$reglas"
    }
    val ctx = contextoUsuario.trim()
    if (ctx.isNotEmpty()) {
        partes += "Memoria y preferencias del usuario (úsalas de forma natural si son relevantes):\n$ctx"
    }
    return partes.joinToString("\n\n")
}
