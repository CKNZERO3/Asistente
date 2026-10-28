package com.miasistente.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.miasistente.app.BuildConfig

/**
 * Guarda datos sensibles (por ahora, la API Key de Gemini) cifrados en el teléfono.
 *
 * - La clave de cifrado vive en el Android Keystore, no en el código ni en el APK.
 * - Todo el acceso a secretos pasa por esta clase: si más adelante cambiamos la
 *   tecnología de cifrado, el resto de la app no se entera.
 *
 * Uso:
 *   val storage = SecureStorage(context)
 *   storage.guardarApiKey("tu-clave")
 *   val clave = storage.obtenerApiKey()   // null si no hay ninguna guardada
 */
class SecureStorage(context: Context) {

    private val appContext = context.applicationContext

    // "lazy": se crea la primera vez que se usa, no al instanciar la clase
    private val prefs: SharedPreferences by lazy { crearPrefs() }

    init {
        // Zero-configuration fallback: auto-injects if defined in local.properties / BuildConfig
        if (!tieneApiKey() && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            guardarApiKey(BuildConfig.GEMINI_API_KEY)
        }
    }

    /** Guarda la API Key (se eliminan espacios al inicio y al final). */
    fun guardarApiKey(clave: String) {
        prefs.edit().putString(KEY_GEMINI_API, clave.trim()).apply()
    }

    /** Devuelve la API Key o null si no hay ninguna guardada. */
    fun obtenerApiKey(): String? =
        prefs.getString(KEY_GEMINI_API, null)?.takeIf { it.isNotBlank() }

    fun tieneApiKey(): Boolean = obtenerApiKey() != null

    fun borrarApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    private fun crearPrefs(): SharedPreferences =
        try {
            abrirPrefsCifradas()
        } catch (e: Exception) {
            // En algunos teléfonos el Keystore puede quedar corrupto (por ejemplo tras
            // restaurar una copia de seguridad). Si pasa, se borra el archivo y se
            // recrea: se pierde la clave guardada y habrá que volver a ingresarla.
            appContext.deleteSharedPreferences(FILE_NAME)
            abrirPrefsCifradas()
        }

    private fun abrirPrefsCifradas(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val FILE_NAME = "asistente_secure_prefs"
        const val KEY_GEMINI_API = "gemini_api_key"
    }
}
