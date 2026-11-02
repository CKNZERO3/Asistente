package com.miasistente.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.miasistente.app.dominio.AssistantState

private val IrisBackground = Color(0xFF050711)
private val IrisText = Color(0xFFF5F6FF)
private val IrisMuted = Color(0xFF9299B2)
private val IrisGlass = Color(0x99111324)

/**
 * Pantalla inicial de Iris adaptada con estados y callbacks funcionales.
 */
@Composable
fun IrisHomeScreen(
    userName: String,
    temperature: Int? = null,
    weatherLabel: String? = null,
    assistantState: AssistantState = AssistantState.IDLE,
    isServiceActive: Boolean = false,
    onSendMessage: (String) -> Unit,
    onMicrophoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var message by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(30_000)
        }
    }

    fun send() {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return
        onSendMessage(cleanMessage)
        message = ""
        focusManager.clearFocus()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Aquí mantendremos el drawer actual que luego rediseñaremos
            IrisDrawer(
                userName = userName,
                onDestinationClick = { scope.launch { drawerState.close() } },
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.58f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF080A15), IrisBackground, Color(0xFF0A153A)),
                    ),
                ),
        ) {
            BackgroundGlow()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 22.dp),
            ) {
                IrisHeader(
                    time = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    temperature = temperature,
                    weatherLabel = weatherLabel,
                    isServiceActive = isServiceActive,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 108.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = greetingFor(currentTime) + ", " + userName,
                        color = IrisMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "¿En qué te ayudo hoy?",
                        color = IrisText,
                        fontSize = 39.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-1.4).sp,
                    )
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = if (assistantState == AssistantState.LISTENING) "Escuchando comando..." else "Pregúntame lo que necesites para organizar tu día.",
                        color = if (assistantState == AssistantState.LISTENING) Color(0xFF8B7CFF) else IrisMuted.copy(alpha = 0.84f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                }

                Spacer(Modifier.weight(1f))

                IrisComposer(
                    value = message,
                    onValueChange = { message = it },
                    onSend = ::send,
                    assistantState = assistantState,
                    isServiceActive = isServiceActive,
                    onMicrophoneClick = onMicrophoneClick,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun IrisHeader(
    time: String,
    temperature: Int?,
    weatherLabel: String?,
    isServiceActive: Boolean,
    onOpenDrawer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Abrir menú principal",
                    onClick = onOpenDrawer,
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RainbowIcon(Modifier.size(31.dp))
                // Indicador sutil de servicio activo en la marca
                if (isServiceActive) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFF55D6AD), CircleShape)
                            .align(Alignment.TopEnd)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = NOMBRE_ASISTENTE,
                color = IrisText,
                fontSize = 23.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.8).sp,
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = time,
            color = IrisText.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.width(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            WeatherIcon(Modifier.size(28.dp))
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = temperature?.let { "$it°" } ?: "Sin clima",
                    color = IrisText.copy(alpha = 0.88f),
                    fontSize = if (temperature == null) 11.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!weatherLabel.isNullOrBlank()) {
                    Text(
                        text = weatherLabel,
                        color = IrisMuted.copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun IrisComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    assistantState: AssistantState,
    isServiceActive: Boolean,
    onMicrophoneClick: () -> Unit,
) {
    val showSend = value.isNotBlank()
    val isListening = assistantState == AssistantState.LISTENING

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .clip(RoundedCornerShape(29.dp))
                .background(IrisGlass)
                .padding(horizontal = 20.dp),
            textStyle = TextStyle(
                color = IrisText,
                fontSize = 14.sp,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { input ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = if (isListening) "Escuchando..." else "Pregúntale algo a Iris",
                            color = if (isListening) Color(0xFF8B7CFF) else IrisMuted.copy(alpha = 0.72f),
                            fontSize = 14.sp,
                        )
                    }
                    input()
                }
            },
        )

        Spacer(Modifier.width(11.dp))

        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (isServiceActive || isListening)
                            listOf(Color(0xFFEE6682), Color(0xFF9A5DE0))
                        else
                            listOf(Color(0xFF6685F4), Color(0xFF7553D9)),
                    ),
                )
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (showSend) "Enviar mensaje" else "Usar micrófono",
                    onClick = if (showSend) onSend else onMicrophoneClick,
                )
                .semantics {
                    role = Role.Button
                    contentDescription = if (showSend) "Enviar mensaje" else "Usar micrófono"
                },
            contentAlignment = Alignment.Center,
        ) {
            ActionIcon(
                showSend = showSend,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

@Composable
private fun IrisDrawer(
    userName: String,
    onDestinationClick: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Color(0xF20A0C18),
        drawerContentColor = IrisText,
        modifier = Modifier
            .widthIn(max = 344.dp)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RainbowIcon(Modifier.size(34.dp))
                Spacer(Modifier.width(11.dp))
                Text(
                    text = "Iris",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 27.sp,
                )
            }
            Spacer(Modifier.height(48.dp))
            Text(
                text = "Tu espacio",
                color = IrisMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Hola, $userName",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(34.dp))

            listOf("Inicio", "Calendario", "Equipo", "Perfil").forEach { label ->
                Text(
                    text = label,
                    color = if (label == "Inicio") IrisText else IrisMuted,
                    fontSize = 15.sp,
                    fontWeight = if (label == "Inicio") FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (label == "Inicio") Color(0x245F76E8) else Color.Transparent,
                        )
                        .clickable(onClick = onDestinationClick)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun BackgroundGlow() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x80204EE8), Color.Transparent),
                center = Offset(size.width * 0.4f, size.height * 1.02f),
                radius = size.width * 1.05f,
            ),
            radius = size.width * 1.05f,
            center = Offset(size.width * 0.4f, size.height * 1.02f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x443F1A86), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.width * 0.8f,
            ),
            radius = size.width * 0.8f,
            center = Offset(size.width, 0f),
        )
    }
}

@Composable
private fun RainbowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = listOf(
            Color(0xFFFF5B64),
            Color(0xFFFFAD4A),
            Color(0xFFF6DB55),
            Color(0xFF55D6AD),
            Color(0xFF6FA8FF),
        )
        colors.forEachIndexed { index, color ->
            val inset = 2f + index * size.minDimension * 0.09f
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(inset, inset + size.height * 0.12f),
                size = Size(size.width - inset * 2, size.height - inset * 1.35f),
                style = Stroke(width = size.minDimension * 0.065f, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun WeatherIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.065f, cap = StrokeCap.Round)
        val color = Color(0xFFCCD5FF)
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.68f)
            cubicTo(
                size.width * 0.03f,
                size.height * 0.5f,
                size.width * 0.18f,
                size.height * 0.31f,
                size.width * 0.36f,
                size.height * 0.35f,
            )
            cubicTo(
                size.width * 0.49f,
                size.height * 0.08f,
                size.width * 0.86f,
                size.height * 0.18f,
                size.width * 0.82f,
                size.height * 0.48f,
            )
            cubicTo(
                size.width * 1.03f,
                size.height * 0.57f,
                size.width * 0.9f,
                size.height * 0.75f,
                size.width * 0.74f,
                size.height * 0.75f,
            )
            lineTo(size.width * 0.22f, size.height * 0.75f)
        }
        drawPath(path, color = color, style = stroke)
    }
}

@Composable
private fun ActionIcon(showSend: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White
        val stroke = Stroke(width = size.minDimension * 0.075f, cap = StrokeCap.Round)
        if (showSend) {
            val path = Path().apply {
                moveTo(size.width * 0.12f, size.height * 0.18f)
                lineTo(size.width * 0.88f, size.height * 0.5f)
                lineTo(size.width * 0.12f, size.height * 0.82f)
                close()
            }
            drawPath(path, color = color, style = stroke)
            drawLine(
                color = color,
                start = Offset(size.width * 0.18f, size.height * 0.5f),
                end = Offset(size.width * 0.62f, size.height * 0.5f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        } else {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.35f, size.height * 0.08f),
                size = Size(size.width * 0.3f, size.height * 0.52f),
                cornerRadius = CornerRadius(size.width * 0.15f),
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.2f, size.height * 0.32f),
                size = Size(size.width * 0.6f, size.height * 0.42f),
                style = stroke,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.5f, size.height * 0.74f),
                end = Offset(size.width * 0.5f, size.height * 0.92f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 0..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}
