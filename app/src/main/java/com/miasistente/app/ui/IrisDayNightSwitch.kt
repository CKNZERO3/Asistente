package com.miasistente.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SwDayTrack = Color(0xFFEDEAF5)
private val SwNightTrack = Color(0xFF0E0B22)
private val SwDayThumb = Color(0xFFFFFFFF)
private val SwNightThumb = Color(0xFF2A2450)
private val SwDayGlow = Color(0xFFFFB74D)
private val SwNightGlow = Color(0xFFA78BFA)
private val SwSun = Color(0xFFF59E0B)
private val SwMoon = Color(0xFFE9E3FF)

@Composable
fun IrisDayNightSwitch(
    isNight: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 120.dp,
    trackHeight: Dp = 64.dp,
) {
    val haptic = LocalHapticFeedback.current
    val padding = 6.dp
    val thumbSize = trackHeight - padding * 2
    val thumbCorner = thumbSize * 0.34f

    val progress by animateFloatAsState(
        targetValue = if (isNight) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 170f),
        label = "switch-progress"
    )
    val burst = remember { Animatable(0f) }
    LaunchedEffect(isNight) {
        burst.snapTo(0f)
        burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }

    val p = progress.coerceIn(0f, 1f)
    val trackColor by animateColorAsState(
        if (isNight) SwNightTrack else SwDayTrack, tween(450), label = "switch-track"
    )
    val thumbColor by animateColorAsState(
        if (isNight) SwNightThumb else SwDayThumb, tween(450), label = "switch-thumb"
    )
    val glowColor = lerp(SwDayGlow, SwNightGlow, p)

    val density = LocalDensity.current
    val travelPx = with(density) { (trackWidth - thumbSize - padding * 2).toPx() }

    BoxWithConstraints(
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
            .pointerInput(isNight) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // Si pasó la mitad del recorrido, cambia de estado
                        if (progress > 0.5f != isNight) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle(!isNight)
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    // Aquí podrías manejar un estado local de arrastre si deseas fluidez extrema, 
                    // pero con el spring actual el toque y arrastre se comportan natural.
                    if (travelPx > 0) {
                        val newProgress = (progress + (dragAmount / travelPx)).coerceIn(0f, 1f)
                        if (newProgress > 0.5f != isNight && (newProgress == 1f || newProgress == 0f)) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle(newProgress > 0.5f)
                        }
                    }
                }
            }
    ) {
        val travel = trackWidth - thumbSize - padding * 2
        val stretch = 1f + 0.14f * sin(PI.toFloat() * p)

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

        Box(
            modifier = Modifier
                .offset(x = padding + travel * progress, y = padding)
                .size(thumbSize)
                .graphicsLayer {
                    scaleX = stretch
                    scaleY = 1f / (1f + (stretch - 1f) * 0.5f)
                }
                .clip(RoundedCornerShape(thumbCorner))
                .background(
                    Brush.verticalGradient(
                        listOf(lerp(thumbColor, Color.White, 0.18f), thumbColor)
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = swMix(0.9f, 0.14f, p)),
                    RoundedCornerShape(thumbCorner)
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                swGlowArc(glowColor, p)
                swSun(p)
                swMoon(p)
                swParticles(burst.value, lerp(SwSun, SwMoon, p))
            }
        }
    }
}

private fun DrawScope.swGlowArc(color: Color, p: Float) {
    val r = size.minDimension * 0.34f
    val brush = Brush.sweepGradient(
        0f to Color.Transparent,
        0.04f to color.copy(alpha = 0.35f),
        0.13f to color,
        0.21f to color.copy(alpha = 0.35f),
        0.25f to Color.Transparent,
        1f to Color.Transparent,
        center = center
    )
    rotate(degrees = swMix(-90f, 90f, p), pivot = center) {
        listOf(9.dp to 0.10f, 6.dp to 0.18f, 3.5.dp to 0.30f).forEach { (w, a) ->
            drawRoundRect(
                brush = brush,
                cornerRadius = CornerRadius(r),
                style = Stroke(width = w.toPx(), cap = StrokeCap.Round),
                alpha = a
            )
        }
        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(r),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.swSun(p: Float) {
    val vis = 1f - p
    if (vis <= 0.01f) return
    val c = center
    val unit = size.minDimension / 2f
    scale(scale = swMix(1f, 0.3f, p), pivot = c) {
        rotate(degrees = p * 120f, pivot = c) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFD166), SwSun), center = c, radius = unit * 0.38f
                ),
                radius = unit * 0.30f,
                center = c,
                alpha = vis
            )
            for (i in 0 until 8) {
                val a = (i * 45f) * (PI.toFloat() / 180f)
                val long = if (i % 2 == 0) 0.22f else 0.14f
                drawLine(
                    color = SwSun,
                    start = Offset(c.x + cos(a) * unit * 0.50f, c.y + sin(a) * unit * 0.50f),
                    end = Offset(
                        c.x + cos(a) * unit * (0.50f + long),
                        c.y + sin(a) * unit * (0.50f + long)
                    ),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    alpha = vis
                )
            }
        }
    }
}

private fun DrawScope.swMoon(p: Float) {
    val vis = p
    if (vis <= 0.01f) return
    val c = center
    val unit = size.minDimension / 2f
    scale(scale = swMix(0.3f, 1f, p), pivot = c) {
        rotate(degrees = (p - 1f) * 90f, pivot = c) {
            val r = unit * 0.46f
            val outer = Path().apply { addOval(Rect(c, r)) }
            val cut = Path().apply {
                addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.30f), r * 0.88f))
            }
            val crescent = Path.combine(PathOperation.Difference, outer, cut)
            drawPath(
                path = crescent,
                brush = Brush.linearGradient(
                    listOf(Color.White, SwMoon),
                    start = Offset(c.x - r, c.y - r),
                    end = Offset(c.x + r, c.y + r)
                ),
                alpha = vis
            )
        }
        swSparkle(Offset(c.x + unit * 0.52f, c.y - unit * 0.50f), unit * 0.16f, vis)
        swSparkle(Offset(c.x + unit * 0.30f, c.y - unit * 0.18f), unit * 0.09f, vis * 0.8f)
    }
}

private fun DrawScope.swSparkle(at: Offset, r: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        quadraticTo(at.x, at.y, at.x + r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - r)
        close()
    }
    drawPath(path, SwMoon, alpha = alpha)
}

private fun DrawScope.swParticles(t: Float, color: Color) {
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

private fun swMix(a: Float, b: Float, t: Float) = a + (b - a) * t
