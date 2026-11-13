package com.miasistente.app.comandos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Consulta el clima actual en Open-Meteo (gratuito, sin clave). Solo se envía el nombre de la
 * ciudad y, después, sus coordenadas; no se comparte la ubicación del teléfono.
 */
class ClimaOpenMeteo {

    private companion object {
        const val GEOCODIFICACION = "https://geocoding-api.open-meteo.com/v1/search"
        const val PRONOSTICO = "https://api.open-meteo.com/v1/forecast"
        const val TIMEOUT_MS = 8_000
    }

    /** Devuelve null si no encuentra la ciudad; lanza [IOException] si falla la red. */
    suspend fun consultar(ciudad: String): DatosClima? = withContext(Dispatchers.IO) {
        val nombre = ciudad.trim().take(80)
        if (nombre.isEmpty()) return@withContext null

        val geo = JSONObject(
            leer("$GEOCODIFICACION?name=${URLEncoder.encode(nombre, "UTF-8")}&count=1&language=es&format=json")
        )
        val lugar = geo.optJSONArray("results")?.optJSONObject(0) ?: return@withContext null
        val etiqueta = listOf(lugar.optString("name"), lugar.optString("country"))
            .filter { it.isNotBlank() }
            .joinToString(", ")

        val url = "$PRONOSTICO?latitude=${lugar.getDouble("latitude")}&longitude=${lugar.getDouble("longitude")}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=1"
        val datos = JSONObject(leer(url))
        val actual = datos.getJSONObject("current")
        val dia = datos.getJSONObject("daily")

        DatosClima(
            lugar = etiqueta.ifEmpty { nombre },
            temperatura = actual.getDouble("temperature_2m"),
            sensacion = actual.getDouble("apparent_temperature"),
            humedad = actual.getInt("relative_humidity_2m"),
            vientoKmh = actual.getDouble("wind_speed_10m"),
            codigo = actual.getInt("weather_code"),
            maxima = dia.getJSONArray("temperature_2m_max").getDouble(0),
            minima = dia.getJSONArray("temperature_2m_min").getDouble(0),
            probabilidadLluvia = dia.optJSONArray("precipitation_probability_max")
                ?.takeIf { it.length() > 0 && !it.isNull(0) }?.getInt(0)
        )
    }

    private fun leer(direccion: String): String {
        val conexion = (URL(direccion).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conexion.responseCode !in 200..299) {
                throw IOException("Open-Meteo respondió HTTP ${conexion.responseCode}")
            }
            return conexion.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conexion.disconnect()
        }
    }
}
