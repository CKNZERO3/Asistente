package com.miasistente.app.comandos

import kotlin.math.roundToInt

data class DatosClima(
    val lugar: String,
    val temperatura: Double,
    val sensacion: Double,
    val humedad: Int,
    val vientoKmh: Double,
    val codigo: Int,
    val maxima: Double,
    val minima: Double,
    val probabilidadLluvia: Int?
)

/** Texto en español a partir de los datos del clima (sin red, fácil de probar). */
object ClimaTexto {

    fun descripcion(codigo: Int): String = when (codigo) {
        0 -> "despejado"
        1 -> "mayormente despejado"
        2 -> "parcialmente nublado"
        3 -> "nublado"
        45, 48 -> "con niebla"
        51, 53, 55 -> "con llovizna"
        56, 57 -> "con llovizna helada"
        61 -> "con lluvia ligera"
        63 -> "con lluvia moderada"
        65 -> "con lluvia fuerte"
        66, 67 -> "con lluvia helada"
        71, 73, 75, 77 -> "con nieve"
        80, 81, 82 -> "con chubascos"
        85, 86 -> "con chubascos de nieve"
        95 -> "con tormenta"
        96, 99 -> "con tormenta y granizo"
        else -> "con condiciones variables"
    }

    fun resumen(d: DatosClima): String {
        fun n(x: Double) = x.roundToInt()
        val lluvia = d.probabilidadLluvia?.let { ", con $it % de probabilidad de lluvia" }.orEmpty()
        return "En ${d.lugar} hay ${n(d.temperatura)} °C y está ${descripcion(d.codigo)}. " +
            "Sensación térmica de ${n(d.sensacion)} °C. " +
            "Hoy se esperan entre ${n(d.minima)} y ${n(d.maxima)} °C$lluvia. " +
            "Humedad del ${d.humedad} % y viento de ${n(d.vientoKmh)} km/h."
    }
}
