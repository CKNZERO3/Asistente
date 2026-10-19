package com.miasistente.app.audio

/**
 * Contrato común de cualquier detector de palabra clave.
 *
 * El servicio solo conoce esta interfaz. Así, hoy usamos Vosk (VoskWakeWord) y mañana
 * podemos cambiar a otro motor (openWakeWord, por ejemplo) escribiendo una clase nueva,
 * sin tocar el servicio ni la pantalla.
 *
 * Todos los métodos se llaman desde el hilo de audio, excepto close().
 */
interface WakeWordEngine : AutoCloseable {

    /** Palabra o frase que este motor está esperando. */
    val palabraClave: String

    /** Último texto que el motor reconoció (útil para depurar). Vacío si aún no hay nada. */
    val ultimoTexto: String

    /**
     * Procesa un frame de audio (PCM 16 bits, 16 kHz, mono).
     *
     * @param muestras audio del frame (el arreglo se reutiliza: no lo guardes sin copiarlo)
     * @param cantidad cuántas muestras válidas hay
     * @param hayVoz lo que dice el VoiceDetector. Los motores pesados lo usan como
     *        "compuerta" para no trabajar en silencio; los motores livianos pueden ignorarlo.
     * @return true si en este frame se completó la detección de la palabra clave
     */
    fun procesar(muestras: ShortArray, cantidad: Int, hayVoz: Boolean): Boolean

    /** Libera memoria y recursos nativos. Después de esto el motor no se puede volver a usar. */
    override fun close()
}
