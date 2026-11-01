package com.miasistente.app.comandos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

class EjecutorLocal(context: Context) {

    private val appContext = context.applicationContext
    private val notas = NotasRepositorio(appContext)

    companion object {
        private const val TAG = "EjecutorLocal"
        const val PAQUETE_MUSICOLET = "in.krosbits.musicolet"
    }

    suspend fun ejecutar(comando: Intencion): ResultadoComando = when (comando) {
        is Intencion.CrearNota -> crearNota(comando.contenido)
        is Intencion.AbrirAplicacion -> abrirAplicacion(comando.nombreApp)
        is Intencion.ReproducirMusica -> reproducirMusica(comando.consulta)
        is Intencion.ConsultaIA -> ResultadoComando(false, "Esta consulta necesita Gemini.")
    }

    private suspend fun crearNota(texto: String): ResultadoComando {
        if (texto.isBlank()) return ResultadoComando(false, "¿Qué quieres anotar?")
        withContext(Dispatchers.IO) { notas.agregar(texto) }
        return ResultadoComando(true, "Nota guardada: «$texto»")
    }

    private fun abrirAplicacion(nombreApp: String): ResultadoComando {
        val pm = appContext.packageManager
        val query = limpiarTexto(nombreApp)

        if (query.contains("musicolet")) {
            return reproducirMusica("")
        }

        try {
            // Obtener todas las apps instaladas que tienen actividad de inicio
            val intentMain = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intentMain, 0)

            // Buscar coincidencia exacta o parcial con el nombre de la app
            val appEncontrada = apps.firstOrNull { resolvedInfo ->
                val label = resolvedInfo.loadLabel(pm).toString()
                limpiarTexto(label).contains(query)
            }

            if (appEncontrada != null) {
                val packageName = appEncontrada.activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    return ResultadoComando(true, "Abriendo $nombreApp")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando aplicación", e)
        }

        return ResultadoComando(false, "No encontré ninguna aplicación instalada llamada «$nombreApp».")
    }

    private fun reproducirMusica(consulta: String): ResultadoComando {
        val pm = appContext.packageManager
        val instalado = try {
            pm.getPackageInfo(PAQUETE_MUSICOLET, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (!instalado) return ResultadoComando(false, "Musicolet no está instalado.")

        val abrirApp = pm.getLaunchIntentForPackage(PAQUETE_MUSICOLET)
            ?: return ResultadoComando(false, "No pude abrir Musicolet.")
        abrirApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val consultaLimpia = consulta.lowercase().trim()
        if (consultaLimpia.isBlank() || consultaLimpia.contains("musicolet")) {
            return try {
                appContext.startActivity(abrirApp)
                ResultadoComando(true, "Musicolet abierto")
            } catch (e: Exception) {
                ResultadoComando(false, "Android bloqueó abrir Musicolet desde segundo plano.")
            }
        }

        val buscar = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(PAQUETE_MUSICOLET)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(android.app.SearchManager.QUERY, consulta)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(buscar)
            return ResultadoComando(true, "Buscando «$consulta» en Musicolet")
        } catch (e: ActivityNotFoundException) {
            appContext.startActivity(abrirApp)
            return ResultadoComando(true, "Musicolet abierto")
        }
    }

    private fun limpiarTexto(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()
}
