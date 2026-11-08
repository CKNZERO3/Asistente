package com.miasistente.app.comandos

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ejecuta intenciones locales: notas mediante apps externas, apertura de apps y música. */
class EjecutorLocal(context: Context) {

    companion object {
        const val PAQUETE_MUSICOLET = "in.krosbits.musicolet"

        // Compartido por todas las instancias: se construye una sola vez por proceso
        @Volatile private var cache: List<Entrada>? = null
        private val bloqueo = Any()
    }

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private class Candidata(val etiqueta: String, val intent: Intent, val puntaje: Int)

    private class Entrada(
        val etiqueta: String,
        val normalizada: String,
        val paquete: String,
        val clase: String
    )

    /** Llamar en segundo plano al abrir la app para que el primer comando sea instantáneo. */
    fun precalentar() {
        obtenerIndice()
    }

    private fun obtenerIndice(forzar: Boolean = false): List<Entrada> = synchronized(bloqueo) {
