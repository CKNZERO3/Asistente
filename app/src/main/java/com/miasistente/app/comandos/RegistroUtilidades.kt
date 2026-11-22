package com.miasistente.app.comandos

import org.json.JSONArray
import org.json.JSONObject

data class ParametroUtilidad(
    val nombre: String,
    val tipo: String,
    val descripcion: String,
    val requerido: Boolean = true,
    val enumValores: List<String> = emptyList()
)

data class UtilidadIris(
    val nombre: String,
    val descripcion: String,
    val parametros: List<ParametroUtilidad>
)

data class LlamadaUtilidad(
    val tool: String,
    val args: Map<String, String>
)

/**
 * Catálogo central de utilidades (Tool-Calling híbrido) de Iris.
 * - Nivel 1 (Local): Prompt JSON ultracompacto (< 260 tokens) para Gemma 3 int4.
 * - Nivel 2 (Nube): Esquema estándar functionDeclarations para Gemini API.
 */
object RegistroUtilidades {

    const val TOOL_SISTEMA = "sistema_control"
    const val TOOL_ALARMA = "alarma_temporizador"
    const val TOOL_NOTAS = "notas_listas"
    const val TOOL_APPS = "apps_navegacion"
    const val TOOL_MEMORIA = "memoria_usuario"
    const val TOOL_WEB = "consulta_web"
    const val TOOL_DELEGAR_NUBE = "delegar_nube"
    const val TOOL_RESPONDER = "responder_directo"

    val catalogo: List<UtilidadIris> = listOf(
        UtilidadIris(
            nombre = TOOL_SISTEMA,
            descripcion = "Controla ajustes o consulta estado del teléfono (brillo, volumen, wifi, bluetooth, linterna, no_molestar, bateria, hora).",
            parametros = listOf(
                ParametroUtilidad("accion", "STRING", "Ajuste a controlar o consultar", enumValores = listOf("brillo", "volumen", "wifi", "bluetooth", "linterna", "no_molestar", "bateria", "hora")),
                ParametroUtilidad("valor", "STRING", "Valor deseado: encender, apagar, subir, bajar, porcentaje (0-100) o consultar", requerido = false)
            )
        ),
        UtilidadIris(
            nombre = TOOL_ALARMA,
            descripcion = "Configura una alarma a una hora específica o un temporizador por minutos.",
            parametros = listOf(
                ParametroUtilidad("tipo", "STRING", "Tipo de aviso", enumValores = listOf("alarma", "temporizador")),
                ParametroUtilidad("valor", "STRING", "Hora en formato HH:mm para alarma, o cantidad de minutos para temporizador"),
                ParametroUtilidad("etiqueta", "STRING", "Nombre o motivo opcional", requerido = false)
            )
        ),
        UtilidadIris(
            nombre = TOOL_NOTAS,
            descripcion = "Guarda o busca contenido limpio en los cuadernos internos de Iris o en app externa de notas.",
            parametros = listOf(
                ParametroUtilidad("accion", "STRING", "Operación", enumValores = listOf("guardar", "buscar")),
                ParametroUtilidad("contenido", "STRING", "Texto limpio a guardar o término a buscar"),
                ParametroUtilidad("destino", "STRING", "Nombre del cuaderno o 'externa' si pide Keep/Notas", requerido = false)
            )
        ),
        UtilidadIris(
            nombre = TOOL_APPS,
            descripcion = "Abre una aplicación instalada, inicia navegación GPS o realiza una llamada.",
            parametros = listOf(
                ParametroUtilidad("accion", "STRING", "Tipo de acción", enumValores = listOf("abrir_app", "navegar", "llamar")),
                ParametroUtilidad("objetivo", "STRING", "Nombre de la app, dirección/lugar o nombre del contacto")
            )
        ),
        UtilidadIris(
            nombre = TOOL_MEMORIA,
            descripcion = "Guarda un dato personal del usuario o consulta recuerdos previos en memoria local.",
            parametros = listOf(
                ParametroUtilidad("accion", "STRING", "Operación de memoria", enumValores = listOf("recordar", "consultar")),
                ParametroUtilidad("dato", "STRING", "Hecho a memorizar o pregunta sobre recuerdos")
            )
        ),
        UtilidadIris(
            nombre = TOOL_WEB,
            descripcion = "Consulta clima actual o información en tiempo real de internet.",
            parametros = listOf(
                ParametroUtilidad("query", "STRING", "Consulta de búsqueda o ciudad para el clima")
            )
        ),
        UtilidadIris(
            nombre = TOOL_DELEGAR_NUBE,
            descripcion = "Escala a Gemini en la nube cuando se pide generar imágenes, videos, audios o análisis profundo.",
            parametros = listOf(
                ParametroUtilidad("capacidad", "STRING", "Tipo de tarea pesada", enumValores = listOf("imagen", "video", "audio", "razonamiento")),
                ParametroUtilidad("prompt", "STRING", "Instrucción completa del usuario")
            )
        )
    )

    /**
     * Prompt compacto para que el modelo local (Gemma 3 1B/4B) decida herramientas en JSON
     * sin gastar tokens innecesarios.
     */
    fun generarPromptCompactoLocal(contextoUsuario: String = ""): String {
        val listaTools = catalogo.joinToString("\n") { tool ->
            val params = tool.parametros.joinToString(",") { p ->
                if (p.enumValores.isNotEmpty()) "${p.nombre}:(${p.enumValores.joinToString("|")})"
                else p.nombre
            }
            "- ${tool.nombre}{$params}: ${tool.descripcion}"
        }

        val bloqueContexto = contextoUsuario.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { "\nContexto usuario:\n$it\n" }
            .orEmpty()

        return """
Eres el enrutador de utilidades de Iris. Analiza la orden del usuario y responde ÚNICAMENTE con un arreglo JSON válido de llamadas.
Si el usuario encadena varias órdenes, incluye varias llamadas en orden.
Si es solo charla general y no requiere herramienta, responde: [{"tool":"$TOOL_RESPONDER","args":{"texto":"tu respuesta breve en español"}}]

Herramientas disponibles:
$listaTools
$bloqueContexto
Formato estricto de salida (sin markdown ni texto extra):
[{"tool":"nombre_tool","args":{"param":"valor"}}]
        """.trimIndent()
    }

    /**
     * Parsea de forma resiliente la respuesta del LLM local.
     * Tolera bloques ```json ... ```, objetos sueltos {...} o arreglos [...].
     */
    fun parsearLlamadasLocales(respuestaCruda: String): List<LlamadaUtilidad> {
        val limpia = respuestaCruda
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()

        if (limpia.isEmpty()) return emptyList()

        return try {
            val array = when {
                limpia.startsWith("[") -> JSONArray(limpia)
                limpia.startsWith("{") -> JSONArray().put(JSONObject(limpia))
                else -> {
                    val inicioArr = limpia.indexOf('[')
                    val finArr = limpia.lastIndexOf(']')
                    val inicioObj = limpia.indexOf('{')
                    val finObj = limpia.lastIndexOf('}')
                    when {
                        inicioArr >= 0 && finArr > inicioArr ->
                            JSONArray(limpia.substring(inicioArr, finArr + 1))
                        inicioObj >= 0 && finObj > inicioObj ->
                            JSONArray().put(JSONObject(limpia.substring(inicioObj, finObj + 1)))
                        else -> return emptyList()
                    }
                }
            }

            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val tool = obj.optString("tool").trim()
                    if (tool.isEmpty()) continue
                    val argsObj = obj.optJSONObject("args") ?: JSONObject()
                    val mapaArgs = mutableMapOf<String, String>()
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = argsObj.opt(k)?.toString()?.trim().orEmpty()
                        if (v.isNotEmpty() && v != "null") {
                            mapaArgs[k] = v
                        }
                    }
                    add(LlamadaUtilidad(tool = tool, args = mapaArgs))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Genera el objeto `functionDeclarations` compatible con la API REST de Gemini
     * para habilitar Function Calling nativo en consultas conectadas (Nivel 2).
     */
    fun generarFunctionDeclarationsGemini(): JSONArray {
        val declaraciones = JSONArray()
        for (utilidad in catalogo) {
            // delegar_nube no hace falta cuando ya estamos en Gemini nube
            if (utilidad.nombre == TOOL_DELEGAR_NUBE) continue

            val properties = JSONObject()
            val required = JSONArray()

            for (param in utilidad.parametros) {
                val propObj = JSONObject().apply {
                    put("type", param.tipo)
                    put("description", param.descripcion)
                    if (param.enumValores.isNotEmpty()) {
                        put("enum", JSONArray(param.enumValores))
                    }
                }
                properties.put(param.nombre, propObj)
                if (param.requerido) required.put(param.nombre)
            }

            val parameters = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", properties)
                if (required.length() > 0) put("required", required)
            }

            declaraciones.put(
                JSONObject().apply {
                    put("name", utilidad.nombre)
                    put("description", utilidad.descripcion)
                    put("parameters", parameters)
                }
            )
        }
        return declaraciones
    }
}
