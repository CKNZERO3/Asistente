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

@Composable
fun AssistantAvatarView(
    currentState: AssistantState,
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF3F51B5)
) {
    val transition = updateTransition(targetState = currentState, label = "AssistantStateTransition")

    // Explicit AnimationSpec<Float> typing prevents Kotlin inference mismatch between InfiniteRepeatableSpec and SpringSpec
    val pulseScale by transition.animateFloat(
        label = "PulseScale",
        transitionSpec = {
            val spec: AnimationSpec<Float> = when (targetState) {
                AssistantState.LISTENING -> infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
                AssistantState.THINKING -> infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
                AssistantState.SPEAKING -> infiniteRepeatable(
                    animation = tween(200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
                AssistantState.IDLE -> spring(stiffness = Spring.StiffnessLow)
            }
            spec
        }
    ) { state ->
        when (state) {
            AssistantState.IDLE -> 1.0f
            AssistantState.LISTENING -> 1.2f
            AssistantState.THINKING -> 1.1f
            AssistantState.SPEAKING -> 1.3f
        }
    }

    val rotationAngle by transition.animateFloat(
        label = "RotationAngle",
        transitionSpec = {
            val spec: AnimationSpec<Float> = if (targetState == AssistantState.THINKING) {
                infiniteRepeatable(animation = tween(1200, easing = LinearEasing))
            } else {
                spring()
            }
            spec
        }
    ) { state ->
        if (state == AssistantState.THINKING) 360f else 0f
    }

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val radius = (size.minDimension / 2) * (pulseScale * 0.8f)
            
            // Outer reactive ring
            drawCircle(
                color = baseColor.copy(alpha = 0.5f),
                radius = radius,
                style = Stroke(width = 4.dp.toPx())
            )
            
            // Core inner circle
            drawCircle(
                color = baseColor,
                radius = size.minDimension / 3
            )
        }
    }
}
