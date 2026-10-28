
package com.miasistente.app.ui.avatar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val transition = rememberInfiniteTransition(
        label = "AssistantAvatarTransition"
    )

    val listeningScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ListeningScale"
    )

    val thinkingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ThinkingScale"
    )

    val speakingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SpeakingScale"
    )

    val pulseScale = when (currentState) {
        AssistantState.IDLE -> 1f
        AssistantState.LISTENING -> listeningScale
        AssistantState.THINKING -> thinkingScale
        AssistantState.SPEAKING -> speakingScale
    }

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val radius = (size.minDimension / 2f) * pulseScale * 0.8f

            drawCircle(
                color = baseColor.copy(alpha = 0.5f),
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )

            drawCircle(
                color = baseColor,
                radius = size.minDimension / 3f
            )
        }
    }
}
