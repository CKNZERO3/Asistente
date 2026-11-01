package com.miasistente.app.ui.avatar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService

@Composable
fun AssistantAvatarView(
    modifier: Modifier = Modifier
) {
    val currentState by AsistenteService.assistantState.collectAsStateCompat()

    AssistantAvatarCanvas(
        currentState = currentState,
        modifier = modifier
    )
}

@Composable
private fun AssistantAvatarCanvas(
    currentState: AssistantState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(
        label = "hitodama_transition"
    )

    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val listeningPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listening_pulse"
    )

    val thinkingPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_pulse"
    )

    val speakingPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_pulse"
    )

    val targetScale = when (currentState) {
        AssistantState.IDLE -> idlePulse
        AssistantState.LISTENING -> listeningPulse
        AssistantState.THINKING -> thinkingPulse
        AssistantState.SPEAKING -> speakingPulse
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing
        ),
        label = "state_scale"
    )

    val glowColor = when (currentState) {
        AssistantState.IDLE -> Color(0xFF8EC5FC)
        AssistantState.LISTENING -> Color(0xFF57E6D3)
        AssistantState.THINKING -> Color(0xFFB69CFF)
        AssistantState.SPEAKING -> Color(0xFFFFB86C)
    }

    Box(
        modifier = modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(104.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.23f
            val radius = baseRadius * scale * breathing

            // Halo exterior difuminado.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.30f),
                        glowColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 2.8f
                ),
                radius = radius * 2.8f,
                center = center
            )

            // Anillo de energía.
            drawCircle(
                color = glowColor.copy(alpha = 0.48f),
                radius = radius * 1.65f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Núcleo luminoso.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        glowColor.copy(alpha = 0.95f),
                        glowColor.copy(alpha = 0.65f)
                    ),
                    center = Offset(
                        center.x - radius * 0.22f,
                        center.y - radius * 0.25f
                    ),
                    radius = radius * 1.7f
                ),
                radius = radius,
                center = center
            )

            // Reflejo sutil para dar sensación orgánica.
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = radius * 0.20f,
                center = Offset(
                    center.x - radius * 0.32f,
                    center.y - radius * 0.35f
                )
            )
        }
    }
}
