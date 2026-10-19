package com.miasistente.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.miasistente.app.MainActivity
import com.miasistente.app.audio.AudioRecorder
import com.miasistente.app.audio.VoiceDetector
import com.miasistente.app.audio.VoskModelManager
import com.miasistente.app.audio.VoskWakeWord
import com.miasistente.app.audio.WakeWordEngine
import com.miasistente.app.data.Ajustes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Estado del audio que la pantalla puede observar.
 *
 * @param capturando true cuando el micrófono está entregando audio de verdad
 * @param hayVoz true mientras el detector considera que alguien habla
 * @param nivelDb volumen actual en dBFS (-90 = silencio, 0 = máximo)
 * @param error mensaje de error, o null si todo va bien
 * @param preparandoModelo true mientras se descarga o carga el modelo de voz
 * @param progresoModelo 0..100 durante la descarga; -1 si no hay porcentaje (cargando)
 * @param palabraClave la palabra que el asistente está esperando
 * @param ultimoTexto lo último que el reconocedor entendió (para depurar)
 * @param activaciones cuántas veces se detectó la palabra clave desde que se activó el servicio
 */
data class EstadoAudio(
    val capturando: Boolean = false,
    val hayVoz: Boolean = false,
    val nivelDb: Float = -90f,
    val error: String? = null,
    val preparandoModelo: Boolean = false,
    val progresoModelo: Int = -1,
    val palabraClave: String = "",
    val ultimoTexto: String = "",
    val activaciones: Int = 0
)

/** El modelo de voz está instalado pero no se pudo cargar (probablemente dañado). */
private class ModeloDanadoException(causa: Throwable) : Exception(causa)

/**
 * Servicio en primer plano que mantiene la app viva y escucha el micrófono.
 *
 * Fase 1: arrancar, notificación persistente y estado.
 * Fase 2: captura continua de audio (AudioRecorder) + detección de voz (VoiceDetector).
 * Fase 3: detección de palabra clave (WakeWordEngine → VoskWakeWord).
 * Fase 4 en adelante: al detectar la palabra clave se escuchará el comando.
 *
 * Flujo de cada frame de audio:
 *   micrófono → VoiceDetector (¿hay voz?) → WakeWordEngine (¿dijo la palabra clave?)
 *
 * Se controla desde fuera con:
 *   AsistenteService.start(context)
 *   AsistenteService.stop(context)
 *   AsistenteService.isRunning     // StateFlow<Boolean>
 *   AsistenteService.estadoAudio   // StateFlow<EstadoAudio>
 */
class AsistenteService : Service() {

    companion object {
        const val ACTION_START = "com.miasistente.app.action.START"
        const val ACTION_STOP = "com.miasistente.app.action.STOP"

        private const val TAG = "AsistenteService"
        private const val CHANNEL_ID = "asistente_escucha"
        private const val NOTIFICATION_ID = 1001

        // Cada cuánto se refresca el nivel en pantalla (el audio llega ~31 veces por segundo)
        private const val INTERVALO_UI_MS = 100L

        // Estado compartido: el servicio lo escribe y la pantalla lo lee
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _estadoAudio = MutableStateFlow(EstadoAudio())
        val estadoAudio: StateFlow<EstadoAudio> = _estadoAudio.asStateFlow()

        /** Inicia el servicio. Llamar solo con la app abierta y con permiso de micrófono ya concedido. */
        fun start(context: Context) {
            val intent = Intent(context, AsistenteService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Detiene el servicio. */
        fun stop(context: Context) {
            context.stopService(Intent(context, AsistenteService::class.java))
        }
    }

    // Trabajo en segundo plano (descargar y cargar el modelo). Se cancela al detener el servicio.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trabajoInicio: Job? = null

    // Componentes de audio. @Volatile: los toca el hilo principal, el de audio y las corrutinas.
    @Volatile private var grabador: AudioRecorder? = null
    @Volatile private var motor: WakeWordEngine? = null
    @Volatile private var destruido = false
    private val detector = VoiceDetector()

    // Solo se tocan desde el hilo de audio
    private var ultimaActualizacionUi = 0L
    private var contadorActivaciones = 0

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> iniciarEnPrimerPlano()
            else -> {
                // ACTION_STOP (botón "Detener" de la notificación) o intent vacío
                stopSelf()
            }
        }
        // NOT_STICKY: si el sistema mata el servicio, NO se reinicia solo.
        // Android 12+ no permite reactivar el micrófono desde segundo plano,
        // así que un reinicio automático fallaría. El usuario lo reactiva desde la app.
        return START_NOT_STICKY
    }

    private fun iniciarEnPrimerPlano() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                crearNotificacion(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            // Ocurre, por ejemplo, si falta el permiso de micrófono o Android lo bloquea
            Log.e(TAG, "No se pudo iniciar en primer plano", e)
            _isRunning.value = false
            stopSelf()
            return
        }

        _isRunning.value = true
        Log.d(TAG, "Servicio iniciado")

        // Si ya se estaba preparando o escuchando, no repetir
        if (trabajoInicio?.isActive == true || grabador != null) return

        trabajoInicio = scope.launch { prepararYEscuchar() }
    }

    // ---------------------------------------------------------------------------------
    // Preparación: modelo de voz → motor de palabra clave → micrófono
    // ---------------------------------------------------------------------------------

    private suspend fun prepararYEscuchar() {
        val palabra = Ajustes.normalizar(Ajustes(applicationContext).palabraClave)
            .ifEmpty { Ajustes.PALABRA_POR_DEFECTO }

        // Estado limpio (esto también borra el error de la vez anterior)
        _estadoAudio.value = EstadoAudio(preparandoModelo = true, palabraClave = palabra)
        detector.reiniciar()
        ultimaActualizacionUi = 0L
        contadorActivaciones = 0

        // 1) Modelo + motor (puede descargar ≈ 39 MB la primera vez)
        val nuevoMotor: WakeWordEngine = try {
            cargarMotor(palabra)
        } catch (e: CancellationException) {
            throw e // el servicio se detuvo mientras preparaba: salir en silencio
        } catch (e: ModeloDanadoException) {
            Log.e(TAG, "Modelo dañado", e)
            fallar("El modelo de voz estaba dañado. Pulsa Activar para descargarlo de nuevo.")
            return
        } catch (e: IOException) {
            Log.e(TAG, "Fallo al preparar el modelo", e)
            fallar("No pude descargar el modelo de voz (≈ 39 MB). Revisa tu conexión a internet y el espacio libre.")
            return
        } catch (e: LinkageError) {
            // Falta una librería nativa de Vosk para el procesador de este teléfono
            Log.e(TAG, "No se pudo cargar la librería nativa", e)
            fallar("No se pudo cargar el motor de voz en este teléfono.")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al preparar el motor", e)
            fallar("Error al preparar el reconocimiento de voz: ${e.message}")
            return
        }

        // El servicio pudo detenerse justo mientras se cargaba el modelo
        if (destruido) {
            nuevoMotor.close()
            return
        }
        motor = nuevoMotor
        _estadoAudio.update { it.copy(preparandoModelo = false, progresoModelo = -1) }

        // 2) Micrófono
        val nuevoGrabador = AudioRecorder(applicationContext)
        val ok = nuevoGrabador.iniciar(
            onFrame = { muestras, cantidad -> procesarFrame(muestras, cantidad) },
            onError = { mensaje -> fallar(mensaje) }
        )
        if (ok) {
            grabador = nuevoGrabador
        }
        // Si no se pudo abrir el micrófono, onError ya llamó a fallar() y se está deteniendo
    }

    /** Instala el modelo si hace falta y crea el motor de palabra clave. */
    private suspend fun cargarMotor(palabra: String): WakeWordEngine {
        if (!VoskModelManager.estaInstalado(applicationContext)) {
            actualizarProgreso(0)
            VoskModelManager.instalar(applicationContext) { porcentaje ->
                actualizarProgreso(porcentaje)
            }
        }

        actualizarProgreso(-1) // "cargando…": sin porcentaje
        return withContext(Dispatchers.Default) {
            try {
                VoskWakeWord(
                    VoskModelManager.directorioModelo(applicationContext).absolutePath,
                    palabra
                )
            } catch (e: IOException) {
                // El modelo no carga: se borra para que el próximo intento lo descargue de nuevo
                VoskModelManager.borrar(applicationContext)
                throw ModeloDanadoException(e)
            }
        }
    }

    private fun actualizarProgreso(porcentaje: Int) {
        _estadoAudio.update { it.copy(preparandoModelo = true, progresoModelo = porcentaje) }
    }

    // ---------------------------------------------------------------------------------
    // Procesamiento de cada frame de audio
    // ---------------------------------------------------------------------------------

    /**
     * Se llama por cada frame de audio (≈31 veces por segundo, en el HILO DE AUDIO).
     * Debe ser rápido: nada de trabajo pesado aquí.
     */
    private fun procesarFrame(muestras: ShortArray, cantidad: Int) {
        // 1) ¿Hay voz? (barato: solo mide volumen)
        val voz = detector.procesar(muestras, cantidad)

        // 2) ¿Dijo la palabra clave? (el motor solo trabaja de verdad cuando hay voz)
        val motorActual = motor
        val detectada = try {
            motorActual?.procesar(muestras, cantidad, voz.hayVoz) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en el motor de palabra clave", e)
            false
        }

        val textoMotor = motorActual?.ultimoTexto.orEmpty()
        if (detectada) alDetectarPalabra(textoMotor)

        // 3) Publicar el estado para la pantalla
        val ahora = SystemClock.elapsedRealtime()
        val actual = _estadoAudio.value
        val cambioDeVoz = voz.hayVoz != actual.hayVoz
        val cambioDeTexto = textoMotor != actual.ultimoTexto

        // Los cambios de voz y de texto se publican al instante; el nivel, cada INTERVALO_UI_MS
        if (cambioDeVoz || cambioDeTexto || !actual.capturando ||
            ahora - ultimaActualizacionUi >= INTERVALO_UI_MS
        ) {
            ultimaActualizacionUi = ahora
            _estadoAudio.update {
                it.copy(
                    capturando = true,
                    hayVoz = voz.hayVoz,
                    nivelDb = voz.nivelDb,
                    ultimoTexto = textoMotor
                )
            }
            if (cambioDeVoz) {
                Log.d(TAG, if (voz.hayVoz) "Voz detectada" else "Fin de voz")
            }
        }
    }

    /**
     * Se llama cuando se detecta la palabra clave.
     * Fase 4: aquí se empezará a escuchar el comando del usuario.
     */
    private fun alDetectarPalabra(texto: String) {
        contadorActivaciones++
        Log.d(TAG, "¡Palabra clave detectada! (#$contadorActivaciones) texto=«$texto»")
        _estadoAudio.update {
            it.copy(activaciones = contadorActivaciones, ultimoTexto = texto)
        }
    }

    // ---------------------------------------------------------------------------------
    // Cierre y errores
    // ---------------------------------------------------------------------------------

    /** Libera el micrófono y el motor. Conserva el último error (si lo hubo) para mostrarlo. */
    private fun detenerEscucha() {
        grabador?.detener() // espera a que termine el hilo de audio
        grabador = null
        motor?.close()      // seguro: close() espera si el motor está procesando un frame
        motor = null
        detector.reiniciar()
        _estadoAudio.update { EstadoAudio(error = it.error) }
    }

    /** Se llama si algo falla (puede ser desde cualquier hilo). Muestra el error y apaga el servicio. */
    private fun fallar(mensaje: String) {
        Log.e(TAG, "Error: $mensaje")
        _estadoAudio.update {
            it.copy(
                capturando = false,
                hayVoz = false,
                preparandoModelo = false,
                error = mensaje
            )
        }
        stopSelf()
    }

    override fun onDestroy() {
        destruido = true
        scope.cancel() // cancela descargas o cargas en curso
        detenerEscucha()
        _isRunning.value = false
        Log.d(TAG, "Servicio detenido")
        super.onDestroy()
    }

    // No es un servicio "enlazable": se controla solo con start/stop
    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------------------------
    // Notificación
    // ---------------------------------------------------------------------------------

    private fun crearCanalNotificacion() {
        val canal = NotificationChannel(
            CHANNEL_ID,
            "Escucha del asistente",
            NotificationManager.IMPORTANCE_LOW // sin sonido ni vibración
        ).apply {
            description = "Notificación persistente mientras el asistente está activo"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun crearNotificacion(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val palabra = Ajustes(this).palabraClave

        // Tocar la notificación abre la app
        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )

        // Botón "Detener" dentro de la notificación
        val detener = PendingIntent.getService(
            this, 1,
            Intent(this, AsistenteService::class.java).setAction(ACTION_STOP),
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Asistente activo")
            .setContentText("Esperando la palabra clave «$palabra»")
            .setContentIntent(abrirApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", detener)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
