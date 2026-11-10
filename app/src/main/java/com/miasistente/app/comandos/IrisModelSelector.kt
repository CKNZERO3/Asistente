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

enum class CategoriaTarea {
    LIGERA,
    GENERAL,
    COMPLEJA
}

object IrisModelSelector {

    // gemini-2.5-flash está deprecado y devuelve 404 en proyectos nuevos; 3.6 es la versión estable.
    const val MODELO_GENERAL = "gemini-3.6-flash"
    const val MODELO_LIGERO = "gemini-3.5-flash-lite"
    const val MODELO_AVANZADO = "gemini-3.8-flash"

    private val modelosPermitidos = setOf(
        MODELO_GENERAL,
        MODELO_LIGERO,
        MODELO_AVANZADO
    )

    private const val MAX_CARACTERES_TAREA_LIGERA = 1_500

    private val tareasComplejas = Regex(
        """\b(investiga|investigar|investigacion|analiza|analizar|compara|comparar|desarrolla|desarrollar|programa|programar|depura|depurar|arquitectura|disena|planifica|planificar|estrategia|complejo|detalladamente|implementa|implementar|construye|construir|automatiza|automatizar|multi(etapa|paso)|varias etapas|multiples fuentes|informe tecnico|paso a paso)\b"""
    )

    private val tareasLigeras = Regex(
        """\b(resume|resumir|resumen|traduce|traducir|traduccion|corrige|corregir|correccion|extrae|extraer|clasifica|clasificar|reformula|reescribe|simplifica|simplificar|define|definir|definicion)\b"""
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
                motivo = "La solicitud es una transformación de texto acotada."
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
