package com.miasistente.app.memoria

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local de Iris (SQLite del propio Android, sin dependencias extra).
 *
 * - cuadernos / entradas: Iris Notebook (texto, imágenes y archivos).
 * - memoria: hechos y preferencias del usuario.
 * - habitos: pesos de aprendizaje progresivo.
 * - entradas_fts / memoria_fts: índices de búsqueda de texto completo (FTS4).
 *
 * Los índices FTS guardan el texto ya normalizado (minúsculas, sin acentos), así la búsqueda
 * no depende del tokenizador que traiga cada versión de Android.
 */
internal class IrisDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NOMBRE, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        ESQUEMA.forEach(db::execSQL)
        // Los dos cuadernos que ya mostraba la pantalla de Iris Notebook.
        val ahora = System.currentTimeMillis()
        listOf("Notas Personales" to "Líneas", "Proyecto de App" to "Puntos").forEach { (titulo, papel) ->
            db.insert(
                "cuadernos",
                null,
                ContentValues().apply {
                    put("titulo", titulo)
                    put("titulo_norm", ClasificadorCuadernos.normalizarTexto(titulo))
                    put("cuerpo", "")
                    put("papel", papel)
                    put("creado", ahora)
                    put("actualizado", ahora)
                }
            )
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, anterior: Int, nueva: Int) {
        // Versión 1: aún no hay migraciones. Cada versión futura añadirá aquí sus pasos
        // sin borrar datos del usuario.
    }

    companion object {
        const val NOMBRE = "iris_memoria.db"
        const val VERSION = 1

        val ESQUEMA = listOf(
            """CREATE TABLE cuadernos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT NOT NULL,
                titulo_norm TEXT NOT NULL,
                cuerpo TEXT NOT NULL DEFAULT '',
                papel TEXT NOT NULL DEFAULT 'Líneas',
                creado INTEGER NOT NULL,
                actualizado INTEGER NOT NULL
            )""",
            "CREATE INDEX idx_cuadernos_titulo ON cuadernos(titulo_norm)",
            """CREATE TABLE entradas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cuaderno_id INTEGER NOT NULL REFERENCES cuadernos(id) ON DELETE CASCADE,
                tipo TEXT NOT NULL,
                texto TEXT NOT NULL DEFAULT '',
                uri TEXT,
                mime TEXT,
                nombre TEXT,
                creado INTEGER NOT NULL
            )""",
            "CREATE INDEX idx_entradas_cuaderno ON entradas(cuaderno_id, creado)",
            "CREATE VIRTUAL TABLE entradas_fts USING fts4(texto)",
            """CREATE TABLE memoria (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL,
                valor TEXT NOT NULL,
                creado INTEGER NOT NULL
            )""",
            "CREATE INDEX idx_memoria_tipo ON memoria(tipo, creado)",
            "CREATE VIRTUAL TABLE memoria_fts USING fts4(texto)",
            """CREATE TABLE habitos (
                contexto TEXT NOT NULL,
                opcion TEXT NOT NULL,
                peso REAL NOT NULL,
                usos INTEGER NOT NULL,
                actualizado INTEGER NOT NULL,
                PRIMARY KEY (contexto, opcion)
            )"""
        )
    }
}
