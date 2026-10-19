package com.miasistente.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Captura audio del micrófono de forma continua usando AudioRecord.
 *
 * Formato: 16 kHz, mono, PCM de 16 bits. Es el estándar que usan casi todos los
 * modelos de voz (wakeword, verificación de hablante, reconocimiento), así que
 * las fases siguientes podrán enchufarse a este mismo flujo sin conversiones.
 *
 * Funcionamiento:
 *  - Un hilo propio lee el micrófono en bloques ("frames") de FRAME_SIZE muestras (32 ms).
 *  - Cada frame se entrega por el callback [onFrame].
 *  - El audio NO se guarda en disco ni se envía a ninguna parte: solo pasa por memoria.
 *
 * IMPORTANTE sobre los callbacks:
 *  - Se ejecutan en el hilo de audio, NO en el hilo principal. Deben ser rápidos.
 *  - El arreglo de muestras se REUTILIZA en cada frame. Si necesitas conservarlo,
 *    cópialo (frame.copyOf(cantidad)).
 *
 * Uso:
 *   val grabador = AudioRecorder(context)
 *   val ok = grabador.iniciar(
 *       onFrame = { muestras, cantidad -> /* procesar */ },
 *       onError = { mensaje -> /* avisar */ }
 *   )
 *   ...
 *   grabador.detener()
 */
class AudioRecorder(context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"

        /** Muestras por segundo. */
        const val SAMPLE_RATE = 16_000

        /** Muestras por frame: 512 muestras = 32 milisegundos de audio. */
        const val FRAME_SIZE = 512
    }

    private val appContext = context.applicationContext

    // @Volatile: el hilo de audio y el hilo principal ven siempre el valor actualizado
    @Volatile
    private var ejecutando = false

    private var hilo: Thread? = null

    /** true mientras el hilo de audio está leyendo el micrófono. */
    val estaCapturando: Boolean
        get() = ejecutando

    /**
     * Abre el micrófono y empieza a capturar.
     *
     * @return true si el micrófono quedó capturando; false si algo falló
     *         (en ese caso [onError] ya fue llamado con la explicación).
     */
    @SuppressLint("MissingPermission") // el permiso se verifica manualmente justo abajo
    fun iniciar(
        onFrame: (muestras: ShortArray, cantidad: Int) -> Unit,
        onError: (mensaje: String) -> Unit
    ): Boolean {
        if (ejecutando) return true

        // 1) Permiso de micrófono
        val permiso = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
        if (permiso != PackageManager.PERMISSION_GRANTED) {
            onError("Falta el permiso de micrófono.")
            return false
        }

        // 2) Tamaño de buffer interno: el mínimo del sistema o 4 frames, lo que sea mayor
        val bufferMinimo = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferMinimo <= 0) {
            onError("Este teléfono no soporta la captura de audio a 16 kHz.")
            return false
        }
        val bufferBytes = maxOf(bufferMinimo, FRAME_SIZE * 2 * 16) // 2 bytes por muestra

        // 3) Crear el AudioRecord
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // optimizado para voz
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear AudioRecord", e)
            onError("No se pudo abrir el micrófono: ${e.message}")
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError("El micrófono no se pudo inicializar.")
            return false
        }

        // 4) Empezar a grabar
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "startRecording falló", e)
            record.release()
            onError("No se pudo iniciar la grabación: ${e.message}")
            return false
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            // Suele pasar si otra app (por ejemplo una llamada) tiene el micrófono
            record.release()
            onError("El micrófono está ocupado por otra aplicación.")
            return false
        }

        // 5) Lanzar el hilo de lectura
        ejecutando = true
        hilo = Thread({ bucleDeLectura(record, onFrame, onError) }, "AudioRecorder").also {
            it.start()
        }
        Log.d(TAG, "Captura iniciada: $SAMPLE_RATE Hz, frames de $FRAME_SIZE muestras")
        return true
    }

    /**
     * Detiene la captura y libera el micrófono.
     * Espera (máximo 1 segundo) a que el hilo de audio termine.
     */
    fun detener() {
        ejecutando = false
        val h = hilo
        hilo = null
        if (h != null && h !== Thread.currentThread()) {
            try {
                h.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /** Código del hilo de audio: lee frame tras frame hasta que se pida detener. */
    private fun bucleDeLectura(
        record: AudioRecord,
        onFrame: (ShortArray, Int) -> Unit,
        onError: (String) -> Unit
    ) {
        // Prioridad alta para evitar cortes en el audio
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val frame = ShortArray(FRAME_SIZE)
        try {
            while (ejecutando) {
                // read() bloquea hasta tener un frame completo (≈32 ms)
                val leidas = record.read(frame, 0, FRAME_SIZE)
                when {
                    leidas > 0 -> onFrame(frame, leidas)
                    leidas < 0 -> {
                        // Valores negativos = códigos de error de AudioRecord
                        onError("Error al leer el micrófono (código $leidas).")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en el hilo de audio", e)
            onError("Fallo en la captura de audio: ${e.message}")
        } finally {
            ejecutando = false
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                // ya estaba detenido
            }
            record.release()
            Log.d(TAG, "Captura detenida y micrófono liberado")
        }
    }
}
