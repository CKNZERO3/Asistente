package com.miasistente.app.data

import android.content.Context

/** Datos del usuario para personalizar la pantalla (por ahora, solo su nombre). */
class PerfilUsuario(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("asistente_perfil", Context.MODE_PRIVATE)

    var nombre: String
        get() = prefs.getString(CLAVE_NOMBRE, null).orEmpty()
        set(valor) {
            prefs.edit().putString(CLAVE_NOMBRE, valor.trim().take(LARGO_MAXIMO)).apply()
        }

    companion object {
        const val LARGO_MAXIMO = 24
        private const val CLAVE_NOMBRE = "nombre"
    }
}
