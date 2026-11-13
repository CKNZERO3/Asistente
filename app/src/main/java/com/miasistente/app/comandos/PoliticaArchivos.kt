package com.miasistente.app.comandos

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Resultado de intentar leer un archivo adjunto para resumirlo. */
sealed interface LecturaArchivo {
    data class Texto(val contenido: String) : LecturaArchivo
    data class Rechazado(val motivo: String) : LecturaArchivo
}

/**
 * Reglas para resumir archivos: solo texto y con límites de tamaño, para no saturar el
 * modelo local (contexto corto) ni gastar la cuota de Gemini.
 */
object PoliticaArchivos {
    const val MAX_BYTES_LECTURA = 200L * 1024
    const val MAX_CARACTERES_LOCAL = 4_000
    const val MAX_CARACTERES_NUBE = 20_000

    private val EXTENSIONES = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "log", "xml", "html", "htm", "yml", "yaml", "ini", "srt"
    )
    private val MIMES_TEXTO = setOf("application/json", "application/xml", "text/csv")

    private val PIDE_RESUMEN = Regex(
        """\b(resume|resumeme|resumir|resumen|sintetiza|sintetizar)\b.*\b(archivo|documento|adjunto|texto|nota|fichero)\b|""" +
            """\b(archivo|documento|adjunto|fichero)\b.*\b(resume|resumir|resumen)\b"""
    )

    fun esTexto(nombre: String, mime: String?): Boolean {
        val extension = nombre.substringAfterLast('.', "").lowercase()
        return extension in EXTENSIONES || mime?.startsWith("text/") == true || mime in MIMES_TEXTO
    }

    /** ¿La frase pide resumir un archivo adjunto? (frases cortas, no texto pegado). */
    fun pideResumirArchivo(texto: String): Boolean =
        texto.length <= 200 && PIDE_RESUMEN.containsMatchIn(normalizar(texto))

    /** Decide por nombre y tamaño si vale la pena leer el archivo. Null = se puede leer. */
    fun motivoDeRechazo(nombre: String, mime: String?, tamano: Long?): String? = when {
        !esTexto(nombre, mime) ->
            "Por ahora solo puedo resumir archivos de texto (.txt, .md, .csv, .json…). «$nombre» no lo es."
        tamano != null && tamano > MAX_BYTES_LECTURA ->
            "«$nombre» pesa demasiado para resumirlo (límite: 200 KB)."
        else -> null
    }

    fun leer(resolver: ContentResolver, uri: Uri, nombre: String, mime: String?, tamano: Long?): LecturaArchivo {
        motivoDeRechazo(nombre, mime, tamano)?.let { return LecturaArchivo.Rechazado(it) }
        return try {
            val bytes = resolver.openInputStream(uri)?.use { leerLimitado(it, MAX_BYTES_LECTURA.toInt() + 1) }
                ?: return LecturaArchivo.Rechazado("No pude abrir «$nombre».")
            if (bytes.size > MAX_BYTES_LECTURA) {
                return LecturaArchivo.Rechazado("«$nombre» pesa demasiado para resumirlo (límite: 200 KB).")
            }
            val texto = String(bytes, Charsets.UTF_8)
            val ilegibles = texto.count { it == '\uFFFD' }
            if (ilegibles > texto.length / 20) {
                LecturaArchivo.Rechazado("No pude leer «$nombre» como texto.")
            } else {
                LecturaArchivo.Texto(texto)
            }
        } catch (e: Exception) {
            LecturaArchivo.Rechazado("No pude leer «$nombre».")
        }
    }

    /** Lee como máximo [limite] bytes (readNBytes no existe en Android antes de la API 33). */
    private fun leerLimitado(entrada: InputStream, limite: Int): ByteArray {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limite) {
            val leidos = entrada.read(buffer, 0, minOf(buffer.size, limite - total))
            if (leidos < 0) break
            salida.write(buffer, 0, leidos)
            total += leidos
        }
        return salida.toByteArray()
    }
}
