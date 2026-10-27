package com.miasistente.app.comandos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Nota(val texto: String, val fechaMs: Long)

/**
 * Notas guardadas dentro de la app (archivo JSON privado, sin internet).
 * Más adelante se podrá mostrar la lista en una pantalla; por ahora solo se crean.
 */
class NotasRepositorio(context: Context) {

    private val archivo = File(context.applicationContext.filesDir, "notas.json")

    @Synchronized
    fun listar(): List<Nota> {
        if (!archivo.exists()) return emptyList()
        return try {
            val arreglo = JSONArray(archivo.readText())
            (0 until arreglo.length()).map {
                val o = arreglo.getJSONObject(it)
                Nota(o.getString("texto"), o.optLong("fecha"))
            }
        } catch (e: Exception) {
            emptyList() // archivo dañado: se empieza de cero en vez de fallar
        }
    }

    @Synchronized
    fun agregar(texto: String) {
        val arreglo = JSONArray()
        listar().forEach {
            arreglo.put(JSONObject().put("texto", it.texto).put("fecha", it.fechaMs))
        }
        arreglo.put(JSONObject().put("texto", texto).put("fecha", System.currentTimeMillis()))
        archivo.writeText(arreglo.toString())
    }
}
