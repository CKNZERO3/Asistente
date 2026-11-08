// DayNightToggle.kt
package com.miasistente.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------- Paleta (ajustada al estilo Iris: violeta/índigo) ----------
private val DayTrack = Color(0xFFEDEAF5)
private val NightTrack = Color(0xFF0E0B22)
private val DayThumb = Color(0xFFFFFFFF)
private val NightThumb = Color(0xFF2A2450)
private val DayGlow = Color(0xFFFFB74D)
private val NightGlow = Color(0xFFA78BFA)
private val SunColor = Color(0xFFF59E0B)
private val MoonColor = Color(0xFFE9E3FF)

@Composable
fun DayNightToggle(
    isNight: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 120.dp,
    trackHeight: Dp = 64.dp,
) {
    val haptic = LocalHapticFeedback.current
    val padding = 6.dp
    val thumbSize = trackHeight - padding * 2

    // Progreso principal (0 = día, 1 = noche) con rebote suave
    val progress by animateFloatAsState(
        targetValue = if (isNight) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 170f),
        label = "progress"
    )
    // Estallido de partículas al cambiar
    val burst = remember { Animatable(0f) }
    LaunchedEffect(isNight) {
        burst.snapTo(0f)
        burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }

    val p = progress.coerceIn(0f, 1f)
    val trackColor by animateColorAsState(
        if (isNight) NightTrack else DayTrack, tween(450), label = "track"
    )
    val thumbColor by animateColorAsState(
        if (isNight) NightThumb else DayThumb, tween(450), label = "thumb"
    )
    val glowColor = lerp(DayGlow, NightGlow, p)

    val travel = trackWidth - thumbSize - padding * 2
    // Efecto "squash & stretch" durante el movimiento
    val stretch = 1f + 0.14f * sin(PI.toFloat() * p)

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(trackColor)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = if (isNight) 0.55f else 0.14f),
                        Color.White.copy(alpha = if (isNight) 0.06f else 0.9f)
                    )
                ),
                RoundedCornerShape(trackHeight / 2)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isNight)
            }
    ) {
        // Sombra interior del carril
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = if (p > 0.5f) 0.45f else 0.10f),
                        Color.Transparent
                    ),
                    endY = size.height * 0.55f
                ),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }

        // Pulgar
        Box(
            modifier = Modifier
                .offset(x = padding + travel * progress, y = padding)
                .size(thumbSize)
                .graphicsLayer { scaleX = stretch; scaleY = 1f / (1f + (stretch - 1f) * 0.5f) }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(thumbColor, Color.White, 0.18f),
                            thumbColor
                        )
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = lerpF(0.9f, 0.14f, p)),
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGlowArc(glowColor, p)
                drawSun(p, burst.value)
                drawMoon(p, burst.value)
                drawParticles(burst.value, lerp(SunColor, MoonColor, p))
            }
        }
    }
}

// ---------- Arco luminoso que rodea la esquina del pulgar ----------
private fun DrawScope.drawGlowArc(color: Color, p: Float) {
    val r = 20.dp.toPx()
    val sweepStops = arrayOf(
        0f to Color.Transparent,
        0.04f to color.copy(alpha = 0.35f),
        0.13f to color,
        0.21f to color.copy(alpha = 0.35f),
        0.25f to Color.Transparent,
        1f to Color.Transparent
    )
    val brush = Brush.sweepGradient(colorStops = sweepStops, center = center)
    rotate(degrees = lerpF(-90f, 90f, p), pivot = center) {
        // Halo difuso (capas)
        listOf(9.dp to 0.10f, 6.dp to 0.18f, 3.5.dp to 0.30f).forEach { (w, a) ->
            drawRoundRect(
                brush = brush,
                cornerRadius = CornerRadius(r),
                style = Stroke(width = w.toPx(), cap = StrokeCap.Round),
                alpha = a
            )
        }
        // Línea nítida
        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(r),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ---------- Sol moderno: núcleo redondeado + rayos con cap redondo ----------
private fun DrawScope.drawSun(p: Float, burst: Float) {
    val vis = 1f - p
    if (vis <= 0.01f) return
    val c = center
    val unit = size.minDimension / 2f
    scale(scale = lerpF(1f, 0.3f, p), pivot = c) {
        rotate(degrees = p * 120f, pivot = c) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFD166), SunColor), center = c, radius = unit * 0.38f
                ),
                radius = unit * 0.30f,
                center = c,
                alpha = vis
            )
            for (i in 0 until 8) {
                val a = (i * 45f) * (PI.toFloat() / 180f)
                val long = if (i % 2 == 0) 0.22f else 0.14f
                val s = Offset(c.x + cos(a) * unit * 0.50f, c.y + sin(a) * unit * 0.50f)
                val e = Offset(
                    c.x + cos(a) * unit * (0.50f + long),
                    c.y + sin(a) * unit * (0.50f + long)
                )
                drawLine(
                    color = SunColor,
                    start = s, end = e,
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    alpha = vis
                )
            }
        }
    }
}

// ---------- Luna creciente suave + destellos ----------
private fun DrawScope.drawMoon(p: Float, burst: Float) {
    val vis = p
    if (vis <= 0.01f) return
    val c = center
    val unit = size.minDimension / 2f
    scale(scale = lerpF(0.3f, 1f, p), pivot = c) {
        rotate(degrees = (p - 1f) * 90f, pivot = c) {
            val r = unit * 0.46f
            val outer = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c, r))
            }
            val cut = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        Offset(c.x + r * 0.55f, c.y - r * 0.30f), r * 0.88f
                    )
                )
            }
            val crescent = Path.combine(PathOperation.Difference, outer, cut)
            drawPath(
                path = crescent,
                brush = Brush.linearGradient(
                    listOf(Color.White, MoonColor),
                    start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r)
                ),
                alpha = vis
            )
        }
        // Estrellas de 4 puntas
        drawSparkle(Offset(c.x + unit * 0.52f, c.y - unit * 0.50f), unit * 0.16f, vis)
        drawSparkle(Offset(c.x + unit * 0.30f, c.y - unit * 0.18f), unit * 0.09f, vis * 0.8f)
    }
}

private fun DrawScope.drawSparkle(at: Offset, r: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        quadraticTo(at.x, at.y, at.x + r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - r)
        close()
    }
    drawPath(path, MoonColor, alpha = alpha)
}

// ---------- Partículas que se dispersan al cambiar de icono ----------
private fun DrawScope.drawParticles(t: Float, color: Color) {
    if (t <= 0f || t >= 1f) return
    val c = center
    val unit = size.minDimension / 2f
    val alpha = sin(PI.toFloat() * t)
    for (i in 0 until 18) {
        val a = (i * 20f + (i % 3) * 7f) * (PI.toFloat() / 180f)
        val dist = unit * (0.15f + 0.85f * t) * (0.7f + (i % 4) * 0.12f)
        drawCircle(
            color = color,
            radius = (1.2f + (i % 3) * 0.6f).dp.toPx() * (1f - t * 0.5f),
            center = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist),
            alpha = alpha * 0.9f
        )
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
