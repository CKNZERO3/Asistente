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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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

    private companion object {
        const val NOMBRE_ARCHIVO = "modelo_local.litertlm"
        const val TAMANO_MINIMO = 50L * 1024 * 1024
        const val MARGEN_ESPACIO = 200L * 1024 * 1024
        const val MAX_TOKENS_SALIDA = 512
        const val HILOS = 4
        const val INACTIVIDAD_MS = 3L * 60 * 1000

        val mutex = Mutex()
        val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile var motorCargado: Engine? = null
        @Volatile var cierre: Job? = null

        fun cerrarMotor() {
            cierre?.cancel()
            val motor = motorCargado
            motorCargado = null
            try {
                motor?.close()
            } catch (_: Exception) {
            }
        }

        fun programarCierre() {
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
