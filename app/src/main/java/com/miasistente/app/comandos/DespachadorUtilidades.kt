package com.miasistente.app.comandos

import com.miasistente.app.memoria.MemoriaLocal

data class ResultadoDespachoHibrido(
    val manejadoLocalmente: Boolean,
    val resultado: ResultadoComando? = null,
    val escalarANubePrompt: String? = null,
    val capacidadNube: String? = null
)

/**
 * Ejecuta en cadena las utilidades seleccionadas por el enrutador local
 * conectándose con [EjecutorLocal], [GestorNotas], [MemoriaLocal] y [ClimaOpenMeteo].
 */
class DespachadorUtilidades(
    private val parser: IntentParser,
    private val local: EjecutorLocal,
    private val notas: GestorNotas,
    private val memoria: MemoriaLocal,
    private val clima: ClimaOpenMeteo
) {

    suspend fun despachar(
        comandoOriginal: String,
        llamadas: List<LlamadaUtilidad>
    ): ResultadoDespachoHibrido {
        if (llamadas.isEmpty()) {
            return ResultadoDespachoHibrido(manejadoLocalmente = false)
        }

        // Si pide respuesta directa del modelo local sin herramientas
        if (llamadas.size == 1 && llamadas.first().tool == RegistroUtilidades.TOOL_RESPONDER) {
            val texto = llamadas.first().args["texto"].orEmpty().trim()
            return if (texto.isNotEmpty()) {
                ResultadoDespachoHibrido(
                    manejadoLocalmente = true,
                    resultado = ResultadoComando(true, texto)
                )
            } else {
                ResultadoDespachoHibrido(manejadoLocalmente = false)
            }
        }

        // Si alguna llamada solicita escalar a Gemini API (multimedia / análisis complejo)
        llamadas.firstOrNull { it.tool == RegistroUtilidades.TOOL_DELEGAR_NUBE }?.let { nube ->
            return ResultadoDespachoHibrido(
                manejadoLocalmente = false,
                escalarANubePrompt = nube.args["prompt"].orEmpty().ifBlank { comandoOriginal },
                capacidadNube = nube.args["capacidad"].orEmpty().ifBlank { "razonamiento" }
            )
        }

        val confirmaciones = mutableListOf<String>()
        var huboExito = false

        for (llamada in llamadas) {
            val res = ejecutarUna(llamada, comandoOriginal)
            if (res != null) {
                if (res.exito) huboExito = true
                if (res.mensaje.isNotBlank()) confirmaciones.add(res.mensaje.trim())
            } else if (llamada.tool == RegistroUtilidades.TOOL_WEB) {
                val q = llamada.args["query"].orEmpty().ifBlank { comandoOriginal }
                return ResultadoDespachoHibrido(
                    manejadoLocalmente = false,
                    escalarANubePrompt = q,
                    capacidadNube = "web"
                )
            }
        }

        return if (confirmaciones.isNotEmpty()) {
            ResultadoDespachoHibrido(
                manejadoLocalmente = true,
                resultado = ResultadoComando(huboExito, confirmaciones.joinToString(" "))
            )
        } else {
            ResultadoDespachoHibrido(manejadoLocalmente = false)
        }
    }

    private suspend fun ejecutarUna(
        llamada: LlamadaUtilidad,
        comandoOriginal: String
    ): ResultadoComando? = when (llamada.tool) {

        RegistroUtilidades.TOOL_SISTEMA -> {
            val accion = llamada.args["accion"].orEmpty().lowercase()
            val valor = llamada.args["valor"].orEmpty().lowercase()
            val fraseSintetica = when (accion) {
                "brillo" -> "brillo $valor"
                "volumen" -> "volumen $valor"
                "wifi" -> "$valor wifi"
                "bluetooth" -> "$valor bluetooth"
                "linterna" -> "$valor linterna"
                "no_molestar" -> "$valor no molestar"
                "bateria" -> "nivel de bateria"
                "hora" -> "que hora es"
                else -> "$accion $valor"
            }.trim()
            local.ejecutar(parser.parsear(fraseSintetica))
        }

        RegistroUtilidades.TOOL_ALARMA -> {
            val tipo = llamada.args["tipo"].orEmpty().lowercase()
            val valor = llamada.args["valor"].orEmpty()
            val etiqueta = llamada.args["etiqueta"].orEmpty()
            val fraseSintetica = if (tipo == "temporizador") {
                "temporizador de $valor minutos $etiqueta".trim()
            } else {
                "pon alarma a las $valor $etiqueta".trim()
            }
            local.ejecutar(parser.parsear(fraseSintetica))
        }

        RegistroUtilidades.TOOL_NOTAS -> {
            val accion = llamada.args["accion"].orEmpty().lowercase()
            val contenido = ExtractorNota.limpiar(llamada.args["contenido"].orEmpty())
            val destinoRaw = llamada.args["destino"].orEmpty().trim()
            if (accion == "buscar") {
                val hallazgos = memoria.buscar(contenido, limite = 4)
                val msg = if (hallazgos.isEmpty()) {
                    "No encontré notas sobre «$contenido»."
                } else {
                    "Esto encontré: ${hallazgos.joinToString("; ")}"
                }
                ResultadoComando(true, msg)
            } else {
                val destino = when {
                    destinoRaw.equals("externa", ignoreCase = true) ||
                        destinoRaw.contains("keep", ignoreCase = true) -> DestinoNota.App("keep")
                    destinoRaw.isNotBlank() -> DestinoNota.Cuaderno(destinoRaw)
                    else -> DestinoNota.SinEspecificar
                }
                notas.guardar(PeticionGuardar(contenido, destino))
            }
        }

        RegistroUtilidades.TOOL_APPS -> {
            val accion = llamada.args["accion"].orEmpty().lowercase()
            val objetivo = llamada.args["objetivo"].orEmpty()
            val fraseSintetica = when (accion) {
                "navegar" -> "como llegar a $objetivo"
                "llamar" -> "llamar a $objetivo"
                else -> "abre $objetivo"
            }
            local.ejecutar(parser.parsear(fraseSintetica)).also {
                if (it.exito && accion == "abrir_app") {
                    memoria.registrarHabito("apps", objetivo.lowercase())
                }
            }
        }

        RegistroUtilidades.TOOL_MEMORIA -> {
            val accion = llamada.args["accion"].orEmpty().lowercase()
            val dato = llamada.args["dato"].orEmpty().trim()
            if (accion == "recordar" && dato.isNotEmpty()) {
                memoria.recordar(dato)
                ResultadoComando(true, "Anotado en mi memoria: $dato.")
            } else {
                val recuerdos = memoria.buscar(dato, limite = 4)
                val msg = if (recuerdos.isEmpty()) {
                    "No tengo recuerdos guardados sobre eso."
                } else {
                    "Recuerdo esto: ${recuerdos.joinToString("; ")}"
                }
                ResultadoComando(true, msg)
            }
        }

        RegistroUtilidades.TOOL_WEB -> {
            val q = llamada.args["query"].orEmpty().ifBlank { comandoOriginal }
            val ciudad = HabilidadesLocales().peticionClima(q)?.ciudad
            if (!ciudad.isNullOrBlank()) {
                val datos = runCatching { clima.consultar(ciudad) }.getOrNull()
                if (datos != null) ResultadoComando(true, ClimaTexto.resumen(datos)) else null
            } else {
                null // Delega búsqueda web en tiempo real a la nube
            }
        }

        else -> null
    }
}
