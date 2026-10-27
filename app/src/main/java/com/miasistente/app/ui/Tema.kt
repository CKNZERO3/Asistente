package com.miasistente.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores de la app (tema oscuro estilo Material You). */
object Paleta {
    val Fondo = Color(0xFF0E1013)
    val Tarjeta = Color(0xFF1B1E24)
    val TarjetaAlta = Color(0xFF262A32)
    val Contorno = Color(0xFF444A55)

    val Texto = Color(0xFFE3E3E8)
    val TextoSuave = Color(0xFF9AA0AA)

    val Primario = Color(0xFFA8C7FA)
    val SobrePrimario = Color(0xFF062E6F)

    val Verde = Color(0xFF6DD58C)
    val Rojo = Color(0xFFFF8A80)
    val Ambar = Color(0xFFFFC857)
    val Azul = Color(0xFF8AB4F8)
    val Morado = Color(0xFFD0BCFF)
}

/** Tema oscuro fijo (el diseño es oscuro; no sigue el modo claro del teléfono). */
@Composable
fun AsistenteTema(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Paleta.Primario,
            onPrimary = Paleta.SobrePrimario,
            background = Paleta.Fondo,
            onBackground = Paleta.Texto,
            surface = Paleta.Tarjeta,
            onSurface = Paleta.Texto,
            surfaceVariant = Paleta.TarjetaAlta,
            onSurfaceVariant = Paleta.TextoSuave,
            outline = Paleta.Contorno,
            error = Paleta.Rojo
        ),
        content = content
    )
}
