package com.miasistente.app.comandos

/**
 * Selecciona un modelo de texto según el trabajo solicitado.
 *
 * Las cuotas son las comunicadas para el proyecto Iris y sirven como referencia
 * de planificación; Google puede modificarlas sin aviso.
 */
data class SeleccionModelo(
    val modelo: String,
    val categoria: CategoriaTarea,
    val motivo: String
)

/**
 * LIGERA: la resuelve el modelo del teléfono (transformar texto, saludos, escritura creativa corta).
 * GENERAL: preguntas de datos o conocimiento; el modelo local se equivoca, va a Gemini ligero.
 * COMPLEJA: razonamiento o varios pasos; va a Gemini avanzado (cuota muy limitada).
 */
enum class CategoriaTarea {
    LIGERA,
    GENERAL,
    COMPLEJA
}

object IrisModelSelector {

    // Cuotas del plan gratuito observadas en AI Studio (peticiones/min · /día):
    //   gemini-3.5-flash-lite: 15 · 500   -> conversación general y tareas ligeras
    //   gemini-3.1-flash-lite: 15 · 500   -> alterno para conversación general y tareas ligeras
    //   gemini-3.6-flash:       5 ·  20   -> tareas complejas
    //   gemini-3.8-flash:       5 ·  20   -> alterno para tareas complejas (suma otras 20 al día)
    // gemini-2.5-flash está deprecado (404 en proyectos nuevos).
    const val MODELO_GENERAL = "gemini-3.5-flash-lite"
    const val MODELO_LIGERO = "gemini-3.5-flash-lite"
    const val MODELO_LIGERO_ALTERNO = "gemini-3.1-flash-lite"
    const val MODELO_AVANZADO = "gemini-3.6-flash"
    const val MODELO_AVANZADO_ALTERNO = "gemini-3.8-flash"

    private val modelosPermitidos = setOf(
        MODELO_GENERAL,
        MODELO_LIGERO,
        MODELO_LIGERO_ALTERNO,
        MODELO_AVANZADO,
        MODELO_AVANZADO_ALTERNO
    )

    private const val MAX_CARACTERES_TAREA_LIGERA = 1_500

    private val tareasComplejas = Regex(
        """\b(investiga|investigar|investigacion|analiza|analizar|compara|comparar|desarrolla|desarrollar|programa|programar|depura|depurar|arquitectura|disena|planifica|planificar|estrategia|complejo|detalladamente|implementa|implementar|construye|construir|automatiza|automatizar|multi(etapa|paso)|varias etapas|multiples fuentes|informe tecnico|paso a paso)\b"""
    )

    private val tareasLigeras = Regex(
        """\b(resume|resumir|resumen|traduce|traducir|traduccion|corrige|corregir|correccion|extrae|extraer|clasifica|clasificar|reformula|reescribe|simplifica|simplificar|sinonimo|sinonimos|antonimo|antonimos|ortografia)\b|""" +
            """\b(como se dice|en ingles|en espanol|en frances|en portugues|en italiano|en aleman)\b"""
    )

    private const val MAX_CARACTERES_GENERACION = 500

    // Solo la frase completa (saludo + nombre opcional): "hola, ¿capital de Francia?" NO cuenta.
    private val saludos = Regex(
        """^(?:hola|hey|buenas|buenos dias|buenas tardes|buenas noches|gracias|muchas gracias|adios|chao|hasta luego|que tal|como estas|como te va|quien eres|como te llamas)(?:,? iris)?$"""
    )

    // Escritura creativa corta. Nada de datos: "dame una lista de países" va a Gemini.
    private val escrituraCreativa = Regex(
        """^(?:escribe|escribeme|redacta|redactame|genera|generame|inventa|inventame|compon|componme|crea|creame|sugiere|sugiereme|cuentame|dame)\b.*""" +
            """\b(poema|poesia|cuento|chiste|mensaje|correo|email|carta|frase|frases|slogan|saludo|idea|ideas|parrafo|cancion)\b"""
    )

    fun seleccionar(pregunta: String): SeleccionModelo {
        val texto = normalizar(pregunta.trim())

        if (texto.isNotBlank() && tareasComplejas.containsMatchIn(texto)) {
            return SeleccionModelo(
                modelo = MODELO_AVANZADO,
                categoria = CategoriaTarea.COMPLEJA,
                motivo = "La solicitud requiere razonamiento o varios pasos."
            )
        }

        if (
            texto.isNotBlank() &&
            pregunta.length <= MAX_CARACTERES_TAREA_LIGERA &&
            tareasLigeras.containsMatchIn(texto)
        ) {
            return SeleccionModelo(
                modelo = MODELO_LIGERO,
                categoria = CategoriaTarea.LIGERA,
                motivo = "La solicitud es una transformación o consulta breve."
            )
        }

        val sinSignos = texto.trim(' ', '.', ',', ';', ':', '!', '?', '¿', '¡')
        if (
            sinSignos.isNotBlank() &&
            (saludos.matches(sinSignos) ||
                (sinSignos.length <= MAX_CARACTERES_GENERACION && escrituraCreativa.containsMatchIn(sinSignos)))
        ) {
            return SeleccionModelo(
                modelo = MODELO_LIGERO,
                categoria = CategoriaTarea.LIGERA,
                motivo = "Saludo o escritura creativa corta: la resuelve el modelo local."
            )
        }

        return SeleccionModelo(
            modelo = MODELO_GENERAL,
            categoria = CategoriaTarea.GENERAL,
            motivo = "La solicitud corresponde al uso conversacional general."
        )
    }

    fun esModeloPermitido(modelo: String): Boolean =
        modelo in modelosPermitidos
}
