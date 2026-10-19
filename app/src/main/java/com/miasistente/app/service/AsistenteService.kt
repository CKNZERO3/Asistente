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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.miasistente.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio en primer plano que mantiene la app viva con el micrófono disponible.
 *
 * Fase 1: solo arranca, muestra la notificación persistente y reporta su estado.
 * Fase 2 en adelante: aquí dentro se conectará la captura de audio y el wakeword.
 *
 * Se controla desde fuera con:
 *   AsistenteService.start(context)
 *   AsistenteService.stop(context)
 *   AsistenteService.isRunning   // StateFlow<Boolean> para que la pantalla lo observe
 */
class AsistenteService : Service() {

    companion object {
        const val ACTION_START = "com.miasistente.app.action.START"
        const val ACTION_STOP = "com.miasistente.app.action.STOP"

        private const val TAG = "AsistenteService"
        private const val CHANNEL_ID = "asistente_escucha"
        private const val NOTIFICATION_ID = 1001

        // Estado compartido: el servicio lo escribe y la pantalla lo lee
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

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
            _isRunning.value = true
            Log.d(TAG, "Servicio iniciado")
            iniciarEscucha()
        } catch (e: Exception) {
            // Ocurre, por ejemplo, si falta el permiso de micrófono o Android lo bloquea
            Log.e(TAG, "No se pudo iniciar en primer plano", e)
            _isRunning.value = false
            stopSelf()
        }
    }

    /** Punto de enganche para la Fase 2: aquí arrancará la captura de audio. */
    private fun iniciarEscucha() {
        // TODO Fase 2: AudioCapture + detección de palabra clave
    }

    /** Punto de enganche para la Fase 2: aquí se liberará el micrófono. */
    private fun detenerEscucha() {
        // TODO Fase 2: detener AudioCapture y liberar recursos
    }

    override fun onDestroy() {
        detenerEscucha()
        _isRunning.value = false
        Log.d(TAG, "Servicio detenido")
        super.onDestroy()
    }

    // No es un servicio "enlazable": se controla solo con start/stop
    override fun onBind(intent: Intent?): IBinder? = null

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
            .setContentText("Listo para escuchar")
            .setContentIntent(abrirApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", detener)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
