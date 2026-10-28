package com.miasistente.app.comandos

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EjecutorLocal(context: Context) {

    private val appContext = context.applicationContext
    private val notas = NotasRepositorio(appContext)

    companion object {
        const val PAQUETE_MUSICOLET = "in.krosbits.musicolet"
    }

    suspend fun ejecutar(comando: Comando): ResultadoComando = when (comando) {
        is Comando.CrearNota -> crearNota(comando.texto)
        is Comando.ReproducirMusica -> reproducirMusica(comando.consulta)
        is Comando.ConsultaIA -> ResultadoComando(false, "Esta consulta necesita Gemini.")
    }

    private suspend fun crearNota(texto: String): ResultadoComando {
        if (texto.isBlank()) return ResultadoComando(false, "¿Qué quieres anotar?")
        withContext(Dispatchers.IO) { notas.agregar(texto) }
        return ResultadoComando(true, "Nota guardada: «$texto»")
    }

    private fun reproducirMusica(consulta: String): ResultadoComando {
        val gestor = appContext.packageManager
        val instalado = try {
            gestor.getPackageInfo(PAQUETE_MUSICOLET, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (!instalado) return ResultadoComando(false, "Musicolet no está instalado.")

        val abrirApp = gestor.getLaunchIntentForPackage(PAQUETE_MUSICOLET)
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
            putExtra(SearchManager.QUERY, consulta)
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
}
