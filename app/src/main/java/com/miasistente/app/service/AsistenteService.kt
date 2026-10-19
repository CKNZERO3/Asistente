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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado del audio que la pantalla puede observar.
 *
 * @param capturando true cuando el micrófono está entregando audio de verdad
 * @param hayVoz true mientras el detector considera que alguien habla
 * @param nivelDb volumen actual en dBFS (-90 = silencio, 0 = máximo)
 * @param error mensaje de error de audio, o null si todo va bien
 */
data class EstadoAudio(
    val capturando: Boolean = false,
    val hayVoz: Boolean = false,
    val nivelDb: Float = -90f,
    val error: String? = null
)

/**
 * Servicio en primer plano que mantiene la app viva y escucha el micrófono.
 *
 * Fase 1: arrancar, notificación persistente y estado.
 * Fase 2: captura continua de audio (AudioRecorder) + detección de voz (VoiceDetector).
 * Fase 3 en adelante: los frames de audio se enviarán también al detector de palabra clave.
 *
 * Se controla desde fuera con:
 *   AsistenteService.start(context)
 *   AsistenteService.stop(context)
 *   AsistenteService.isRunning     // StateFlow<Boolean>: servicio activo y micrófono abierto
 *   AsistenteService.estadoAudio   // StateFlow<EstadoAudio>: nivel, voz detectada, errores
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

    // Componentes de audio (se crean al iniciar la escucha y se liberan al detenerla)
    private var grabador: AudioRecorder? = null
    private val detector = VoiceDetector()

    // Solo se toca desde el hilo de audio
    private var ultimaActualizacionUi = 0L

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

        // Solo se marca como "activo" si el micrófono realmente se abrió
        if (iniciarEscucha()) {
            _isRunning.value = true
            Log.d(TAG, "Servicio iniciado")
        } else {
            stopSelf()
        }
    }

    // ---------------------------------------------------------------------------------
    // Escucha: micrófono + detección de voz
    // ---------------------------------------------------------------------------------

    /** Abre el micrófono y empieza a procesar audio. @return true si quedó capturando. */
    private fun iniciarEscucha(): Boolean {
        if (grabador != null) return true // ya estaba escuchando

        // Limpia el estado y el error de la vez anterior
        _estadoAudio.value = EstadoAudio()
        detector.reiniciar()
        ultimaActualizacionUi = 0L

        val nuevo = AudioRecorder(applicationContext)
        val ok = nuevo.iniciar(
            onFrame = { muestras, cantidad -> procesarFrame(muestras, cantidad) },
            onError = { mensaje -> alFallarElAudio(mensaje) }
        )
        if (!ok) return false

        grabador = nuevo
        return true
    }

    /** Libera el micrófono. Conserva el último error (si lo hubo) para mostrarlo en pantalla. */
    private fun detenerEscucha() {
        grabador?.detener()
        grabador = null
        detector.reiniciar()
        _estadoAudio.update { EstadoAudio(error = it.error) }
    }

    /**
     * Se llama por cada frame de audio (≈31 veces por segundo, en el HILO DE AUDIO).
     * Debe ser rápido: nada de trabajo pesado aquí.
     *
     * Fase 3: justo aquí se entregará el frame también al detector de palabra clave.
     */
    private fun procesarFrame(muestras: ShortArray, cantidad: Int) {
        val resultado = detector.procesar(muestras, cantidad)

        val ahora = SystemClock.elapsedRealtime()
        val actual = _estadoAudio.value
        val cambioDeVoz = resultado.hayVoz != actual.hayVoz

        // Los cambios de voz se publican al instante; el nivel, cada INTERVALO_UI_MS
        if (cambioDeVoz || !actual.capturando || ahora - ultimaActualizacionUi >= INTERVALO_UI_MS) {
            ultimaActualizacionUi = ahora
            _estadoAudio.update {
                it.copy(
                    capturando = true,
                    hayVoz = resultado.hayVoz,
                    nivelDb = resultado.nivelDb
                )
            }
            if (cambioDeVoz) {
                Log.d(TAG, if (resultado.hayVoz) "Voz detectada" else "Fin de voz")
            }
        }
    }

    /** Se llama si el audio falla (puede ser desde el hilo de audio). Apaga el servicio. */
    private fun alFallarElAudio(mensaje: String) {
        Log.e(TAG, "Error de audio: $mensaje")
        _estadoAudio.update { it.copy(capturando = false, hayVoz = false, error = mensaje) }
        stopSelf()
    }

    override fun onDestroy() {
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
            .setContentText("Escuchando el micrófono")
            .setContentIntent(abrirApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", detener)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
