package com.miasistente.app.ui.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.miasistente.app.dominio.AssistantState

@Composable
fun AssistantAvatarView(
    currentState: AssistantState,
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF3F51B5)
) {
    // Usamos InfiniteTransition para animaciones continuas en bucle de forma nativa
    val infiniteTransition = rememberInfiniteTransition(label = "AssistantInfiniteTransition")

    // Declaraciones incondicionales (respetan el slot table de Compose)
    val listeningScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ListeningScale"
    )

    val thinkingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ThinkingScale"
    )

    val speakingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SpeakingScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ThinkingRotation"
    )

    // Selección reactiva del factor de escala según el estado del asistente
    val pulseScale = when (currentState) {
        AssistantState.IDLE -> 1.0f
        AssistantState.LISTENING -> listeningScale
        AssistantState.THINKING -> thinkingScale
        AssistantState.SPEAKING -> speakingScale
    }

    val activeRotation = if (currentState == AssistantState.THINKING) rotationAngle else 0f

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val radius = (size.minDimension / 2) * (pulseScale * 0.8f)
            
            // Anillo reactivo exterior
            drawCircle(
                color = baseColor.copy(alpha = 0.5f),
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )
            
            // Círculo central
            drawCircle(
                color = baseColor,
                radius = size.minDimension / 3
            )
        }
    }
}
