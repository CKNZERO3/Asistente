package com.miasistente.app.comandos

/**
 * Recuerda qué modelos agotaron su cuota para no seguir golpeándolos.
 *
 * Con el plan gratuito, cada intento fallido gasta una petición. Tras un 429 de Google
 * el modelo queda en pausa 60 s; si vuelve a fallar la pausa se duplica (máx. 15 min).
 * Un éxito reinicia el modelo.
 */
class CuotaModelos(private val reloj: () -> Long = System::currentTimeMillis) {

    private val hasta = HashMap<String, Long>()
    private val fallos = HashMap<String, Int>()

    @Synchronized
    fun disponible(modelo: String): Boolean = (hasta[modelo] ?: 0L) <= reloj()

    @Synchronized
    fun segundosRestantes(modelo: String): Long {
        val faltan = ((hasta[modelo] ?: 0L) - reloj()).coerceAtLeast(0L)
        return (faltan + 999L) / 1000L
    }

    @Synchronized
    fun registrarAgotada(modelo: String) {
        val n = (fallos[modelo] ?: 0) + 1
        fallos[modelo] = n
        val espera = (BASE_MS shl (n - 1).coerceAtMost(4)).coerceAtMost(MAX_MS)
        hasta[modelo] = reloj() + espera
    }

    @Synchronized
    fun registrarExito(modelo: String) {
        fallos.remove(modelo)
        hasta.remove(modelo)
    }

    companion object {
        const val BASE_MS = 60_000L
        const val MAX_MS = 15 * 60_000L
    }
}
