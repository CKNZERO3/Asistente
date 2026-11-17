package com.miasistente.app.comandos

/** Adónde quiere el usuario guardar lo que dicta. */
sealed interface DestinoNota {
    /** Cuaderno de Iris Notebook. [nombre] null = "en mi cuaderno", sin decir cuál. */
    data class Cuaderno(val nombre: String?) : DestinoNota

    /** App externa nombrada por el usuario ("Keep", "Samsung Notes"…). */
    data class App(val nombre: String) : DestinoNota

    /** No dijo dónde: decide el hábito aprendido. */
    data object SinEspecificar : DestinoNota
}

/** Lo que hay que guardar (sin palabras de la orden) y dónde. */
data class PeticionGuardar(
    val contenido: String,
    val destino: DestinoNota,
    /** El usuario se refirió a una imagen o archivo ("guarda esta imagen…"). */
    val mencionaAdjunto: Boolean = false
)

/**
 * Separa la orden ("puedes crear una nota y guardarla:") del contenido ("comprar pan, leche")
 * y detecta el destino ("en Keep", "en el cuaderno Recetas", "en Trabajo").
 *
 * Funciona sin red y sin IA: solo reglas, para que sea instantáneo.
 */
object ExtractorNota {

    private val CORTESIA = Regex(
        """^(?:(?:oye|iris|por favor|porfa|puedes|podrias|me puedes|me podrias|quiero que|necesito que|quiero|necesito|ahora)(?:\s|,)+)+"""
    )

    private val VERBO = Regex(
        """^(?:(?:crea|crear|creame|haz|hazme|nueva|toma|tomar|anade|anadir|agrega|agregar)\s+(?:una\s+|un\s+|la\s+)?(?:nota|apunte)""" +
            """|guardame|guardalo|guardala|guarda|guardar|anotame|anota|anotar|apuntame|apunta|apuntar|recuerdame|recordarme""" +
            """|escribeme|escribe(?!\s+(?:un\s+|el\s+)?(?:correo|mensaje|email)\b)|escribir)\b"""
    )

    /** Palabras de la orden que nunca forman parte del contenido. */
    private val RELLENO = Regex(
        """^(?:y\s+(?:la\s+|lo\s+)?(?:guarda|guardala|guardalo|guardarla|guardarlo|guardes|guardas|guardame)""" +
            """|(?:una|la|esta|esa|mi|un)\s+(?:nota|apunte)|nota|esto|eso|tambien|rapido|rapidamente|por favor|porfa)\b"""
    )

    private val ADJUNTO = Regex(
        """^(?:esta|este|estas|estos|esa|ese|la|las|el|los|mi|mis)\s+""" +
            """(?:imagen|imagenes|foto|fotos|fotografia|archivo|archivos|documento|documentos|captura|capturas|adjunto|adjuntos|pdf)\b"""
    )

    private val DESTINO_CUADERNO = Regex(
        """^(?:en|dentro de|al|a)\s+(?:el\s+|mi\s+|mis\s+|los\s+|un\s+)?""" +
            """(?:iris notebook|cuadernos|cuaderno|libretas|libreta|notebook)""" +
            """(?:\s+(?:de|del|llamado|que se llama|titulado))?\b"""
    )

    private val APPS_CONOCIDAS =
        """google keep|keep|samsung notes|notas de samsung|notas de google|google tasks|tareas de google|""" +
            """onenote|one note|evernote|notion|colornote|obsidian|todoist|notas"""

    private val DESTINO_APP = Regex(
        """^(?:en|a|con)\s+(?:la\s+|mi\s+)?(?:(?:app|aplicacion)\s+(?:de\s+)?)?($APPS_CONOCIDAS)\b"""
    )

    private val DESTINO_APP_GENERICA = Regex("""^(?:en|con)\s+(?:la\s+|mi\s+)?(?:app|aplicacion)\s+(?:de\s+)?([a-z0-9]\w*)""")

    /** "en la lista": no es un destino concreto. */
    private val LISTA = Regex("""^en\s+(?:la|mi|una|las|mis)\s+listas?\b""")

    /** "anota en Trabajo …": cuaderno de una palabra. */
    private val DESTINO_NOMBRE = Regex(
        """^en\s+(?!(?:la|el|un|una|mi|mis|los|las|que|cuanto|cuanta|cuando|caso|vez|serio|cuenta)\b)([a-z0-9]\w*)"""
    )

    private val SEPARADOR = Regex(
        """^(?::|;|-|que diga|que dice|que ponga|que pone|diciendo|con el texto|lo siguiente|lo que sigue)"""
    )

    /** Separador en cualquier punto (para limitar el nombre del cuaderno). */
    private val SEPARADOR_MEDIO = Regex("""\s*(?::|;|\bque diga\b|\bque dice\b|\bque ponga\b|\bque pone\b|\bdiciendo\b|\bcon el texto\b|\blo siguiente\b)""")

    private val INICIO_CONTENIDO = Regex("""^(?:de que|que|de|sobre|acerca de)\s+""")
    private val COLA = Regex("""\s+(?:por favor|porfa)$""")
    private val SOLO_ADJUNTO = Regex(
        """^(?:esta|este|estas|estos|la|las|el|los)?\s*(?:imagen|imagenes|foto|fotos|archivo|archivos|documento|documentos|captura|capturas|adjunto|adjuntos)$"""
    )
    private val ESPACIOS = Regex("""\s+""")

    /** Devuelve null si la frase no es una orden de guardar o anotar. */
    fun analizar(frase: String): PeticionGuardar? {
        val original = frase.trim()
        if (original.isEmpty()) return null
        val norm = normalizar(original)
        // Igual que IntentParser: se corta del original para conservar acentos y mayúsculas.
        val base = if (original.length == norm.length) original else norm

        var i = saltarEspacios(norm, 0)
        CORTESIA.find(norm.substring(i))?.let { i = saltarEspacios(norm, i + it.value.length) }
        val verbo = VERBO.find(norm.substring(i)) ?: return null
        i = saltarEspacios(norm, i + verbo.value.length)

        var destino: DestinoNota = DestinoNota.SinEspecificar
        var mencionaAdjunto = false
        var huboSeparador = false

        while (i < norm.length) {
            val resto = norm.substring(i)
            val sep = SEPARADOR.find(resto)
            if (sep != null) {
                i = saltarEspacios(norm, i + sep.value.length)
                huboSeparador = true
                break
            }
            if (resto.startsWith(",")) {
                i = saltarEspacios(norm, i + 1)
                continue
            }
            val relleno = RELLENO.find(resto)
            if (relleno != null) {
                i = saltarEspacios(norm, i + relleno.value.length)
                continue
            }
            val adjunto = ADJUNTO.find(resto)
            if (adjunto != null) {
                mencionaAdjunto = true
                i = saltarEspacios(norm, i + adjunto.value.length)
                continue
            }
            if (destino == DestinoNota.SinEspecificar) {
                val cuaderno = DESTINO_CUADERNO.find(resto)
                if (cuaderno != null) {
                    i = saltarEspacios(norm, i + cuaderno.value.length)
                    val (nombre, fin) = nombreCuaderno(norm, base, i)
                    destino = DestinoNota.Cuaderno(nombre)
                    i = saltarEspacios(norm, fin)
                    continue
                }
                val app = DESTINO_APP.find(resto) ?: DESTINO_APP_GENERICA.find(resto)
                if (app != null) {
                    val g = app.groups[1]!!.range
                    destino = DestinoNota.App(nombrePropio(base.substring(i + g.first, i + g.last + 1)))
                    i = saltarEspacios(norm, i + app.value.length)
                    continue
                }
                val lista = LISTA.find(resto)
                if (lista != null) {
                    i = saltarEspacios(norm, i + lista.value.length)
                    continue
                }
                val nombre = DESTINO_NOMBRE.find(resto)
                if (nombre != null) {
                    val g = nombre.groups[1]!!.range
                    destino = DestinoNota.Cuaderno(nombrePropio(base.substring(i + g.first, i + g.last + 1)))
                    i = saltarEspacios(norm, i + nombre.value.length)
                    continue
                }
            }
            break
        }

        var contenido = if (i < base.length) base.substring(i) else ""
        if (!huboSeparador) {
            val inicio = INICIO_CONTENIDO.find(normalizar(contenido))
            if (inicio != null && inicio.value.length <= contenido.length) {
                contenido = contenido.substring(inicio.value.length)
            }
        }
        contenido = limpiar(contenido)
        val contenidoNorm = normalizar(contenido)
        if (SOLO_ADJUNTO.matches(contenidoNorm)) {
            mencionaAdjunto = true
            contenido = ""
        }
        return PeticionGuardar(contenido, destino, mencionaAdjunto)
    }

    /** Quita comillas, puntuación sobrante y "por favor" al final. */
    fun limpiar(texto: String): String {
        var t = texto.trim()
        t = t.trim(' ', '.', ',', ';', ':', '!', '?', '¿', '¡', '-', '"', '“', '”', '«', '»', '\'')
        val cola = COLA.find(normalizar(t))
        if (cola != null && cola.range.first <= t.length) t = t.substring(0, cola.range.first)
        return t.trim(' ', '.', ',', ';', ':', '!', '?', '"', '“', '”', '«', '»', '\'').replace(ESPACIOS, " ")
    }

    /**
     * Nombre después de "en el cuaderno …". Con separador ("…Recetas: pan") el nombre es todo
     * hasta el separador (máx. 4 palabras); sin separador, una sola palabra.
     */
    private fun nombreCuaderno(norm: String, base: String, desde: Int): Pair<String?, Int> {
        if (desde >= norm.length) return null to desde
        val resto = norm.substring(desde)
        if (SEPARADOR.find(resto) != null || resto.startsWith(",")) return null to desde

        val sep = SEPARADOR_MEDIO.find(resto)
        if (sep != null) {
            val candidato = base.substring(desde, desde + sep.range.first).trim()
            val palabras = candidato.split(ESPACIOS).filter { it.isNotBlank() }
            if (palabras.size in 1..4) {
                return nombrePropio(limpiar(candidato)).ifBlank { null } to (desde + sep.range.first)
            }
        }
        val palabra = Regex("""^[a-z0-9]\w*""").find(resto) ?: return null to desde
        val nombre = base.substring(desde, desde + palabra.value.length)
        return nombrePropio(nombre) to (desde + palabra.value.length)
    }

    private fun nombrePropio(texto: String): String =
        texto.trim().replaceFirstChar { it.titlecase() }

    private fun saltarEspacios(s: String, desde: Int): Int {
        var i = desde
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }
}
