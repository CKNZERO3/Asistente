package com.miasistente.app.audio

import android.util.Log
import com.miasistente.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CancionReconocida(
    val artista: String,
    val titulo: String,
    val album: String?,
    val enlaceSpotify: String?
)

class AudDClient {

    companion object {
        private const val TAG = "AudDClient"
        // URL de tu Cloudflare Worker apuntando al endpoint de reconocimiento
        private const val URL_WORKER = "https://asistente.cristian-nava-ruido.workers.dev/recognize"
    }

    suspend fun reconocerCancion(archivoAudio: File): Result<CancionReconocida?> = withContext(Dispatchers.IO) {
        try {
            val boundary = "Boundary-${System.currentTimeMillis()}"
            val conexion = (URL(URL_WORKER).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                if (BuildConfig.APP_TOKEN.isNotBlank()) setRequestProperty("X-App-Token", BuildConfig.APP_TOKEN)
            }

            conexion.outputStream.use { outputStream ->
                val writer = outputStream.bufferedWriter(Charsets.UTF_8)
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${archivoAudio.name}\"\r\n")
                writer.write("Content-Type: audio/mp3\r\n\r\n")
                writer.flush()

                archivoAudio.inputStream().use { it.copyTo(outputStream) }
                outputStream.flush()

                writer.write("\r\n--$boundary--\r\n")
                writer.flush()
            }

            val codigo = conexion.responseCode
            if (codigo !in 200..299) {
                return@withContext Result.failure(IOException("Error HTTP en reconocimiento: $codigo"))
            }

            val respuestaJson = conexion.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(respuestaJson)

            val status = json.optString("status")
            if (status != "success") {
                return@withContext Result.success(null)
            }

            val resultObj = json.optJSONObject("result") ?: return@withContext Result.success(null)
            val artista = resultObj.optString("artist", "Desconocido")
            val titulo = resultObj.optString("title", "Desconocida")
            val album = resultObj.optString("album").takeIf { !it.isNullOrBlank() }
            
            val spotifyObj = resultObj.optJSONObject("spotify")
            val enlaceSpotify = spotifyObj?.optJSONObject("external_urls")?.optString("spotify")

            Result.success(CancionReconocida(artista, titulo, album, enlaceSpotify))
        } catch (e: Exception) {
            Log.e(TAG, "Error reconociendo canción", e)
            Result.failure(e)
        }
    }
}
