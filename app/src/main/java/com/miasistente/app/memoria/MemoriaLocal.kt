package com.miasistente.app.memoria

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class Cuaderno(
    val id: Long,
    val titulo: String,
    val cuerpo: String,
    val papel: String,
    val actualizado: Long
)

data class EntradaCuaderno(
    val id: Long,
    val cuadernoId: Long,
    val tipo: String,
    val texto: String,
    val uri: String?,
    val mime: String?,
    val nombre: String?,
    val creado: Long
)

/**
 * Memoria local de Iris: 100 % sin internet.
 *
 * - Lecturas frecuentes (lista de cuadernos, hábitos) salen de una caché en RAM.
 * - Todas las escrituras van a un único hilo propio, en orden, sin tocar el hilo de la pantalla.
 * - [registrarHabito] no espera: actualiza en segundo plano y no añade latencia.
 * - Provee contexto compacto del usuario para inyectar en el systemPrompt del modelo local.
 */
class MemoriaLocal private constructor(context: Context) {

    private val db = IrisDatabase(context)
    private val ambito = CoroutineScope(
        SupervisorJob() + HILO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Error en memoria local", e) }
    )

    @Volatile private var cacheCuadernos: List<Cuaderno>? = null
    private val cacheHabitos = ConcurrentHashMap<String, List<PesoHabito>>()

    // --- Cuadernos -------------------------------------------------------------------------

    suspend fun cuadernos(): List<Cuaderno> =
        cacheCuadernos ?: withContext(HILO) { cacheCuadernos ?: leerCuadernos().also { cacheCuadernos = it } }

    suspend fun crearCuaderno(titulo: String, papel: String = "Líneas"): Cuaderno = withContext(HILO) {
        val ahora = System.currentTimeMillis()
        val limpio = titulo.trim().ifBlank { "Notas" }.take(60)
        val id = db.writableDatabase.insertOrThrow(
            "cuadernos",
            null,
            ContentValues().apply {
                put("titulo", limpio)
                put("titulo_norm", ClasificadorCuadernos.normalizarTexto(limpio))
                put("cuerpo", "")
                put("papel", papel)
                put("creado", ahora)
                put("actualizado", ahora)
            }
        )
        cacheCuadernos = null
        Cuaderno(id, limpio, "", papel, ahora)
    }

    suspend fun actualizarCuaderno(id: Long, titulo: String, cuerpo: String, papel: String) = withContext(HILO) {
        db.writableDatabase.update(
            "cuadernos",
            ContentValues().apply {
                put("titulo", titulo)
                put("titulo_norm", ClasificadorCuadernos.normalizarTexto(titulo))
                put("cuerpo", cuerpo)
                put("papel", papel)
                put("actualizado", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(id.toString())
        )
        cacheCuadernos = null
    }

    suspend fun eliminarCuaderno(id: Long) = withContext(HILO) {
        transaccion { w ->
            w.execSQL(
                "DELETE FROM entradas_fts WHERE rowid IN (SELECT id FROM entradas WHERE cuaderno_id = ?)",
                arrayOf<Any>(id)
            )
            w.delete("entradas", "cuaderno_id = ?", arrayOf(id.toString()))
            w.delete("cuadernos", "id = ?", arrayOf(id.toString()))
        }
        cacheCuadernos = null
        cacheHabitos.clear()
        db.writableDatabase.delete("habitos", "opcion = ? AND contexto LIKE 'tema:%'", arrayOf(id.toString()))
    }

    /** Cuaderno por nombre: igual, o empieza igual ("receta" → "Recetas"). */
    suspend fun buscarCuadernoPorNombre(nombre: String): Cuaderno? {
        val buscado = ClasificadorCuadernos.normalizarTexto(nombre).trim()
        if (buscado.isEmpty()) return null
        val lista = cuadernos()
        fun norm(c: Cuaderno) = ClasificadorCuadernos.normalizarTexto(c.titulo).trim()
        return lista.firstOrNull { norm(it) == buscado }
            ?: lista.firstOrNull { buscado.length >= 4 && norm(it).startsWith(buscado) }
            ?: lista.firstOrNull { norm(it) == buscado.removeSuffix("s") || norm(it).removeSuffix("s") == buscado }
    }

    suspend fun agregarEntrada(
        cuadernoId: Long,
        tipo: String,
        texto: String = "",
        uri: String? = null,
        mime: String? = null,
        nombre: String? = null
    ): Long = withContext(HILO) {
        val ahora = System.currentTimeMillis()
        val id = transaccion { w ->
            val nuevo = w.insertOrThrow(
                "entradas",
                null,
                ContentValues().apply {
                    put("cuaderno_id", cuadernoId)
                    put("tipo", tipo)
                    put("texto", texto)
                    put("uri", uri)
                    put("mime", mime)
                    put("nombre", nombre)
                    put("creado", ahora)
                }
            )
            val indexable = listOfNotNull(texto.takeIf { it.isNotBlank() }, nombre).joinToString(" ")
            if (indexable.isNotBlank()) {
                w.execSQL(
                    "INSERT INTO entradas_fts(rowid, texto) VALUES (?, ?)",
                    arrayOf<Any>(nuevo, ClasificadorCuadernos.normalizarTexto(indexable))
                )
            }
            if (tipo == TIPO_TEXTO && texto.isNotBlank()) {
                w.execSQL(
                    "UPDATE cuadernos SET cuerpo = CASE WHEN cuerpo = '' THEN ? ELSE cuerpo || ? END, " +
                        "actualizado = ? WHERE id = ?",
                    arrayOf<Any>("• $texto", "\n• $texto", ahora, cuadernoId)
                )
            } else {
                w.execSQL("UPDATE cuadernos SET actualizado = ? WHERE id = ?", arrayOf<Any>(ahora, cuadernoId))
            }
            nuevo
        }
        cacheCuadernos = null
        id
    }

    suspend fun adjuntos(cuadernoId: Long): List<EntradaCuaderno> = withContext(HILO) {
        db.readableDatabase.rawQuery(
            "SELECT id, cuaderno_id, tipo, texto, uri, mime, nombre, creado FROM entradas " +
                "WHERE cuaderno_id = ? AND tipo IN (?, ?) ORDER BY creado",
            arrayOf(cuadernoId.toString(), TIPO_IMAGEN, TIPO_ARCHIVO)
        ).use { c ->
            val lista = mutableListOf<EntradaCuaderno>()
            while (c.moveToNext()) {
                lista.add(
                    EntradaCuaderno(
                        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                        c.getStringOrNull(4), c.getStringOrNull(5), c.getStringOrNull(6), c.getLong(7)
                    )
                )
            }
            lista
        }
    }

    suspend fun resumenes(): List<CuadernoResumen> = withContext(HILO) {
        db.readableDatabase.rawQuery(
            "SELECT c.id, c.titulo, substr(c.cuerpo, -600) || ' ' || IFNULL((SELECT group_concat(texto, ' ') FROM " +
                "(SELECT e.texto AS texto FROM entradas e WHERE e.cuaderno_id = c.id ORDER BY e.creado DESC LIMIT 10)), '') " +
                "FROM cuadernos c",
            null
        ).use { c ->
            val lista = mutableListOf<CuadernoResumen>()
            while (c.moveToNext()) lista.add(CuadernoResumen(c.getLong(0), c.getString(1), c.getString(2).orEmpty()))
            lista
        }
    }

    // --- Memoria esencial (hechos, preferencias y memoria importada) -----------------------

    suspend fun recordar(valor: String, tipo: String = TIPO_HECHO) = withContext(HILO) {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return@withContext
        transaccion { w ->
            insertarSiNoExiste(w, limpio, tipo, System.currentTimeMillis())
        }
    }

    /**
     * Importa en lote múltiples hechos o fragmentos de contexto del usuario dentro de una sola
     * transacción SQLite + FTS4, omitiendo duplicados. Devuelve la cantidad de elementos nuevos.
     */
    suspend fun importarHechos(valores: List<String>, tipo: String = TIPO_IMPORTADO): Int = withContext(HILO) {
        if (valores.isEmpty()) return@withContext 0
        var insertados = 0
        val baseTiempo = System.currentTimeMillis()
        transaccion { w ->
            valores.forEachIndexed { idx, raw ->
                val limpio = raw.trim()
                if (limpio.isNotEmpty() && insertarSiNoExiste(w, limpio, tipo, baseTiempo + idx)) {
                    insertados++
                }
            }
        }
        insertados
    }

    private fun insertarSiNoExiste(
        w: SQLiteDatabase,
        valor: String,
        tipo: String,
        timestamp: Long
    ): Boolean {
        val norm = ClasificadorCuadernos.normalizarTexto(valor).trim()
        if (norm.isEmpty()) return false
        val yaExiste = w.rawQuery(
            "SELECT id FROM memoria WHERE lower(trim(valor)) = lower(trim(?)) LIMIT 1",
            arrayOf(valor)
        ).use { it.moveToFirst() }
        if (yaExiste) return false

        val id = w.insertOrThrow(
            "memoria",
            null,
            ContentValues().apply {
                put("tipo", tipo)
                put("valor", valor)
                put("creado", timestamp)
            }
        )
        w.execSQL(
            "INSERT INTO memoria_fts(rowid, texto) VALUES (?, ?)",
            arrayOf<Any>(id, norm)
        )
        return true
    }

    suspend fun hechos(limite: Int = 10): List<String> = withContext(HILO) {
        db.readableDatabase.rawQuery(
            "SELECT valor FROM memoria WHERE tipo IN (?, ?) ORDER BY creado DESC LIMIT ?",
            arrayOf(TIPO_HECHO, TIPO_IMPORTADO, limite.toString())
        ).use { c ->
            val lista = mutableListOf<String>()
            while (c.moveToNext()) lista.add(c.getString(0))
            lista
        }
    }

    /** Búsqueda de texto completo en la memoria y en los cuadernos. */
    suspend fun buscar(consulta: String, limite: Int = 6): List<String> = withContext(HILO) {
        val expresion = expresionFts(consulta) ?: return@withContext emptyList()
        val resultados = mutableListOf<String>()
        db.readableDatabase.rawQuery(
            "SELECT m.valor FROM memoria_fts f JOIN memoria m ON m.id = f.rowid " +
                "WHERE memoria_fts MATCH ? ORDER BY m.creado DESC LIMIT ?",
            arrayOf(expresion, limite.toString())
        ).use { c -> while (c.moveToNext()) resultados.add(c.getString(0)) }
        db.readableDatabase.rawQuery(
            "SELECT e.texto, e.nombre, c.titulo FROM entradas_fts f JOIN entradas e ON e.id = f.rowid " +
                "JOIN cuadernos c ON c.id = e.cuaderno_id WHERE entradas_fts MATCH ? ORDER BY e.creado DESC LIMIT ?",
            arrayOf(expresion, limite.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val texto = c.getString(0).orEmpty().ifBlank { c.getStringOrNull(1).orEmpty() }
                if (texto.isNotBlank()) resultados.add("$texto (cuaderno «${c.getString(2)}»)")
            }
        }
        resultados.take(limite)
    }

    /**
     * Construye un resumen compacto de la memoria del usuario (hechos relevantes por FTS +
     * recuerdos recientes + nombre) para inyectarlo en el systemPrompt del modelo local (LiteRT).
     */
    suspend fun construirContextoUsuario(
        consulta: String = "",
        nombreUsuario: String? = null,
        maxCaracteres: Int = 650
    ): String = withContext(HILO) {
        val seleccionados = LinkedHashSet<String>()

        nombreUsuario?.trim()?.takeIf { it.isNotEmpty() }?.let {
            seleccionados.add("El usuario se llama $it.")
        }

        // 1. Coincidencias semánticas/FTS con la pregunta actual
        val expr = expresionFts(consulta)
        if (expr != null) {
            runCatching {
                db.readableDatabase.rawQuery(
                    "SELECT m.valor FROM memoria_fts f JOIN memoria m ON m.id = f.rowid " +
                        "WHERE memoria_fts MATCH ? ORDER BY m.creado DESC LIMIT 4",
                    arrayOf(expr)
                ).use { c ->
                    while (c.moveToNext()) seleccionados.add(c.getString(0))
                }
            }
        }

        // 2. Hechos recientes e importados para que el LLM local conozca al usuario
        db.readableDatabase.rawQuery(
            "SELECT valor FROM memoria WHERE tipo IN (?, ?) ORDER BY creado DESC LIMIT 8",
            arrayOf(TIPO_HECHO, TIPO_IMPORTADO)
        ).use { c ->
            while (c.moveToNext() && seleccionados.size < 8) {
                seleccionados.add(c.getString(0))
            }
        }

        if (seleccionados.isEmpty()) return@withContext ""

        val sb = StringBuilder()
        for (item in seleccionados) {
            val linea = "- ${item.take(140)}\n"
            if (sb.length + linea.length > maxCaracteres) break
            sb.append(linea)
        }
        sb.toString().trim()
    }

    // --- Hábitos (aprendizaje progresivo) --------------------------------------------------

    suspend fun pesosHabito(contexto: String): List<PesoHabito> =
        cacheHabitos[contexto] ?: withContext(HILO) {
            cacheHabitos[contexto] ?: leerHabitos(contexto).also { cacheHabitos[contexto] = it }
        }

    fun registrarHabito(contexto: String, opcion: String) {
        ambito.launch {
            val actuales = cacheHabitos[contexto] ?: leerHabitos(contexto)
            val nuevos = AprendizajeHabitos.actualizar(actuales, opcion)
            cacheHabitos[contexto] = nuevos
            val ahora = System.currentTimeMillis()
            transaccion { w ->
                nuevos.forEach {
                    w.execSQL(
                        "INSERT OR REPLACE INTO habitos(contexto, opcion, peso, usos, actualizado) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any>(contexto, it.opcion, it.peso, it.usos, ahora)
                    )
                }
            }
        }
    }

    // --- Internos ----------------------------------------------------------------------------

    private fun leerCuadernos(): List<Cuaderno> =
        db.readableDatabase.rawQuery(
            "SELECT id, titulo, cuerpo, papel, actualizado FROM cuadernos ORDER BY id",
            null
        ).use { c ->
            val lista = mutableListOf<Cuaderno>()
            while (c.moveToNext()) {
                lista.add(Cuaderno(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4)))
            }
            lista
        }

    private fun leerHabitos(contexto: String): List<PesoHabito> =
        db.readableDatabase.rawQuery(
            "SELECT opcion, peso, usos FROM habitos WHERE contexto = ?",
            arrayOf(contexto)
        ).use { c ->
            val lista = mutableListOf<PesoHabito>()
            while (c.moveToNext()) lista.add(PesoHabito(c.getString(0), c.getDouble(1), c.getInt(2)))
            lista
        }

    private inline fun <T> transaccion(bloque: (SQLiteDatabase) -> T): T {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            val r = bloque(w)
            w.setTransactionSuccessful()
            return r
        } finally {
            w.endTransaction()
        }
    }

    private fun android.database.Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    companion object {
        private const val TAG = "IrisMemoria"

        const val TIPO_TEXTO = "texto"
        const val TIPO_IMAGEN = "imagen"
        const val TIPO_ARCHIVO = "archivo"
        const val TIPO_HECHO = "hecho"
        const val TIPO_IMPORTADO = "importado"

        private val HILO = Executors.newSingleThreadExecutor { r ->
            Thread(r, "iris-memoria").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        @Volatile private var instancia: MemoriaLocal? = null

        fun get(context: Context): MemoriaLocal =
            instancia ?: synchronized(this) {
                instancia ?: MemoriaLocal(context.applicationContext).also { instancia = it }
            }

        /** "mi color favorito" → "color* favorito*" (prefijos, solo letras y números). */
        fun expresionFts(consulta: String): String? {
            val palabras = ClasificadorCuadernos.palabras(consulta)
                .map { p -> p.filter { it.isLetterOrDigit() } }
                .filter { it.length >= 2 }
                .take(6)
            return if (palabras.isEmpty()) null else palabras.joinToString(" ") { "$it*" }
        }
    }
}
