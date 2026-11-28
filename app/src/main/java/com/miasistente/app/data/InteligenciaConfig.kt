package com.miasistente.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacena claves de API de forma cifrada mediante Android Keystore (AES-256-GCM),
 * sin añadir dependencias externas.
 */
class SecureStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun guardarApiKey(proveedorId: String, apiKey: String) {
        val limpia = apiKey.trim()
        if (limpia.isEmpty()) {
            eliminarApiKey(proveedorId)
            return
        }
        val cifrada = cifrar(limpia)
        prefs.edit().putString(clavePref(proveedorId), cifrada).apply()
    }

    fun obtenerApiKey(proveedorId: String): String {
        val almacenada = prefs.getString(clavePref(proveedorId), null) ?: return ""
        return descifrar(almacenada)
    }

    fun eliminarApiKey(proveedorId: String) {
        prefs.edit().remove(clavePref(proveedorId)).apply()
    }

    fun tieneApiKey(proveedorId: String): Boolean =
        obtenerApiKey(proveedorId).isNotBlank()

    private fun cifrar(textoPlano: String): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOLlave())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val datos = Base64.encodeToString(
            cipher.doFinal(textoPlano.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$datos"
    }

    private fun descifrar(payload: String): String = try {
        val partes = payload.split(":", limit = 2)
        if (partes.size != 2) return ""
        val iv = Base64.decode(partes[0], Base64.NO_WRAP)
        val datos = Base64.decode(partes[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, obtenerOLlave(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(datos), Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }

    private fun obtenerOLlave(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existente = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existente != null) return existente

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun clavePref(proveedorId: String) = "api_key_$proveedorId"

    companion object {
        private const val PREFS_NAME = "iris_secure_keys"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "iris_api_keys_aes_gcm"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
    }
}

/** Perfiles adaptativos de modelos locales (.litertlm) según la capacidad del hardware. */
enum class PerfilModeloLocal(
    val id: String,
    val titulo: String,
    val modeloSugerido: String,
    val descripcion: String,
    val ramTotalMinimaMb: Long,
    val ramDisponibleMinimaMb: Long,
    val almacenamientoMinimoMb: Long
) {
    BASICO(
        id = "basico",
        titulo = "Básico Inteligente",
        modeloSugerido = "Gemma 3 1B int4 (~550 MB)",
        descripcion = "Utilidades, notas, memoria local y enrutamiento rápido en gama de entrada.",
        ramTotalMinimaMb = 0L,
        ramDisponibleMinimaMb = 0L,
        almacenamientoMinimoMb = 900L
    ),
    INTERMEDIO(
        id = "intermedio",
        titulo = "Intermedio Equilibrado",
        modeloSugerido = "Gemma 3n E2B / Gemma 3 4B int4 (~1.8 GB)",
        descripcion = "Redacción fluida, resúmenes offline y comprensión contextual ampliada.",
        ramTotalMinimaMb = 5_000L,
        ramDisponibleMinimaMb = 1_800L,
        almacenamientoMinimoMb = 2_800L
    ),
    AVANZADO(
        id = "avanzado",
        titulo = "Avanzado Pro",
        modeloSugerido = "Gemma 3n E4B int4 (~3.5 GB)",
        descripcion = "Razonamiento profundo offline, documentos largos y tareas complejas.",
        ramTotalMinimaMb = 9_500L,
        ramDisponibleMinimaMb = 3_800L,
        almacenamientoMinimoMb = 5_000L
    );

    companion object {
        fun desdeId(id: String?): PerfilModeloLocal =
            entries.firstOrNull { it.id == id } ?: BASICO
    }
}

/** Resultado del diagnóstico de hardware y compatibilidad de modelos locales. */
data class DiagnosticoHardware(
    val ramTotalMb: Long,
    val ramDisponibleMb: Long,
    val nucleosCpu: Int,
    val almacenamientoLibreMb: Long,
    val perfilRecomendado: PerfilModeloLocal
) {
    fun esCompatible(perfil: PerfilModeloLocal): Boolean =
        motivoIncompatibilidad(perfil) == null

    fun motivoIncompatibilidad(perfil: PerfilModeloLocal): String? {
        if (perfil == PerfilModeloLocal.BASICO) return null
        if (ramTotalMb < perfil.ramTotalMinimaMb) {
            val reqGb = String.format(java.util.Locale.US, "%.0f", perfil.ramTotalMinimaMb / 1024.0)
            return "Requiere al menos $reqGb GB de RAM total para evitar cierres por memoria."
        }
        if (ramDisponibleMb < perfil.ramDisponibleMinimaMb) {
            return "RAM libre insuficiente (mínimo ${perfil.ramDisponibleMinimaMb} MB disponibles)."
        }
        if (almacenamientoLibreMb < perfil.almacenamientoMinimoMb) {
            val reqEspacio = String.format(java.util.Locale.US, "%.1f", perfil.almacenamientoMinimoMb / 1024.0)
            return "Espacio insuficiente en almacenamiento (necesitas $reqEspacio GB libres)."
        }
        return null
    }
}

/** Inspecciona las capacidades reales del teléfono (RAM, CPU y almacenamiento). */
object HardwareDispositivo {

    fun inspeccionar(context: Context): DiagnosticoHardware {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val ramTotalMb = if (memInfo.totalMem > 0L) memInfo.totalMem / (1024L * 1024L) else 4_096L
        val ramDisponibleMb = if (memInfo.availMem > 0L) memInfo.availMem / (1024L * 1024L) else 1_500L
        val nucleosCpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val almacenamientoLibreMb = runCatching {
            StatFs(appContext.filesDir.absolutePath).availableBytes / (1024L * 1024L)
        }.getOrDefault(8_192L)

        return evaluar(
            ramTotalMb = ramTotalMb,
            ramDisponibleMb = ramDisponibleMb,
            nucleosCpu = nucleosCpu,
            almacenamientoLibreMb = almacenamientoLibreMb
        )
    }

    fun evaluar(
        ramTotalMb: Long,
        ramDisponibleMb: Long,
        nucleosCpu: Int,
        almacenamientoLibreMb: Long
    ): DiagnosticoHardware {
        val base = DiagnosticoHardware(
            ramTotalMb = ramTotalMb,
            ramDisponibleMb = ramDisponibleMb,
            nucleosCpu = nucleosCpu,
            almacenamientoLibreMb = almacenamientoLibreMb,
            perfilRecomendado = PerfilModeloLocal.BASICO
        )
        val recomendado = when {
            base.esCompatible(PerfilModeloLocal.AVANZADO) && nucleosCpu >= 6 -> PerfilModeloLocal.AVANZADO
            base.esCompatible(PerfilModeloLocal.INTERMEDIO) && nucleosCpu >= 6 -> PerfilModeloLocal.INTERMEDIO
            else -> PerfilModeloLocal.BASICO
        }
        return base.copy(perfilRecomendado = recomendado)
    }
}

/** Proveedores de modelos de IA soportados por Iris. */
enum class ProveedorIA(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val requiereApiKey: Boolean,
    val requiereEndpoint: Boolean,
    val endpointDefecto: String,
    val modeloDefecto: String
) {
    IRIS_NUBE(
        id = "iris_nube",
        titulo = "Iris Cloud (Predeterminado)",
        descripcion = "Proxy seguro de Iris con rotación automática de modelos Gemini.",
        requiereApiKey = false,
        requiereEndpoint = false,
        endpointDefecto = "",
        modeloDefecto = "gemini-3.5-flash-lite"
    ),
    GEMINI_DIRECTO(
        id = "gemini_directo",
        titulo = "Google Gemini API (Clave propia)",
        descripcion = "Conecta directamente con Google AI Studio usando tu propia API Key.",
        requiereApiKey = true,
        requiereEndpoint = false,
        endpointDefecto = "https://generativelanguage.googleapis.com/v1beta/models",
        modeloDefecto = "gemini-2.5-flash"
    ),
    OPENAI_COMPATIBLE(
        id = "openai_compatible",
        titulo = "OpenAI / Groq / Compatible",
        descripcion = "Cualquier API compatible con el formato OpenAI Chat Completions.",
        requiereApiKey = true,
        requiereEndpoint = true,
        endpointDefecto = "https://api.openai.com/v1/chat/completions",
        modeloDefecto = "gpt-4o-mini"
    ),
    OLLAMA_LOCAL(
        id = "ollama_local",
        titulo = "Ollama / Servidor Local",
        descripcion = "Servidor local o en red doméstica compatible con OpenAI.",
        requiereApiKey = false,
        requiereEndpoint = true,
        endpointDefecto = "http://127.0.0.1:11434/v1/chat/completions",
        modeloDefecto = "gemma3:4b"
    ),
    SOLO_LOCAL(
        id = "solo_local",
        titulo = "Solo Modelo Local (.litertlm)",
        descripcion = "Ejecución 100 % offline en el teléfono sin salir a internet.",
        requiereApiKey = false,
        requiereEndpoint = false,
        endpointDefecto = "",
        modeloDefecto = "modelo_local.litertlm"
    );

    companion object {
        fun desdeId(id: String?): ProveedorIA =
            entries.firstOrNull { it.id == id } ?: IRIS_NUBE
    }
}

/** Estrategia de reparto entre modelo local (.litertlm) y nube. */
enum class ModoEjecucionLocal(
    val id: String,
    val titulo: String,
    val descripcion: String
) {
    HIBRIDO(
        id = "hibrido",
        titulo = "Híbrido inteligente (Recomendado)",
        descripcion = "Tareas ligeras y memoria personal en local; consultas complejas en la nube."
    ),
    PRIORIZAR_LOCAL(
        id = "priorizar_local",
        titulo = "Priorizar modelo local",
        descripcion = "Usa el modelo .litertlm instalado siempre que no se requiera internet en tiempo real."
    ),
    SOLO_NUBE(
        id = "solo_nube",
        titulo = "Priorizar nube",
        descripcion = "Usa el proveedor en la nube siempre que haya conexión a internet."
    );

    companion object {
        fun desdeId(id: String?): ModoEjecucionLocal =
            entries.firstOrNull { it.id == id } ?: HIBRIDO
    }
}

/** Preferencia de destino por defecto al guardar notas sin especificar lugar. */
enum class DestinoNotasPredeterminado(
    val id: String,
    val titulo: String,
    val descripcion: String
) {
    AUTO(
        id = "auto",
        titulo = "Aprendizaje automático",
        descripcion = "Aprende si prefieres Iris Notebook o una app externa según tu uso."
    ),
    CUADERNO(
        id = "cuaderno",
        titulo = "Siempre Iris Notebook",
        descripcion = "Guarda tus notas directamente en tus cuadernos locales sin abrir el selector."
    ),
    EXTERNO(
        id = "externo",
        titulo = "Siempre app externa",
        descripcion = "Envía tus notas a Google Keep o al selector de aplicaciones."
    );

    companion object {
        fun desdeId(id: String?): DestinoNotasPredeterminado =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Regla o instrucción personalizada en lista para moldear el comportamiento de Iris. */
data class ReglaComportamiento(
    val id: String,
    val titulo: String,
    val instruccion: String,
    val activa: Boolean = true
)

/**
 * Configuración persistente de Inteligencia Personalizada (Sprint 3):
 * - Proveedores de IA, modelos locales (.litertlm) y almacenamiento cifrado de API Keys.
 * - Conectores externos (Google Keep, Calendario, Archivos/Drive, Búsqueda Web).
 * - Listas de instrucciones personalizadas que aplican tanto en local como en la nube.
 */
class InteligenciaConfig(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureStorage = SecureStorage(appContext)

    var proveedorActivo: ProveedorIA
        get() = ProveedorIA.desdeId(prefs.getString(KEY_PROVEEDOR, null))
        set(valor) {
            prefs.edit().putString(KEY_PROVEEDOR, valor.id).apply()
        }

    var perfilModeloLocal: PerfilModeloLocal
        get() {
            val guardado = prefs.getString(KEY_PERFIL_MODELO_LOCAL, null)
            return if (guardado != null) {
                PerfilModeloLocal.desdeId(guardado)
            } else {
                HardwareDispositivo.inspeccionar(appContext).perfilRecomendado
            }
        }
        set(valor) {
            prefs.edit().putString(KEY_PERFIL_MODELO_LOCAL, valor.id).apply()
        }

    var modoEjecucionLocal: ModoEjecucionLocal
        get() = ModoEjecucionLocal.desdeId(prefs.getString(KEY_MODO_LOCAL, null))
        set(valor) {
            prefs.edit().putString(KEY_MODO_LOCAL, valor.id).apply()
        }

    var destinoNotas: DestinoNotasPredeterminado
        get() = DestinoNotasPredeterminado.desdeId(prefs.getString(KEY_DESTINO_NOTAS, null))
        set(valor) {
            prefs.edit().putString(KEY_DESTINO_NOTAS, valor.id).apply()
        }

    var conectorKeep: Boolean
        get() = prefs.getBoolean(KEY_CONECTOR_KEEP, true)
        set(valor) {
            prefs.edit().putBoolean(KEY_CONECTOR_KEEP, valor).apply()
        }

    var conectorCalendario: Boolean
        get() = prefs.getBoolean(KEY_CONECTOR_CALENDARIO, true)
        set(valor) {
            prefs.edit().putBoolean(KEY_CONECTOR_CALENDARIO, valor).apply()
        }

    var conectorArchivosDrive: Boolean
        get() = prefs.getBoolean(KEY_CONECTOR_DRIVE, true)
        set(valor) {
            prefs.edit().putBoolean(KEY_CONECTOR_DRIVE, valor).apply()
        }

    var conectorBusquedaWeb: Boolean
        get() = prefs.getBoolean(KEY_CONECTOR_WEB, true)
        set(valor) {
            prefs.edit().putBoolean(KEY_CONECTOR_WEB, valor).apply()
        }

    var nombreModeloLocalInstalado: String
        get() = prefs.getString(KEY_NOMBRE_MODELO_LOCAL, null).orEmpty()
        set(valor) {
            prefs.edit().putString(KEY_NOMBRE_MODELO_LOCAL, valor.trim()).apply()
        }

    fun urlModeloPerfil(perfil: PerfilModeloLocal): String =
        prefs.getString("url_modelo_perfil_${perfil.id}", null).orEmpty()

    fun guardarUrlModeloPerfil(perfil: PerfilModeloLocal, url: String) {
        prefs.edit().putString("url_modelo_perfil_${perfil.id}", url.trim()).apply()
    }

    fun endpointProveedor(proveedor: ProveedorIA): String =
        prefs.getString("endpoint_${proveedor.id}", null)
            ?.takeIf { it.isNotBlank() }
            ?: proveedor.endpointDefecto

    fun guardarEndpointProveedor(proveedor: ProveedorIA, endpoint: String) {
        prefs.edit().putString("endpoint_${proveedor.id}", endpoint.trim()).apply()
    }

    fun modeloProveedor(proveedor: ProveedorIA): String =
        prefs.getString("modelo_${proveedor.id}", null)
            ?.takeIf { it.isNotBlank() }
            ?: proveedor.modeloDefecto

    fun guardarModeloProveedor(proveedor: ProveedorIA, modelo: String) {
        prefs.edit().putString("modelo_${proveedor.id}", modelo.trim()).apply()
    }

    fun obtenerApiKey(proveedor: ProveedorIA): String =
        secureStorage.obtenerApiKey(proveedor.id)

    fun guardarApiKey(proveedor: ProveedorIA, apiKey: String) {
        secureStorage.guardarApiKey(proveedor.id, apiKey)
    }

    fun tieneApiKey(proveedor: ProveedorIA): Boolean =
        secureStorage.tieneApiKey(proveedor.id)

    // --- Instrucciones y listas de comportamiento -----------------------------------------

    fun obtenerReglas(): List<ReglaComportamiento> =
        deserializarReglas(prefs.getString(KEY_REGLAS, null))

    fun guardarReglas(reglas: List<ReglaComportamiento>) {
        prefs.edit().putString(KEY_REGLAS, serializarReglas(reglas)).apply()
    }

    fun agregarRegla(titulo: String, instruccion: String): List<ReglaComportamiento> {
        val tituloLimpio = titulo.trim().take(60)
        val textoLimpio = instruccion.trim().take(400)
        if (tituloLimpio.isEmpty() || textoLimpio.isEmpty()) return obtenerReglas()

        val actualizadas = obtenerReglas() + ReglaComportamiento(
            id = UUID.randomUUID().toString(),
            titulo = tituloLimpio,
            instruccion = textoLimpio,
            activa = true
        )
        guardarReglas(actualizadas)
        return actualizadas
    }

    fun alternarRegla(id: String, activa: Boolean): List<ReglaComportamiento> {
        val actualizadas = obtenerReglas().map {
            if (it.id == id) it.copy(activa = activa) else it
        }
        guardarReglas(actualizadas)
        return actualizadas
    }

    fun eliminarRegla(id: String): List<ReglaComportamiento> {
        val actualizadas = obtenerReglas().filterNot { it.id == id }
        guardarReglas(actualizadas)
        return actualizadas
    }

    fun construirBloqueInstrucciones(): String =
        compilarInstruccionesActivas(obtenerReglas())

    companion object {
        private const val PREFS_NAME = "iris_inteligencia_prefs"
        private const val KEY_PROVEEDOR = "proveedor_activo"
        private const val KEY_PERFIL_MODELO_LOCAL = "perfil_modelo_local"
        private const val KEY_MODO_LOCAL = "modo_ejecucion_local"
        private const val KEY_DESTINO_NOTAS = "destino_notas_predeterminado"
        private const val KEY_CONECTOR_KEEP = "conector_keep"
        private const val KEY_CONECTOR_CALENDARIO = "conector_calendario"
        private const val KEY_CONECTOR_DRIVE = "conector_drive"
        private const val KEY_CONECTOR_WEB = "conector_busqueda_web"
        private const val KEY_NOMBRE_MODELO_LOCAL = "nombre_modelo_local"
        private const val KEY_REGLAS = "reglas_comportamiento_json"

        fun serializarReglas(reglas: List<ReglaComportamiento>): String {
            val arr = JSONArray()
            reglas.forEach { regla ->
                arr.put(
                    JSONObject().apply {
                        put("id", regla.id)
                        put("titulo", regla.titulo)
                        put("instruccion", regla.instruccion)
                        put("activa", regla.activa)
                    }
                )
            }
            return arr.toString()
        }

        fun deserializarReglas(rawJson: String?): List<ReglaComportamiento> {
            if (rawJson.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(rawJson)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").ifBlank { "regla_$i" }
                        val titulo = obj.optString("titulo").trim()
                        val instruccion = obj.optString("instruccion").trim()
                        val activa = obj.optBoolean("activa", true)
                        if (titulo.isNotEmpty() && instruccion.isNotEmpty()) {
                            add(ReglaComportamiento(id, titulo, instruccion, activa))
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun compilarInstruccionesActivas(reglas: List<ReglaComportamiento>): String {
            val activas = reglas.filter { it.activa && it.instruccion.isNotBlank() }
            if (activas.isEmpty()) return ""
            return activas.joinToString("\n") { regla ->
                "- [${regla.titulo}]: ${regla.instruccion}"
            }
        }

        fun plantillasSugeridas(): List<Pair<String, String>> = listOf(
            "Respuestas breves" to "Ve directo al grano en máximo 3 o 4 líneas salvo que pida detalle.",
            "Formato en viñetas" to "Organiza siempre las explicaciones y pasos usando listas con viñetas claras.",
            "Modo técnico" to "Incluye ejemplos de código limpios, comandos exactos y consideraciones técnicas.",
            "Tono profesional" to "Usa un registro formal, preciso y estructurado."
        )
    }
}
