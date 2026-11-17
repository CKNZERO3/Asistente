package com.miasistente.app.comandos

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.miasistente.app.memoria.AprendizajeHabitos
import com.miasistente.app.memoria.ClasificadorCuadernos
import com.miasistente.app.memoria.Cuaderno
import com.miasistente.app.memoria.MemoriaLocal
import kotlinx.coroutines.CancellationException
import java.io.File

/** Imagen o archivo adjunto que el usuario quiere guardar. */
data class AdjuntoNota(val uri: String, val nombre: String, val mime: String?) {
    val esImagen: Boolean get() = mime?.startsWith("image/") == true
}

/**
 * Guarda notas donde el usuario quiere:
 * - en un cuaderno de Iris Notebook (por nombre, por tema o creando uno nuevo), sin red;
 * - en una app externa (Keep, Samsung Notes…) mediante el Intent estándar de compartir;
 * - si no dijo dónde, según su hábito aprendido, o con el selector del sistema mientras aprende.
 */
class GestorNotas(context: Context) {

    private val appContext = context.applicationContext
    private val memoria = MemoriaLocal.get(appContext)

    /** Última nota guardada, para responder a "no se guardó" sin perder el contexto. */
    data class UltimaNota(
        val contenido: String,
        val adjuntos: List<AdjuntoNota>,
        val cuaderno: String?,
        val app: String?
    )

    private data class AppDestino(val paquete: String, val etiqueta: String)

    suspend fun guardar(peticion: PeticionGuardar, adjuntos: List<AdjuntoNota> = emptyList()): ResultadoComando {
        val contenido = peticion.contenido.trim()
        if (contenido.isEmpty() && adjuntos.isEmpty()) {
            return ResultadoComando(
                false,
                if (peticion.mencionaAdjunto) "Adjunta primero la imagen o el archivo que quieres guardar."
                else "¿Qué quieres anotar?"
            )
        }
        return try {
            when (val destino = peticion.destino) {
                is DestinoNota.Cuaderno -> guardarEnCuaderno(destino.nombre, contenido, adjuntos)
                is DestinoNota.App -> enviarAAppNombrada(destino.nombre, contenido, adjuntos)
                DestinoNota.SinEspecificar -> guardarSegunHabito(contenido, adjuntos)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo guardar la nota", e)
            ResultadoComando(false, "No pude guardar la nota. Inténtalo de nuevo.")
        }
    }

    /** Respuesta a "no se guardó": se asegura una copia en Iris Notebook. */
    suspend fun reintentarUltima(): ResultadoComando? {
        val u = ultima ?: return null
        if (u.cuaderno != null) {
            return ResultadoComando(
                true,
                "Sí está guardado: lo tienes en tu cuaderno «${u.cuaderno}», dentro de Iris Notebook."
            )
        }
        val copia = guardarEnCuaderno(null, u.contenido, u.adjuntos)
        if (!copia.exito) return copia
        val donde = u.app?.let { "en $it" } ?: "en la app que elegiste"
        return ResultadoComando(
            true,
            "Perdón, parece que no se guardó $donde. ${copia.mensaje.replaceFirst("Guardé", "Ya guardé")}"
        )
    }

    // --- Cuadernos de Iris ---------------------------------------------------------------

    private suspend fun guardarEnCuaderno(
        nombre: String?,
        contenido: String,
        adjuntos: List<AdjuntoNota>
    ): ResultadoComando {
        val textoClave = contenido.ifBlank { adjuntos.joinToString(" ") { it.nombre } }
        val tema = ClasificadorCuadernos.tema(textoClave)
        var creado = false

        val cuaderno: Cuaderno = if (nombre != null) {
            memoria.buscarCuadernoPorNombre(nombre)
                ?: memoria.crearCuaderno(nombre).also { creado = true }
        } else {
            cuadernoPorHabito(tema)
                ?: ClasificadorCuadernos.elegir(textoClave, memoria.resumenes())
                    ?.let { elegido -> memoria.cuadernos().firstOrNull { it.id == elegido.id } }
                ?: memoria.crearCuaderno(ClasificadorCuadernos.tituloNuevo(textoClave)).also { creado = true }
        }

        if (contenido.isNotBlank()) {
            memoria.agregarEntrada(cuaderno.id, MemoriaLocal.TIPO_TEXTO, texto = contenido)
        }
        adjuntos.forEach {
            memoria.agregarEntrada(
                cuaderno.id,
                if (it.esImagen) MemoriaLocal.TIPO_IMAGEN else MemoriaLocal.TIPO_ARCHIVO,
                uri = it.uri,
                mime = it.mime,
                nombre = it.nombre
            )
        }

        // Aprendizaje en segundo plano: no espera a la base de datos.
        memoria.registrarHabito(CONTEXTO_DESTINO, OPCION_CUADERNO)
        if (tema != null) memoria.registrarHabito(contextoTema(tema), cuaderno.id.toString())

        ultima = UltimaNota(contenido, adjuntos, cuaderno.titulo, null)
        val donde = if (creado) "en un cuaderno nuevo, «${cuaderno.titulo}»" else "en tu cuaderno «${cuaderno.titulo}»"
        return ResultadoComando(true, "Guardé ${describir(contenido, adjuntos)} $donde.")
    }

    /** "¿A qué cuaderno suele mandar este tipo de contenido?" */
    private suspend fun cuadernoPorHabito(tema: String?): Cuaderno? {
        tema ?: return null
        val id = AprendizajeHabitos.decidir(memoria.pesosHabito(contextoTema(tema)))?.toLongOrNull() ?: return null
        return memoria.cuadernos().firstOrNull { it.id == id }
    }

    // --- Apps externas -------------------------------------------------------------------

    private suspend fun guardarSegunHabito(contenido: String, adjuntos: List<AdjuntoNota>): ResultadoComando {
        val preferida = AprendizajeHabitos.decidir(memoria.pesosHabito(CONTEXTO_DESTINO))
        return when {
            preferida == OPCION_CUADERNO -> guardarEnCuaderno(null, contenido, adjuntos)
            preferida != null && preferida.startsWith(PREFIJO_APP) -> {
                val paquete = preferida.removePrefix(PREFIJO_APP)
                enviarAPaquete(paquete, etiquetaDe(appContext, paquete) ?: paquete, contenido, adjuntos)
                    ?: abrirSelector(contenido, adjuntos)
            }
            else -> abrirSelector(contenido, adjuntos)
        }
    }

    private suspend fun enviarAAppNombrada(
        nombre: String,
        contenido: String,
        adjuntos: List<AdjuntoNota>
    ): ResultadoComando {
        val app = resolverApp(nombre)
        if (app == null) {
            val copia = guardarEnCuaderno(null, contenido, adjuntos)
            return ResultadoComando(
                copia.exito,
                "No encontré «$nombre» en tu teléfono, así que lo guardé aquí: ${copia.mensaje.replaceFirstChar { it.lowercase() }}"
            )
        }
        return enviarAPaquete(app.paquete, app.etiqueta, contenido, adjuntos)
            ?: ResultadoComando(false, "No pude abrir ${app.etiqueta} para guardar la nota.")
    }

    /** Envía directo a la app (sin selector). Null si Android no lo permitió. */
    private fun enviarAPaquete(
        paquete: String,
        etiqueta: String,
        contenido: String,
        adjuntos: List<AdjuntoNota>
    ): ResultadoComando? = try {
        val envio = construirEnvio(contenido, adjuntos)
            .setPackage(paquete)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(envio)
        memoria.registrarHabito(CONTEXTO_DESTINO, PREFIJO_APP + paquete)
        ultima = UltimaNota(contenido, adjuntos, null, etiqueta)
        ResultadoComando(true, "Listo, envié ${describir(contenido, adjuntos)} a $etiqueta.")
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo enviar a $paquete", e)
        null
    }

    /** Selector del sistema mientras Iris aprende qué app prefieres. */
    private fun abrirSelector(contenido: String, adjuntos: List<AdjuntoNota>): ResultadoComando = try {
        val aviso = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, EleccionAppReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val selector = Intent.createChooser(construirEnvio(contenido, adjuntos), "Guardar nota con…", aviso.intentSender)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(selector)
        ultima = UltimaNota(contenido, adjuntos, null, null)
        ResultadoComando(
            true,
            "Elige la app donde guardar la nota, o dime «guárdalo en mi cuaderno». Iré aprendiendo tu preferencia."
        )
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo abrir el selector", e)
        ResultadoComando(false, "No pude abrir las aplicaciones disponibles para guardar la nota.")
    }

    private fun construirEnvio(contenido: String, adjuntos: List<AdjuntoNota>): Intent {
        val compartibles = adjuntos.mapNotNull { a -> uriCompartible(a.uri)?.let { a to it } }
        val intent = when (compartibles.size) {
            0 -> Intent(Intent.ACTION_SEND).setType("text/plain")
            1 -> Intent(Intent.ACTION_SEND)
                .setType(compartibles[0].first.mime ?: "*/*")
                .putExtra(Intent.EXTRA_STREAM, compartibles[0].second)
            else -> Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(if (compartibles.all { it.first.esImagen }) "image/*" else "*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(compartibles.map { it.second }))
        }
        if (contenido.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, contenido)
        if (compartibles.isNotEmpty()) {
            val clip = ClipData.newRawUri("", compartibles[0].second)
            compartibles.drop(1).forEach { clip.addItem(ClipData.Item(it.second)) }
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent
    }

    /** Los adjuntos viven en archivos privados de Iris: se comparten con FileProvider. */
    private fun uriCompartible(uri: String): Uri? = try {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") {
            parsed.path?.let {
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.archivos", File(it))
            }
        } else {
            parsed
        }
    } catch (e: Exception) {
        null
    }

    private fun resolverApp(nombre: String): AppDestino? {
        val pm = appContext.packageManager
        val consulta = Intent(Intent.ACTION_SEND).setType("text/plain")
        @Suppress("DEPRECATION")
        val actividades = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(consulta, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(consulta, 0)
        }
        val candidatas = actividades
            .map { AppDestino(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.paquete != appContext.packageName }
            .distinctBy { it.paquete }
        val buscado = normalizar(nombre).trim()

        // El nombre visible de Keep cambia según versión e idioma; su paquete no.
        if ("keep" in buscado) candidatas.firstOrNull { it.paquete == PAQUETE_KEEP }?.let { return it }

        return candidatas.firstOrNull { normalizar(it.etiqueta) == buscado }
            ?: candidatas.firstOrNull { normalizar(it.etiqueta).startsWith(buscado) }
            ?: candidatas.firstOrNull { buscado.length >= 4 && buscado in normalizar(it.etiqueta) }
    }

    private fun describir(contenido: String, adjuntos: List<AdjuntoNota>): String {
        val partes = mutableListOf<String>()
        if (contenido.isNotBlank()) {
            partes += "«" + (if (contenido.length > 70) contenido.take(70).trimEnd() + "…" else contenido) + "»"
        }
        val imagenes = adjuntos.count { it.esImagen }
        val archivos = adjuntos.size - imagenes
        if (imagenes > 0) partes += if (imagenes == 1) "1 imagen" else "$imagenes imágenes"
        if (archivos > 0) partes += if (archivos == 1) "1 archivo" else "$archivos archivos"
        return partes.joinToString(" y ")
    }

    companion object {
        private const val TAG = "IrisNotas"
        const val CONTEXTO_DESTINO = "destino_nota"
        const val OPCION_CUADERNO = "cuaderno"
        const val PREFIJO_APP = "app:"
        private const val PAQUETE_KEEP = "com.google.android.keep"

        /** Compartida entre la pantalla y el servicio de voz (cada uno tiene su procesador). */
        @Volatile var ultima: UltimaNota? = null
            private set

        fun contextoTema(tema: String) = "tema:$tema"

        /** Lo llama [EleccionAppReceiver] cuando el usuario elige una app en el selector. */
        fun registrarEleccion(context: Context, paquete: String) {
            if (paquete == context.packageName) return
            MemoriaLocal.get(context).registrarHabito(CONTEXTO_DESTINO, PREFIJO_APP + paquete)
            ultima = ultima?.copy(app = etiquetaDe(context, paquete) ?: paquete)
        }

        private fun etiquetaDe(context: Context, paquete: String): String? = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
        } catch (e: Exception) {
            null
        }
    }
}
