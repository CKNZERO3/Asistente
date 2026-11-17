package com.miasistente.app.memoria

/** Peso de una opción dentro de un contexto ("destino_nota" → "cuaderno", "app:com.google…"). */
data class PesoHabito(val opcion: String, val peso: Double, val usos: Int)

/**
 * Aprendizaje progresivo de hábitos: cada elección suma 1 a su opción y las demás pierden un
 * poco de peso (decaimiento), así los hábitos recientes cuentan más que los antiguos.
 *
 * Solo se automatiza una decisión cuando el hábito es claro; mientras tanto se pregunta.
 */
object AprendizajeHabitos {
    const val DECAIMIENTO = 0.9
    const val MIN_USOS = 3
    const val PROPORCION_MINIMA = 0.6

    /** Opción dominante, o null si todavía no hay un hábito claro. */
    fun decidir(pesos: List<PesoHabito>): String? {
        val total = pesos.sumOf { it.peso }
        if (total <= 0.0) return null
        val mejor = pesos.maxByOrNull { it.peso } ?: return null
        return if (mejor.usos >= MIN_USOS && mejor.peso / total >= PROPORCION_MINIMA) mejor.opcion else null
    }

    /** Pesos tras registrar que el usuario eligió [elegida]. */
    fun actualizar(pesos: List<PesoHabito>, elegida: String): List<PesoHabito> {
        val resultado = pesos.map {
            if (it.opcion == elegida) PesoHabito(it.opcion, it.peso * DECAIMIENTO + 1.0, it.usos + 1)
            else it.copy(peso = it.peso * DECAIMIENTO)
        }
        return if (resultado.any { it.opcion == elegida }) resultado
        else resultado + PesoHabito(elegida, 1.0, 1)
    }
}
