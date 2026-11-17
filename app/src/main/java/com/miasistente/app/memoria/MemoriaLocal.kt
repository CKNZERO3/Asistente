package com.miasistente.app.memoria

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.miasistente.app.data.PerfilUsuario
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
    val actualizado: Long,
    val fondo: String = "Ninguno",
    val fijado: Boolean = false,
    val bloqueado: Boolean = false
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

data class RecuerdoItem(
    val id: Long,
    val valor: String,
    val creado: Long
)

class MemoriaLocal private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = IrisDatabase(appContext)
    private val ambito = CoroutineScope(
        SupervisorJob() + HILO + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Error en memoria local", e) }
    )

    @Volatile private var cacheCuadernos: List<Cuaderno>? = null
    private val cacheHabitos = ConcurrentHashMap<String, List<PesoHabito>>()

    // --- Cuadernos -------------------------------------------------------------------------

    suspend fun cuadernos(): List<Cuaderno> =
        cacheCuadernos ?: withContext(HILO) { cacheCuadernos ?: leerCuadernos().also { cacheCuadernos = it } }

    suspend fun crearCuaderno(
        titulo: String,
        papel: String = "Líneas",
        fondo: String = "Ninguno"
    ): Cuaderno = withContext(HILO) {
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
                put("fondo", fondo)
                put("fijado", 0)
                put("bloqueado", 0)
                put("creado", ahora)
                put("actualizado", ahora)
            }
        )
        cacheCuadernos = null
        Cuaderno(id, limpio, "", papel, ahora, fondo, fijado = false, bloqueado = false)
    }

    suspend fun actualizarCuaderno(
        id: Long,
        titulo: String,
        cuerpo: String,
        papel: String,
        fondo: String = "Ninguno",
        fijado: Boolean = false,
        bloqueado: Boolean = false
    ) = withContext(HILO) {
        db.writableDatabase.update(
            "cuadernos",
            ContentValues().apply {
                put("titulo", titulo)
                put("titulo_norm", ClasificadorCuadernos.normalizarTexto(titulo))
                put("cuerpo", cuerpo)
                put("papel", papel)
                put("fondo", fondo)
                put("fijado", if (fijado) 1 else 0)
                put("bloqueado", if (bloqueado) 1 else 0)
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
                val bloque = if (texto.contains('\n')) texto else "• $texto"
                val separador = if (texto.contains('\n')) "\n\n$texto" else "\n• $texto"
                w.execSQL(
                    "UPDATE cuadernos SET cuerpo = CASE WHEN cuerpo = '' THEN ? ELSE cuerpo || ? END, " +
                        "actualizado = ? WHERE id = ?",
                    arrayOf<Any>(bloque, separador, ahora, cuadernoId)
                )
            } else {
                w.execSQL("UPDATE cuadernos SET actualizado = ? WHERE id = ?", arrayOf<Any>(ahora, cuadernoId))
            }
            nuevo
        }
        cacheCuadernos = null
        id
    }

    suspend fun eliminarEntrada(entradaId: Long) = withContext(HILO) {
        transaccion { w ->
            w.execSQL("DELETE FROM entradas_fts WHERE rowid = ?", arrayOf<Any>(entradaId))
            w.delete("entradas", "id = ?", arrayOf(entradaId.toString()))
        }
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

    // --- Memoria esencial (hechos, importación e inyección en IA local) ---------------------

    suspend fun recordar(valor: String, tipo: String = TIPO_HECHO) = withContext(HILO) {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return@withContext
        transaccion { w ->
            val id = w.insertOrThrow(
                "memoria",
                null,
                ContentValues().apply {
                    put("tipo", tipo)
                    put("valor", limpio)
                    put("creado", System.currentTimeMillis())
                }
            )
            w.execSQL(
                "INSERT INTO memoria_fts(rowid, texto) VALUES (?, ?)",
                arrayOf<Any>(id, ClasificadorCuadernos.normalizarTexto(limpio))
            )
        }
    }

    /** Inserta múltiples recuerdos deduplicados en una sola transacción (Sprint 2: Importar memoria). */
    suspend fun recordarLote(valores: List<String>, tipo: String = TIPO_HECHO): Int = withContext(HILO) {
        if (valores.isEmpty()) return@withContext 0
        val existentes = HashSet<String>()
        db.readableDatabase.rawQuery(
            "SELECT valor FROM memoria WHERE tipo = ?",
            arrayOf(tipo)
        ).use { c ->
            while (c.moveToNext()) {
                existentes.add(ClasificadorCuadernos.normalizarTexto(c.getString(0)).trim())
            }
        }

        var insertados = 0
        val ahora = System.currentTimeMillis()
        transaccion { w ->
            for (raw in valores) {
                val limpio = raw.trim().take(400)
                if (limpio.length < 3) continue
                val norm = ClasificadorCuadernos.normalizarTexto(limpio).trim()
                if (!existentes.add(norm)) continue
                val id = w.insertOrThrow(
                    "memoria",
                    null,
                    ContentValues().apply {
                        put("tipo", tipo)
                        put("valor", limpio)
                        put("creado", ahora + insertados)
                    }
                )
                w.execSQL(
                    "INSERT INTO memoria_fts(rowid, texto) VALUES (?, ?)",
                    arrayOf<Any>(id, norm)
                )
                insertados++
            }
        }
        insertados
    }

    suspend fun hechos(limite: Int = 10): List<String> = withContext(HILO) {
        db.readableDatabase.rawQuery(
            "SELECT valor FROM memoria WHERE tipo = ? ORDER BY creado DESC LIMIT ?",
            arrayOf(TIPO_HECHO, limite.toString())
        ).use { c ->
            val lista = mutableListOf<String>()
            while (c.moveToNext()) lista.add(c.getString(0))
            lista
        }
    }

    suspend fun listarRecuerdos(limite: Int = 60): List<RecuerdoItem> = withContext(HILO) {
        db.readableDatabase.rawQuery(
            "SELECT id, valor, creado FROM memoria WHERE tipo = ? ORDER BY creado DESC LIMIT ?",
            arrayOf(TIPO_HECHO, limite.toString())
        ).use { c ->
            val lista = mutableListOf<RecuerdoItem>()
            while (c.moveToNext()) {
                lista.add(RecuerdoItem(c.getLong(0), c.getString(1), c.getLong(2)))
            }
            lista
        }
    }

    suspend fun contarRecuerdos(): Int = withContext(HILO) {
        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM memoria WHERE tipo = ?",
            arrayOf(TIPO_HECHO)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun eliminarRecuerdo(id: Long) = withContext(HILO) {
        transaccion { w ->
            w.execSQL("DELETE FROM memoria_fts WHERE rowid = ?", arrayOf<Any>(id))
            w.delete("memoria", "id = ?", arrayOf(id.toString()))
        }
    }

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
     * Construye un bloque compacto de contexto personal (perfil + recuerdos relevantes)
     * para inyectarlo en el prompt del modelo local (Gemma 3 1B) sin gastar cuota de red.
     */
    suspend fun construirContextoUsuario(consulta: String, maxCaracteres: Int = 450): String {
        val nombre = runCatching { PerfilUsuario(appContext).nombre.trim() }.getOrDefault("")
        val relevantes = buscar(consulta, limite = 4)
        val recientes = hechos(limite = 4)
        val combinados = (relevantes + recientes).distinct().take(6)

        if (nombre.isEmpty() && combinados.isEmpty()) return ""

        val sb = StringBuilder("\n\nContexto del usuario (úsalos solo si aportan a la respuesta):")
        if (nombre.isNotEmpty()) sb.append("\n- Nombre del usuario: $nombre")
        for (item in combinados) {
            val linea = "\n- $item"
            if (sb.length + linea.length > maxCaracteres) break
            sb.append(linea)
        }
        return sb.toString()
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
            "SELECT id, titulo, cuerpo, papel, actualizado, fondo, fijado, bloqueado " +
                "FROM cuadernos ORDER BY fijado DESC, actualizado DESC, id ASC",
            null
        ).use { c ->
            val lista = mutableListOf<Cuaderno>()
            while (c.moveToNext()) {
                lista.add(
                    Cuaderno(
                        id = c.getLong(0),
                        titulo = c.getString(1),
                        cuerpo = c.getString(2),
                        papel = c.getString(3),
                        actualizado = c.getLong(4),
                        fondo = c.getStringOrNull(5) ?: "Ninguno",
                        fijado = c.getInt(6) == 1,
                        bloqueado = c.getInt(7) == 1
                    )
                )
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

        private val HILO = Executors.newSingleThreadExecutor { r ->
            Thread(r, "iris-memoria").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        @Volatile private var instancia: MemoriaLocal? = null

        fun get(context: Context): MemoriaLocal =
            instancia ?: synchronized(this) {
                instancia ?: MemoriaLocal(context.applicationContext).also { instancia = it }
            }

        fun expresionFts(consulta: String): String? {
            val palabras = ClasificadorCuadernos.palabras(consulta)
                .map { p -> p.filter { it.isLetterOrDigit() } }
                .filter { it.length >= 2 }
                .take(6)
            return if (palabras.isEmpty()) null else palabras.joinToString(" ") { "$it*" }
        }
    }
}
