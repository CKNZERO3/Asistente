package com.miasistente.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log

class OverlayAvatarService : Service() {

    companion object {
        private const val TAG = "OverlayAvatarService"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Intento de iniciar overlay sin permiso concedido")
                return
            }
            try {
                val intent = Intent(context, OverlayAvatarService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error al iniciar OverlayAvatarService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, OverlayAvatarService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Error al detener OverlayAvatarService", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Lógica de la ventana flotante activa
        return START_NOT_STICKY
    }
}

