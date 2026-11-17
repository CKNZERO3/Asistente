package com.miasistente.app.comandos

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat

/**
 * Android avisa aquí qué app eligió el usuario en el selector "Guardar nota con…".
 * Iris lo registra como hábito para, con el tiempo, enviar directo a esa app.
 */
class EleccionAppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val elegida = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_CHOSEN_COMPONENT,
            ComponentName::class.java
        ) ?: return
        GestorNotas.registrarEleccion(context, elegida.packageName)
    }
}
