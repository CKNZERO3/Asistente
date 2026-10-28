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
import com.miasistente.app.dominio.AssistantState
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

private class ModeloDanadoException(causa: Throwable) : Exception(causa)

class AsistenteService : Service() {

    companion object {
        const val ACTION_START = "com.miasistente.app.action.START"
        const val ACTION_STOP = "com.miasistente.app.action.STOP"

        private const val TAG = "AsistenteService"
        private const val CHANNEL_ID = "asistente_escucha"
        private const val NOTIFICATION_ID = 1001
        private const val INTERVALO_UI_MS = 100L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _estadoAudio = MutableStateFlow(EstadoAudio())
        val estadoAudio: StateFlow<EstadoAudio> = _estadoAudio.asStateFlow()

        // Reactive bridge for UI Avatar states
        private val _assistantState = MutableStateFlow(AssistantState.IDLE)
        val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

        fun updateAssistantState(state: AssistantState) {
            _assistantState.value = state
        }

        fun start(context: Context) {
            val intent = Intent(context, AsistenteService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AsistenteService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trabajoInicio: Job? = null

    @Volatile private var grabador: AudioRecorder? = null
    @Volatile private var motor: WakeWordEngine? = null
    @Volatile private var destruido = false
    private val detector = VoiceDetector()

    private var ultimaActualizacionUi = 0L
    private var contadorActivaciones = 0

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> iniciarEnPrimerPlano()
            else -> stopSelf()
        }
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
            Log.e(TAG, "No se pudo iniciar en primer plano", e)
            _isRunning.value = false
            stopSelf()
            return
        }

        _isRunning.value = true
        Log.d(TAG, "Servicio iniciado")

        if (trabajoInicio?.isActive == true || grabador != null) return
        trabajoInicio = scope.launch { prepararYEscuchar() }
    }

    private suspend fun prepararYEscuchar() {
        val palabra = Ajustes.normalizar(Ajustes(applicationContext).palabraClave)
            .ifEmpty { Ajustes.PALABRA_POR_DEFECTO }

        _estadoAudio.value = EstadoAudio(preparandoModelo = true, palabraClave = palabra)
        _assistantState.update { AssistantState.THINKING }
        detector.reiniciar()
        ultimaActualizacionUi = 0L
        contadorActivaciones = 0

        val nuevoMotor: WakeWordEngine = try {
            cargarMotor(palabra)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModeloDanadoException) {
            Log.e(TAG, "Modelo dañado", e)
            fallar("El modelo de voz estaba dañado. Pulsa Activar para descargarlo de nuevo.")
            return
        } catch (e: IOException) {
            Log.e(TAG, "Fallo al preparar el modelo", e)
            fallar("No pude descargar el modelo de voz (≈ 39 MB). Revisa tu conexión a internet y el espacio libre.")
            return
        } catch (e: LinkageError) {
            Log.e(TAG, "No se pudo cargar la librería nativa", e)
            fallar("No se pudo cargar el motor de voz en este teléfono.")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al preparar el motor", e)
            fallar("Error al preparar el reconocimiento de voz: ${e.message}")
            return
        }

        if (destruido) {
            nuevoMotor.close()
            return
        }
        motor = nuevoMotor
        _estadoAudio.update { it.copy(preparandoModelo = false, progresoModelo = -1) }
        _assistantState.update { AssistantState.IDLE }

        val nuevoGrabador = AudioRecorder(applicationContext)
        val ok = nuevoGrabador.iniciar(
            onFrame = { muestras, cantidad -> procesarFrame(muestras, cantidad) },
            onError = { mensaje -> fallar(mensaje) }
        )
        if (ok) {
            grabador = nuevoGrabador
        }
    }

    private suspend fun cargarMotor(palabra: String): WakeWordEngine {
        if (!VoskModelManager.estaInstalado(applicationContext)) {
            actualizarProgreso(0)
            VoskModelManager.instalar(applicationContext) { porcentaje ->
                actualizarProgreso(porcentaje)
            }
        }

        actualizarProgreso(-1)
        return withContext(Dispatchers.Default) {
            try {
                VoskWakeWord(
                    VoskModelManager.directorioModelo(applicationContext).absolutePath,
                    palabra
                )
            } catch (e: IOException) {
                VoskModelManager.borrar(applicationContext)
                throw ModeloDanadoException(e)
            }
        }
    }

    private fun actualizarProgreso(porcentaje: Int) {
        _estadoAudio.update { it.copy(preparandoModelo = true, progresoModelo = porcentaje) }
    }

    private fun procesarFrame(muestras: ShortArray, cantidad: Int) {
        val voz = detector.procesar(muestras, cantidad)

        if (_isRunning.value && _assistantState.value != AssistantState.THINKING && _assistantState.value != AssistantState.SPEAKING) {
            if (voz.hayVoz) {
                _assistantState.update { AssistantState.LISTENING }
            } else {
                _assistantState.update { AssistantState.IDLE }
            }
        }

        val motorActual = motor
        val detectada = try {
            motorActual?.procesar(muestras, cantidad, voz.hayVoz) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en el motor de palabra clave", e)
            false
        }

        val textoMotor = motorActual?.ultimoTexto.orEmpty()
        if (detectada) alDetectarPalabra(textoMotor)

        val ahora = SystemClock.elapsedRealtime()
        val actual = _estadoAudio.value
        val cambioDeVoz = voz.hayVoz != actual.hayVoz
        val cambioDeTexto = textoMotor != actual.ultimoTexto

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
        }
    }

    private fun alDetectarPalabra(texto: String) {
        if (texto.isBlank()) return
        contadorActivaciones++
        Log.d(TAG, "¡Comando de voz transcrito! (#$contadorActivaciones) texto=«$texto»")
        _estadoAudio.update {
            it.copy(activaciones = contadorActivaciones, ultimoTexto = texto)
        }

        _assistantState.update { AssistantState.THINKING }

        scope.launch {
            try {
                val procesador = com.miasistente.app.comandos.ProcesadorComandos(applicationContext)
                procesador.procesar(texto)

                _assistantState.update { AssistantState.SPEAKING }
                kotlinx.coroutines.delay(2500L)
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando comando tras activación", e)
            } finally {
                _assistantState.update { AssistantState.IDLE }
            }
        }
    }

    private fun detenerEscucha() {
        grabador?.detener()
        grabador = null
        motor?.close()
        motor = null
        detector.reiniciar()
        _assistantState.update { AssistantState.IDLE }
        _estadoAudio.update { EstadoAudio(error = it.error) }
    }

    private fun fallar(mensaje: String) {
        Log.e(TAG, "Error: $mensaje")
        _assistantState.update { AssistantState.IDLE }
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
        scope.cancel()
        detenerEscucha()
        _isRunning.value = false
        Log.d(TAG, "Servicio detenido")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun crearCanalNotificacion() {
        val canal = NotificationChannel(
            CHANNEL_ID,
            "Escucha del asistente",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificación persistente mientras el asistente está activo"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun crearNotificacion(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val palabra = Ajustes(this).palabraClave

        val abrirApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )

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
