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

/**
 * IA que corre en el propio teléfono (LiteRT-LM, procesador). Se encarga de las tareas ligeras
 * para no gastar la cuota de Gemini. El modelo (.litertlm) lo importa el usuario desde Ajustes.
 *
 * El motor se carga solo cuando hace falta y se libera tras unos minutos sin uso, para no
 * ocupar memoria. El estado es compartido por toda la app (un único motor).
 */
class ModeloLocal(context: Context) {

    private val appContext = context.applicationContext
    private val carpeta = File(appContext.filesDir, "modelo_local")
    private val archivo = File(carpeta, NOMBRE_ARCHIVO)

    fun disponible(): Boolean = archivo.isFile && archivo.length() >= TAMANO_MINIMO

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

/** Instrucción de sistema para las respuestas del modelo local. */
const val INSTRUCCION_LOCAL =
    "Eres Iris, una asistente en español. Responde de forma breve, clara y directa. " +
        "Si no sabes algo, dilo; no inventes datos."
