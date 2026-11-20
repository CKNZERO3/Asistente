package com.miasistente.app.memoria

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.miasistente.app.comandos.LecturaArchivo
import com.miasistente.app.comandos.PoliticaArchivos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Resultado de importar un archivo de memoria del usuario. */
data class ResultadoImportacionMemoria(
    val exito: Boolean,
    val nuevos: Int,
    val mensaje: String
)

/**
 * Parsea archivos de contexto/memoria del usuario (.txt, .md, .json, .csv) e indexa sus hechos
 * en [MemoriaLocal] (SQLite + FTS4) para enriquecer al modelo local (Gemma 3 1B) sin internet.
 */
class ImportadorMemoria(context: Context) {

    private val appContext = context.applicationContext
    private val memoria = MemoriaLocal.get(appContext)

    suspend fun importarDesdeUri(uri: Uri): ResultadoImportacionMemoria = withContext(Dispatchers.IO) {
        val (nombre, tamano) = metadatos(uri)
        val mime = appContext.contentResolver.getType(uri)
        val lectura = PoliticaArchivos.leer(appContext.contentResolver, uri, nombre, mime, tamano)
        when (lectura) {
            is LecturaArchivo.Rechazado -> ResultadoImportacionMemoria(false, 0, lectura.motivo)
            is LecturaArchivo.Texto -> importarTexto(lectura.contenido, nombre)
        }
    }

    suspend fun importarTexto(contenido: String, nombreArchivo: String = "memoria.txt"): ResultadoImportacionMemoria {
        val hechos = parsearContenido(contenido, nombreArchivo)
        if (hechos.isEmpty()) {
            return ResultadoImportacionMemoria(
                exito = false,
                nuevos = 0,
                mensaje = "No encontré datos o recuerdos utilizables en «$nombreArchivo»."
            )
        }
        val insertados = memoria.importarHechos(hechos)
        return if (insertados > 0) {
            ResultadoImportacionMemoria(
                exito = true,
                nuevos = insertados,
                mensaje = "Se importaron $insertados recuerdos a la memoria local de Iris."
            )
        } else {
            ResultadoImportacionMemoria(
                exito = true,
                nuevos = 0,
                mensaje = "Los datos de «$nombreArchivo» ya estaban guardados en la memoria de Iris."
            )
        }
    }

    private fun metadatos(uri: Uri): Pair<String, Long?> {
        var nombre = uri.lastPathSegment?.substringAfterLast('/') ?: "memoria.txt"
        var tamano: Long? = null
        runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val iNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (iNombre >= 0) cursor.getString(iNombre)?.let { nombre = it }
                    val iTamano = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (iTamano >= 0 && !cursor.isNull(iTamano)) tamano = cursor.getLong(iTamano)
                }
            }
        }
        return nombre to tamano
    }

    companion object {
        private const val MAX_ITEMS = 120
        private const val MAX_LARGO_ITEM = 260

        /** Extrae hechos limpios desde JSON, Markdown, CSV o texto plano. */
        fun parsearContenido(raw: String, nombreArchivo: String = ""): List<String> {
            val texto = raw.trim()
            if (texto.isEmpty()) return emptyList()

            val ext = nombreArchivo.substringAfterLast('.', "").lowercase()
            if (ext == "json" || texto.startsWith("[") || texto.startsWith("{")) {
                parsearJson(texto)?.let { if (it.isNotEmpty()) return it }
            }

            return texto.lines()
                .map { linea ->
                    linea.trim()
                        .removePrefix("- ")
                        .removePrefix("* ")
                        .removePrefix("• ")
                        .replace(Regex("""^\d+[.)]\s+"""), "")
                        .replace(Regex("""^#+\s+"""), "")
                        .trim()
                }
                .filter { it.length >= 4 && !it.startsWith("---") && !it.startsWith("```") }
                .map { it.take(MAX_LARGO_ITEM) }
                .distinct()
                .take(MAX_ITEMS)
        }

        private fun parsearJson(texto: String): List<String>? = try {
            val salida = mutableListOf<String>()
            when {
                texto.startsWith("[") -> {
                    val arr = JSONArray(texto)
                    for (i in 0 until arr.length()) {
                        extraerDeElementoJson(arr.get(i), salida)
                    }
                }
                texto.startsWith("{") -> {
                    val obj = JSONObject(texto)
                    val clavesContenedor = listOf("memories", "memoria", "hechos", "facts", "items", "datos", "perfil")
                    val claveEncontrada = clavesContenedor.firstOrNull { obj.has(it) }
                    if (claveEncontrada != null) {
                        extraerDeElementoJson(obj.get(claveEncontrada), salida)
                    } else {
                        extraerDeElementoJson(obj, salida)
                    }
                }
            }
            salida.map { it.trim().take(MAX_LARGO_ITEM) }
                .filter { it.length >= 4 }
                .distinct()
                .take(MAX_ITEMS)
        } catch (_: Exception) {
            null
        }

        private fun extraerDeElementoJson(elemento: Any?, destino: MutableList<String>) {
            when (elemento) {
                is String -> destino.add(elemento)
                is JSONArray -> {
                    for (i in 0 until elemento.length()) {
                        extraerDeElementoJson(elemento.get(i), destino)
                    }
                }
                is JSONObject -> {
                    val clavesTexto = listOf("valor", "value", "fact", "hecho", "memory", "memoria", "text", "contenido", "content")
                    val directa = clavesTexto.firstOrNull { elemento.has(it) && elemento.optString(it).isNotBlank() }
                    if (directa != null) {
                        destino.add(elemento.optString(directa))
                    } else {
                        val keys = elemento.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = elemento.opt(k)
                            if (v is String || v is Number || v is Boolean) {
                                val s = v.toString().trim()
                                if (s.isNotEmpty()) destino.add("$k: $s")
                            } else {
                                extraerDeElementoJson(v, destino)
                            }
                        }
                    }
                }
            }
        }
    }
}
