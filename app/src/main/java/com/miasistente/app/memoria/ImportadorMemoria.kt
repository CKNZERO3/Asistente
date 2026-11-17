package com.miasistente.app.memoria

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ResultadoImportacionMemoria(
    val exito: Boolean,
    val mensaje: String,
    val cantidadImportada: Int = 0
)

object ImportadorMemoria {

    private const val MAX_BYTES = 500 * 1024
    private const val MAX_RECUERDOS_POR_ARCHIVO = 250

    suspend fun importarDesdeUri(context: Context, uri: Uri): ResultadoImportacionMemoria =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                var nombre = uri.lastPathSegment?.substringAfterLast('/') ?: "memoria.txt"
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx)?.let { nombre = it }
                    }
                }

                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8 * 1024)
                    var total = 0
                    while (total <= MAX_BYTES) {
                        val leidos = input.read(buf, 0, minOf(buf.size, MAX_BYTES + 1 - total))
                        if (leidos < 0) break
                        out.write(buf, 0, leidos)
                        total += leidos
                    }
                    out.toByteArray()
                } ?: return@withContext ResultadoImportacionMemoria(false, "No pude abrir el archivo seleccionado.")

                if (bytes.isEmpty()) {
                    return@withContext ResultadoImportacionMemoria(false, "El archivo «$nombre» está vacío.")
                }
                if (bytes.size > MAX_BYTES) {
                    return@withContext ResultadoImportacionMemoria(false, "El archivo supera el límite de 500 KB.")
                }

                val crudo = String(bytes, Charsets.UTF_8)
                if (crudo.count { it == '\uFFFD' } > crudo.length / 15) {
                    return@withContext ResultadoImportacionMemoria(
                        false,
                        "Formato no legible. Usa archivos .txt, .md, .json o .csv."
                    )
                }

                val candidatos = extraerRecuerdos(crudo, nombre).take(MAX_RECUERDOS_POR_ARCHIVO)
                if (candidatos.isEmpty()) {
                    return@withContext ResultadoImportacionMemoria(
                        false,
                        "No encontré recuerdos o notas válidas en «$nombre»."
                    )
                }

                val nuevos = MemoriaLocal.get(context).recordarLote(candidatos)
                val msg = when {
                    nuevos > 0 -> "Se importaron $nuevos recuerdo(s) desde «$nombre»."
                    else -> "Todos los recuerdos de «$nombre» ya estaban guardados en Iris."
                }
                ResultadoImportacionMemoria(true, msg, nuevos)
            } catch (e: Exception) {
                ResultadoImportacionMemoria(false, "No pude importar la memoria: ${e.message ?: "error de lectura"}")
            }
        }

    internal fun extraerRecuerdos(contenido: String, nombreArchivo: String): List<String> {
        val trim = contenido.trim()
        if (nombreArchivo.endsWith(".json", ignoreCase = true) || trim.startsWith("{") || trim.startsWith("[")) {
            val desdeJson = extraerDesdeJson(trim)
            if (desdeJson.isNotEmpty()) return desdeJson
        }
        return extraerDesdeTextoPlano(trim)
    }

    private fun extraerDesdeJson(jsonText: String): List<String> {
        val salida = mutableListOf<String>()
        val clavesTexto = listOf("fact", "hecho", "memory", "memoria", "note", "nota", "content", "text", "valor", "title")

        fun inspeccionar(nodo: Any?) {
            when (nodo) {
                is JSONArray -> {
                    for (i in 0 until nodo.length()) inspeccionar(nodo.opt(i))
                }
                is JSONObject -> {
                    for (clave in clavesTexto) {
                        val valor = nodo.optString(clave, "").trim()
                        if (valor.length in 3..400) salida.add(limpiarLinea(valor))
                    }
                    val keys = nodo.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k !in clavesTexto) {
                            val sub = nodo.opt(k)
                            if (sub is JSONArray || sub is JSONObject) inspeccionar(sub)
                        }
                    }
                }
                is String -> {
                    val s = limpiarLinea(nodo)
                    if (s.length in 3..400) salida.add(s)
                }
            }
        }

        runCatching {
            if (jsonText.startsWith("[")) inspeccionar(JSONArray(jsonText))
            else if (jsonText.startsWith("{")) inspeccionar(JSONObject(jsonText))
        }
        return salida.distinct()
    }

    private fun extraerDesdeTextoPlano(texto: String): List<String> {
        return texto.lines()
            .map { limpiarLinea(it) }
            .filter { it.length in 4..400 }
            .filterNot { it.startsWith("#") && it.length < 6 }
            .distinct()
    }

    private fun limpiarLinea(linea: String): String =
        linea.trim()
            .removePrefix("- ")
            .removePrefix("* ")
            .removePrefix("• ")
            .replace(Regex("""^#+\s*"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
