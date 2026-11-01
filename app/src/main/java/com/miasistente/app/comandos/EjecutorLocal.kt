package com.miasistente.app.comandos

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ejecuta las intenciones que no necesitan internet: notas, abrir apps y música (Musicolet). */
class EjecutorLocal(context: Context) {

    companion object {
        const val PAQUETE_MUSICOLET = "in.krosbits.musicolet"
    }

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager
    private val notas = NotasRepositorio(appContext)

    private class Candidata(val etiqueta: String, val intent: Intent, val puntaje: Int)

    suspend fun ejecutar(intencion: Intencion): ResultadoComando = when (intencion) {
        is Intencion.CrearNota -> crearNota(intencion.contenido)
        is Intencion.AbrirAplicacion -> withContext(Dispatchers.Default) { abrirAplicacion(intencion.nombreApp) }
        is Intencion.ReproducirMusica -> reproducirMusica(intencion.consulta)
        is Intencion.ConsultaIA -> ResultadoComando(false, "Esta consulta necesita Gemini.")
    }

    // --- Notas ---------------------------------------------------------------------------

    private suspend fun crearNota(texto: String): ResultadoComando {
        if (texto.isBlank()) return ResultadoComando(false, "¿Qué quieres anotar?")
        withContext(Dispatchers.IO) { notas.agregar(texto) }
        return ResultadoComando(true, "Nota guardada: «$texto»")
    }

    // --- Abrir aplicaciones (búsqueda dinámica en las apps instaladas) --------------------

    private fun abrirAplicacion(nombre: String): ResultadoComando {
        if (nombre.isBlank()) return ResultadoComando(false, "¿Qué aplicación quieres abrir?")
        val buscado = normalizar(nombre)

        // Prioridad: Musicolet por paquete, aunque su nombre en pantalla cambie
        if ("musicolet" in buscado) {
            val intent = pm.getLaunchIntentForPackage(PAQUETE_MUSICOLET)
                ?: return ResultadoComando(false, "Musicolet no está instalado.")
            return lanzar(intent, "Abriendo Musicolet")
        }

        val app = buscarApp(buscado)
            ?: return ResultadoComando(false, "No encontré una app llamada «$nombre».")
        return lanzar(app.intent, "Abriendo ${app.etiqueta}")
    }

    @Suppress("DEPRECATION")
    private fun buscarApp(buscado: String): Candidata? {
        val filtro = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(filtro, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(filtro, 0)
        }
        return apps.asSequence()
            .filter { it.activityInfo.packageName != appContext.packageName }
            .map {
                val etiqueta = it.loadLabel(pm).toString()
                Candidata(
                    etiqueta,
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(it.activityInfo.packageName, it.activityInfo.name),
                    puntaje(buscado, normalizar(etiqueta), it.activityInfo.packageName.lowercase())
                )
            }
            .filter { it.puntaje > 0 }
            .maxWithOrNull(compareBy<Candidata>({ it.puntaje }, { -it.etiqueta.length }))
    }

    /** Qué tan bien encaja lo pedido con el nombre de una app (0 = no encaja). */
    private fun puntaje(q: String, etiqueta: String, paquete: String): Int {
        val qc = q.replace(" ", "")
        val ec = etiqueta.replace(" ", "")
        return when {
            etiqueta == q -> 100
            ec == qc -> 95
            etiqueta.startsWith(q) -> 85
            q in etiqueta -> 70
            etiqueta.length >= 3 && etiqueta in q -> 60
            q.split(" ").all { it in etiqueta } -> 50
            qc.length >= 4 && qc in paquete -> 30
            else -> 0
        }
    }

    // --- Música (Musicolet) --------------------------------------------------------------

    private fun reproducirMusica(consulta: String): ResultadoComando {
        val abrir = pm.getLaunchIntentForPackage(PAQUETE_MUSICOLET)
            ?: return ResultadoComando(false, "Musicolet no está instalado.")

        if (consulta.isNotBlank()) {
            val buscar = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .setPackage(PAQUETE_MUSICOLET)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .putExtra(SearchManager.QUERY, consulta)
            if (iniciar(buscar)) return ResultadoComando(true, "Buscando «$consulta» en Musicolet")
        }
        return lanzar(abrir, "Musicolet abierto")
    }

    // --- Utilidades ----------------------------------------------------------------------

    private fun iniciar(intent: Intent): Boolean = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun lanzar(intent: Intent, exito: String) =
        if (iniciar(intent)) ResultadoComando(true, exito)
        else ResultadoComando(false, "Android no me dejó abrir esa app desde segundo plano.")
}
