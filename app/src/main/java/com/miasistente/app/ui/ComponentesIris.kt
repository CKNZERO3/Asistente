package com.miasistente.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miasistente.app.comandos.ResultadoComando
import com.miasistente.app.dominio.AssistantState

/** Nombre con el que se presenta el asistente en la pantalla principal. */
const val NOMBRE_ASISTENTE = "Iris"

/** Colores de la pantalla principal (tema "noche profunda"). */
object PaletaIris {
    val FondoArriba = Color(0xFF0F0C1B)
    val FondoMorado = Color(0xFF1A102F)
    val FondoAzulNoche = Color(0xFF121838)
    val FondoMarino = Color(0xFF0B1026)
    val PanelLateral = Color(0xFF110E22)

    val BotonClaro = Color(0xFF7052FF)
    val BotonOscuro = Color(0xFF4D32E4)
    val Acento = Color(0xFF9D8CFF)

    val Vidrio = Color.White.copy(alpha = 0.08f)
    val BordeVidrio = Color.White.copy(alpha = 0.12f)
    val TextoSuave = Color.White.copy(alpha = 0.65f)
    val TextoTenue = Color.White.copy(alpha = 0.45f)
}

/** Fondo con degradado vertical + dos resplandores suaves (morado arriba a la derecha, azul abajo). */
@Composable
fun FondoNoche(modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        PaletaIris.FondoArriba,
                        PaletaIris.FondoMorado,
                        PaletaIris.FondoAzulNoche,
                        PaletaIris.FondoMarino
                    )
                )
            )
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF5B2DB5).copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.12f),
                        radius = size.width * 0.95f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2745D8).copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height),
                        radius = size.width * 1.15f
                    )
                )
            },
        content = contenido
    )
}

/** Panel translúcido con borde fino (efecto "vidrio"). */
fun Modifier.vidrio(forma: Shape = RoundedCornerShape(20.dp)): Modifier =
    this
        .background(PaletaIris.Vidrio, forma)
        .border(1.dp, PaletaIris.BordeVidrio, forma)

// ---------------------------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------------------------

/**
 * Izquierda: avatar del usuario con un punto de estado (verde = asistente activo).
 * Derecha: botón de ajustes; el punto morado avisa de que falta conceder algún permiso.
 */
@Composable
fun BarraSuperiorIris(
    activo: Boolean,
    avatarId: String,
    imagenPersonalizada: ImageBitmap?,
    hayAviso: Boolean,
    onAbrirAjustes: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar + punto brillante
            Box(Modifier.size(48.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PaletaIris.Vidrio)
                        .border(1.dp, PaletaIris.BordeVidrio, RoundedCornerShape(14.dp))
                ) {
                    AvatarVista(
                        id = avatarId,
                        imagenPersonalizada = imagenPersonalizada,
                        tamanoEmoji = 22.sp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val colorPunto = if (activo) Paleta.Verde else Color(0xFF6B6F7A)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(14.dp)
                        .drawBehind {
                            if (activo) {
                                drawCircle(colorPunto.copy(alpha = 0.35f), radius = size.minDimension)
                            }
                        }
                        .background(colorPunto, CircleShape)
                        .border(2.dp, PaletaIris.FondoArriba, CircleShape)
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = NOMBRE_ASISTENTE,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )

            // Botón de ajustes (abre el menú lateral)
            Box(Modifier.size(48.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(1.dp, PaletaIris.BordeVidrio, CircleShape)
                        .clickable(onClick = onAbrirAjustes),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Abrir ajustes",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (hayAviso) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .size(12.dp)
                            .background(PaletaIris.Acento, CircleShape)
                            .border(2.dp, PaletaIris.FondoArriba, CircleShape)
                    )
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    }
}

// ---------------------------------------------------------------------------------------------
// Cabecera: fecha, hora y clima
// ---------------------------------------------------------------------------------------------

@Composable
fun CabeceraFechaClima(fecha: String, hora: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(fecha, color = PaletaIris.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                text = hora,
                color = Color.White,
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
        }
        Spacer(Modifier.width(12.dp))
        ClimaWidget(modifier = Modifier.padding(top = 4.dp))
    }
}

/** Widget de clima minimalista. Aún no hay fuente de datos: muestra "no disponible". */
@Composable
fun ClimaWidget(
    modifier: Modifier = Modifier,
    titulo: String = "Clima no disponible",
    subtitulo: String = "Tu ubicación"
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = titulo,
                color = PaletaIris.TextoSuave,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = PaletaIris.TextoTenue,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(subtitulo, color = PaletaIris.TextoTenue, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Zona central
// ---------------------------------------------------------------------------------------------

@Composable
fun SaludoPrincipal(saludo: String) {
    Column {
        Text(saludo, color = PaletaIris.TextoSuave, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "¿En qué te puedo ayudar hoy?",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            ),
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Pregúntame lo que necesites para organizar tu día.",
            color = PaletaIris.TextoSuave,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
    }
}

/** Píldora con un punto de color y un texto corto de estado. */
@Composable
fun ChipEstado(texto: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .vidrio(RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(texto, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Último comando escrito y su respuesta. */
@Composable
fun TarjetaConversacion(
    comando: String,
    resultado: ResultadoComando?,
    procesando: Boolean,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .vidrio(RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tú",
                color = PaletaIris.Acento,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCerrar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar conversación",
                    tint = PaletaIris.TextoSuave,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(comando, color = Color.White, fontSize = 16.sp)

        Spacer(Modifier.height(12.dp))
        Text(
            NOMBRE_ASISTENTE,
            color = PaletaIris.Acento,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        when {
            resultado != null -> Text(
                text = (if (resultado.exito) "✔ " else "✖ ") + resultado.mensaje,
                color = if (resultado.exito) Paleta.Verde else Paleta.Rojo,
                fontSize = 16.sp
            )
            procesando -> Text(
                "Pensando…",
                color = PaletaIris.TextoSuave,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Barra de entrada flotante + botón de micrófono
// ---------------------------------------------------------------------------------------------

/**
 * Barra alargada con campo de texto y, a la derecha, el botón de micrófono.
 * Si hay texto escrito, el botón se convierte en "enviar".
 */
@Composable
fun BarraEntradaIris(
    texto: String,
    onTexto: (String) -> Unit,
    onEnviar: () -> Unit,
    estado: AssistantState,
    activo: Boolean,
    onMicrofono: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forma = RoundedCornerShape(28.dp)
    val hayTexto = texto.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PaletaIris.FondoMarino.copy(alpha = 0.60f), forma)
            .border(1.dp, PaletaIris.BordeVidrio, forma)
            .padding(start = 22.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (texto.isEmpty()) {
                Text(
                    text = "Pregúntale algo a $NOMBRE_ASISTENTE...",
                    color = PaletaIris.TextoTenue,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = texto,
                onValueChange = onTexto,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(PaletaIris.Acento),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onEnviar() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        BotonMicrofono(
            estado = estado,
            activo = activo,
            modoEnviar = hayTexto,
            onClick = if (hayTexto) onEnviar else onMicrofono
        )
    }
}

/**
 * Botón circular con degradado morado→azul. Reacciona al estado del asistente:
 *  - apagado:        quieto
 *  - IDLE (activo):  "respira" con un aro suave
 *  - LISTENING:      ondas que se expanden
 *  - THINKING:       arco que gira
 *  - SPEAKING:       latido con resplandor
 */
@Composable
fun BotonMicrofono(
    estado: AssistantState,
    activo: Boolean,
    modoEnviar: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transicion = rememberInfiniteTransition(label = "microfono")
    val onda = transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "onda"
    )
    val giro = transicion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "giro"
    )
    val aliento = transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aliento"
    )
    val latido = transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "latido"
    )

    val brillo = PaletaIris.Acento
    val animar = activo || estado == AssistantState.THINKING

    Box(
        modifier = modifier
            .size(52.dp)
            .drawBehind {
                if (!animar) return@drawBehind
                val radioBase = size.minDimension / 2f
                when (estado) {
                    AssistantState.LISTENING -> {
                        repeat(2) { k ->
                            val p = (onda.value + k * 0.5f) % 1f
                            drawCircle(
                                color = brillo.copy(alpha = (1f - p) * 0.55f),
                                radius = radioBase + p * 9.dp.toPx(),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    AssistantState.THINKING -> {
                        val r = radioBase + 5.dp.toPx()
                        rotate(degrees = giro.value, pivot = center) {
                            drawArc(
                                color = brillo,
                                startAngle = 0f,
                                sweepAngle = 110f,
                                useCenter = false,
                                topLeft = Offset(center.x - r, center.y - r),
                                size = Size(r * 2, r * 2),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                    AssistantState.SPEAKING -> {
                        drawCircle(
                            color = brillo.copy(alpha = 0.18f + 0.22f * latido.value),
                            radius = radioBase + 4.dp.toPx() + 5.dp.toPx() * latido.value
                        )
                    }
                    AssistantState.IDLE -> {
                        drawCircle(
                            color = brillo.copy(alpha = 0.40f * (1f - aliento.value)),
                            radius = radioBase + 2.dp.toPx() + 5.dp.toPx() * aliento.value,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
            .graphicsLayer {
                val escala = when {
                    !activo -> 1f
                    estado == AssistantState.SPEAKING -> 1f + 0.06f * latido.value
                    estado == AssistantState.LISTENING -> 1f + 0.04f * (1f - onda.value)
                    else -> 1f
                }
                scaleX = escala
                scaleY = escala
                alpha = if (activo || modoEnviar) 1f else 0.9f
            }
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(PaletaIris.BotonClaro, PaletaIris.BotonOscuro))
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = modoEnviar, label = "iconoBoton") { enviar ->
            Icon(
                imageVector = if (enviar) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                contentDescription = when {
                    enviar -> "Enviar"
                    activo -> "Detener asistente"
                    else -> "Activar asistente"
                },
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
