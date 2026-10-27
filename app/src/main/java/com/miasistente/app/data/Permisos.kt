package com.miasistente.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Consulta y solicitud de permisos especiales en un solo lugar.
 *
 * - Micrófono: se pide con el cuadro normal del sistema.
 * - Superposición (SYSTEM_ALERT_WINDOW): NO tiene cuadro; hay que llevar al usuario a una
 *   pantalla de Ajustes donde la activa a mano. Por eso se revisa de nuevo al volver a la app.
 */
object Permisos {

    fun microfono(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun superposicion(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Pantalla de Ajustes donde se concede "Mostrar sobre otras apps" a esta app. */
    fun intentSuperposicion(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Ajustes generales de la app (para cuando el micrófono quedó denegado de forma permanente). */
    fun intentAjustesApp(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
