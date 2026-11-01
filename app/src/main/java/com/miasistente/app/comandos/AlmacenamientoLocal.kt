package com.miasistente.app.comandos

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter

object AlmacenamientoLocal {

    fun guardarArchivoEnDocumentos(nombreArchivo: String, contenido: String): Boolean {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val archivo = File(dir, nombreArchivo)
            FileWriter(archivo).use { it.write(contenido) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun leerArchivoDeDocumentos(nombreArchivo: String): String? {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val archivo = File(dir, nombreArchivo)
            if (archivo.exists()) archivo.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
