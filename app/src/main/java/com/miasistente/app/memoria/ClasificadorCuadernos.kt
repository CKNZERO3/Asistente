package com.miasistente.app.memoria

import java.text.Normalizer

/** Lo mínimo de un cuaderno para clasificar: su título y algo de su contenido reciente. */
data class CuadernoResumen(val id: Long, val titulo: String, val textoReciente: String = "")

/**
 * Decide en qué cuaderno va un contenido cuando el usuario no dijo el nombre.
 * Todo es local e instantáneo (diccionario de temas + palabras en común), sin red ni IA.
 */
object ClasificadorCuadernos {

    /** Tema → palabras clave (sin acentos, en minúsculas). El orden desempata. */
    val TEMAS: Map<String, Set<String>> = linkedMapOf(
        "Compras" to setOf(
            "comprar", "compra", "compras", "supermercado", "mercado", "abasto", "pan", "leche", "azucar",
            "huevos", "arroz", "harina", "cafe", "carne", "pollo", "pescado", "queso", "verduras", "frutas",
            "jabon", "detergente", "champu", "aceite", "pasta", "mantequilla", "agua", "refresco"
        ),
        "Recetas" to setOf(
            "receta", "recetas", "ingrediente", "ingredientes", "horno", "cocinar", "hornear", "cucharada",
            "cucharadas", "gramos", "mezclar", "salsa", "freir", "hervir", "batir", "masa"
        ),
        "Trabajo" to setOf(
            "trabajo", "reunion", "reuniones", "cliente", "clientes", "jefe", "oficina", "informe", "proyecto",
            "entrega", "presentacion", "factura", "presupuesto", "empresa", "equipo", "contrato"
        ),
        "Estudios" to setOf(
            "examen", "examenes", "clase", "clases", "estudiar", "universidad", "curso", "materia", "apuntes",
            "leccion", "profesor", "profesora", "tarea escolar", "parcial", "evaluacion"
        ),
        "Salud" to setOf(
            "medico", "medica", "cita medica", "medicina", "medicamento", "pastilla", "dosis", "doctor",
            "hospital", "dentista", "gimnasio", "ejercicio", "dieta", "vacuna", "farmacia"
        ),
        "Viajes" to setOf(
            "viaje", "viajes", "viajar", "vuelo", "hotel", "maleta", "pasaporte", "reserva", "aeropuerto",
            "playa", "boleto", "itinerario"
        ),
        "Finanzas" to setOf(
            "deuda", "banco", "ahorro", "ahorrar", "gasto", "gastos", "dinero", "transferencia", "sueldo",
            "salario", "prestamo", "cuota", "pagar", "pago", "dolares", "bolivares"
        ),
        "Ideas" to setOf("idea", "ideas", "ocurrencia", "inspiracion", "invento", "proyecto personal"),
        "Contactos" to setOf("telefono", "numero de", "direccion", "contacto", "cumpleanos"),
        "Música" to setOf("cancion", "canciones", "album", "artista", "playlist", "musica"),
        "Películas y series" to setOf("pelicula", "peliculas", "serie", "series", "temporada", "capitulo"),
        "Tareas" to setOf(
            "tarea", "tareas", "pendiente", "pendientes", "llamar", "recoger", "arreglar", "limpiar", "lavar",
            "hacer", "enviar", "revisar", "mañana", "manana"
        )
    )

    /** Palabras que nombran el tema directamente: valen por 3 ("receta" pesa más que "harina"). */
    private val FUERTES: Map<String, Set<String>> = mapOf(
        "Compras" to setOf("comprar", "compra", "compras", "supermercado"),
        "Recetas" to setOf("receta", "recetas", "ingredientes"),
        "Trabajo" to setOf("trabajo", "reunion", "cliente", "clientes", "oficina"),
        "Estudios" to setOf("examen", "clase", "estudiar", "universidad"),
        "Salud" to setOf("medico", "doctor", "medicina", "cita medica"),
        "Viajes" to setOf("viaje", "viajar", "vuelo", "hotel"),
        "Finanzas" to setOf("deuda", "banco", "ahorro", "gastos"),
        "Ideas" to setOf("idea", "ideas"),
        "Contactos" to setOf("telefono", "contacto"),
        "Música" to setOf("cancion", "canciones", "playlist"),
        "Películas y series" to setOf("pelicula", "peliculas", "serie", "series"),
        "Tareas" to setOf("tarea", "tareas", "pendiente", "pendientes")
    )

    private val VACIAS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "a", "en", "y", "o", "que",
        "con", "por", "para", "mi", "mis", "tu", "su", "sus", "es", "se", "lo", "le", "me", "nos", "este",
        "esta", "esto", "eso", "ese", "esa", "como", "mas", "muy", "ya", "no", "si", "hay", "nota", "notas",
        "cuaderno", "cuadernos", "nuevo", "nueva", "tema", "personales", "cosas"
    )

    private val MARCAS = Regex("\\p{Mn}+")

    fun normalizarTexto(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(MARCAS, "").lowercase()

    /** Palabras con significado (sin artículos ni preposiciones). */
    fun palabras(texto: String): List<String> =
        normalizarTexto(texto).split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.length >= 3 && it !in VACIAS }

    /** Tema más probable del texto, o null si no encaja con ninguno. */
    fun tema(texto: String): String? {
        val norm = " " + normalizarTexto(texto).replace(Regex("""[^\p{L}\p{N}]+"""), " ") + " "
        var mejor: String? = null
        var mejorPuntos = 0
        for ((tema, claves) in TEMAS) {
            val fuertes = FUERTES[tema].orEmpty()
            var puntos = 0
            for (clave in claves) {
                if (!norm.contains(" ${normalizarTexto(clave)} ")) continue
                puntos += if (clave in fuertes) 3 else 1
            }
            if (puntos > mejorPuntos) {
                mejor = tema
                mejorPuntos = puntos
            }
        }
        return mejor
    }

    /**
     * Cuaderno existente que mejor encaja con el contenido, o null si ninguno encaja
     * (entonces hay que crear uno nuevo con [tituloNuevo]).
     */
    fun elegir(contenido: String, cuadernos: List<CuadernoResumen>): CuadernoResumen? {
        if (cuadernos.isEmpty()) return null
        val temaContenido = tema(contenido)
        val palabrasContenido = palabras(contenido).toSet()

        var mejor: CuadernoResumen? = null
        var mejorPuntos = 0
        for (c in cuadernos) {
            var puntos = 0
            if (temaContenido != null && tema(c.titulo) == temaContenido) puntos += 3
            val palabrasTitulo = palabras(c.titulo)
            if (palabrasTitulo.any { it in palabrasContenido }) puntos += 3
            if (temaContenido != null && c.textoReciente.isNotBlank() && tema(c.textoReciente) == temaContenido) puntos += 2
            val comunes = palabras(c.textoReciente).toSet().intersect(palabrasContenido).size
            puntos += minOf(comunes, 2)
            if (puntos > mejorPuntos) {
                mejor = c
                mejorPuntos = puntos
            }
        }
        return if (mejorPuntos >= 3) mejor else null
    }

    /** Título corto para un cuaderno nuevo: el tema, o las primeras palabras con significado. */
    fun tituloNuevo(contenido: String): String {
        tema(contenido)?.let { return it }
        val originales = contenido.split(Regex("""\s+"""))
            .map { it.trim(' ', '.', ',', ';', ':', '!', '?', '¿', '¡', '"', '«', '»') }
            .filter { it.length >= 3 && normalizarTexto(it) !in VACIAS }
            .take(3)
        val titulo = originales.joinToString(" ").take(24).trim()
        return titulo.replaceFirstChar { it.titlecase() }.ifBlank { "Notas" }
    }
}
