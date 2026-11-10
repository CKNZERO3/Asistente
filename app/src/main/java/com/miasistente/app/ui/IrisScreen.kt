package com.miasistente.app.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import kotlinx.coroutines.launch

private val SwitchTrackOffDark = Color(0xFF39304D)
private val SwitchTrackOffLight = Color(0xFFD9D1EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrisScreen(
    userName: String? = null,
    profilePhoto: String? = null,
    userEmail: String? = null,
    avatarEnabled: Boolean = false,
    darkTheme: Boolean = true,
    onDarkThemeChange: (Boolean) -> Unit = {},
    messages: List<ChatMessage> = emptyList(),
    isThinking: Boolean = false,
    micActive: Boolean = false,
    micPulse: Boolean = false,
    voiceStatus: String? = null,
    permissionPrompt: PermissionPrompt? = null,
    onPermissionConfirm: () -> Unit = {},
    onPermissionDismiss: () -> Unit = {},
    onAvatarChanged: (Boolean) -> Unit = {},
    onSend: (String) -> Unit = {},
    onVoice: () -> Unit = {},
    onAttach: () -> Unit = {},
    onAction: (String) -> Unit = {}
) {
    val palette = if (darkTheme) IrisDarkPalette else IrisLightPalette
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    fun handle(action: String) {
        when (action) {
            "settings" -> showSettings = true
            "about", "privacy", "account" -> dialog = action
            else -> onAction(action)
        }
    }

    fun navigate(action: String) {
        scope.launch {
            drawerState.close()
            handle(action)
        }
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            background = Color(0xFF080C21),
            surface = Color(0xFF17112C),
            onSurface = palette.text,
            onBackground = palette.text
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            background = Color(0xFFF8F6FF),
            surface = Color.White,
            onSurface = palette.text,
            onBackground = palette.text
        )
    }

    CompositionLocalProvider(LocalIrisPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme) {
            Box(Modifier.fillMaxSize()) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    scrimColor = palette.scrim,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight(),
                            drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                            drawerContainerColor = Color.Transparent
                        ) {
                            IrisDrawer(
                                userName = userName,
                                profilePhoto = profilePhoto,
                                userEmail = userEmail,
                                avatarEnabled = avatarEnabled,
                                onAvatarChanged = onAvatarChanged,
                                onAction = ::navigate
                            )
                        }
                    }
                ) {
                    IrisMain(
                        userName = userName,
                        messages = messages,
                        isThinking = isThinking,
                        micActive = micActive,
                        micPulse = micPulse,
                        voiceStatus = voiceStatus,
                        message = message,
                        onMessageChange = { message = it },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onAttach = onAttach,
                        onVoice = onVoice,
                        onSend = {
                            val text = message.trim()
                            if (text.isNotEmpty()) {
                                onSend(text)
                                message = ""
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = showSettings,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut()
                ) {
                    IrisSettingsScreen(
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onBack = { showSettings = false },
                        onAction = { handle(it) }
                    )
                }

                var lastPrompt by remember { mutableStateOf<PermissionPrompt?>(null) }
                SideEffect { if (permissionPrompt != null) lastPrompt = permissionPrompt }
                val shownPrompt = permissionPrompt ?: lastPrompt
                AnimatedVisibility(
                    visible = permissionPrompt != null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 20.dp, end = 20.dp, bottom = 88.dp),
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    if (shownPrompt != null) {
                        PermissionCard(
                            prompt = shownPrompt,
                            onConfirm = onPermissionConfirm,
                            onDismiss = onPermissionDismiss
                        )
                    }
                }
            }

            BackHandler(enabled = showSettings) { showSettings = false }

            when (dialog) {
                "about" -> InfoDialog(
                    title = "Sobre Iris",
                    text = "Iris es tu asistente personal para Android. Versión 1.0.",
                    onDismiss = { dialog = null }
                )
                "privacy" -> InfoDialog(
                    title = "Privacidad y datos",
                    text = "La palabra clave se reconoce en tu teléfono con un modelo de voz local. " +
                        "Las consultas generales se procesan con un servicio de inteligencia artificial " +
                        "de Google a través del servidor de Iris; ninguna clave se guarda en tu teléfono. " +
                        "Puedes revocar el micrófono, " +
                        "las fotos y los archivos cuando quieras desde Ajustes → Permisos.",
                    onDismiss = { dialog = null }
                )
                "account" -> AlertDialog(
                    onDismissRequest = { dialog = null },
                    title = { Text(userName ?: "Tu cuenta") },
                    text = { Text("¿Quieres cerrar tu sesión en Iris?") },
                    confirmButton = {
                        TextButton(onClick = {
                            dialog = null
                            onAction("sign_out")
                        }) { Text("Cerrar sesión", color = palette.accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialog = null }) { Text("Cancelar", color = palette.muted) }
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    val p = LocalIrisPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = p.accent) }
        }
    )
}

@Composable
private fun IrisMain(
    userName: String?,
    messages: List<ChatMessage>,
    isThinking: Boolean,
    micActive: Boolean,
    micPulse: Boolean,
    voiceStatus: String?,
    message: String,
    onMessageChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    val p = LocalIrisPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Iris",
            color = p.text,
            fontSize = 28.sp,
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Abrir menú", onClick = onOpenDrawer)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            if (messages.isEmpty()) {
                IrisWelcome(userName)
            } else {
                IrisChatList(messages, isThinking)
            }
        }

        if (voiceStatus != null) {
            Text(
                text = voiceStatus,
                color = p.accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(p.card)
                    .border(1.dp, p.outline, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        IrisComposer(
            value = message,
            onValueChange = onMessageChange,
            micActive = micActive,
            micPulse = micPulse,
            onAttach = onAttach,
            onVoice = onVoice,
            onSend = onSend
        )

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun IrisWelcome(userName: String?) {
    val p = LocalIrisPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IrisLogo(modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(32.dp))
        Text(
            text = userName?.takeIf { it.isNotBlank() }?.let { "Hola, $it." } ?: "Hola.",
            color = p.muted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("¿Qué tienes en mente?", color = p.text, fontSize = 25.sp)
        Spacer(Modifier.height(10.dp))
        Text("Un espacio para tus ideas.", color = p.muted, fontSize = 13.sp)
    }
}

@Composable
private fun IrisChatList(messages: List<ChatMessage>, isThinking: Boolean) {
    val listState = rememberLazyListState()
    val total = messages.size + if (isThinking) 1 else 0
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(total, imeOpen) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            ChatBubble(message.text, message.fromUser, message.attachment)
        }
        if (isThinking) {
            item(key = "thinking") {
                ChatBubble("Iris está pensando…", fromUser = false, attachment = null, muted = true)
            }
        }
    }
}

@Composable
private fun ChatBubble(
    text: String,
    fromUser: Boolean,
    attachment: ChatAttachment?,
    muted: Boolean = false
) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (fromUser) 20.dp else 6.dp,
        bottomEnd = if (fromUser) 6.dp else 20.dp
    )
    val hasAttachment = attachment != null
    val textColor = when {
        fromUser -> Color.White
        muted -> p.muted
        else -> p.text
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(if (fromUser) IrisAccentGradient else SolidColor(p.assistantBubble), shape)
                .then(
                    if (!fromUser && !p.isDark) Modifier.border(1.dp, p.outline, shape) else Modifier
                )
                .padding(
                    horizontal = if (hasAttachment) 6.dp else 14.dp,
                    vertical = if (hasAttachment) 6.dp else 10.dp
                )
        ) {
            Column {
                if (attachment != null) {
                    AttachmentPreview(attachment, onAccent = fromUser)
                }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = 14.sp,
                        modifier = if (hasAttachment) {
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            Modifier
                        }
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
    micActive: Boolean,
    micPulse: Boolean,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(28.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.composer)
            .border(1.dp, p.outline, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconButton(onClick = onAttach) {
            Icon(Icons.Default.Add, contentDescription = "Adjuntar", tint = p.muted)
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 15.dp),
            textStyle = TextStyle(color = p.text, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            maxLines = 4,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text("Pregúntale algo a Iris...", color = p.muted, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            }
        )

        MicButton(active = micActive, pulsing = micPulse, onClick = onVoice)

        Spacer(Modifier.width(2.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { alpha = if (value.isBlank()) 0.55f else 1f }
                .clip(CircleShape)
                .background(IrisAccentGradient)
                .clickable(role = Role.Button, onClickLabel = "Enviar", onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = "Enviar",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun MicButton(active: Boolean, pulsing: Boolean, onClick: () -> Unit) {
    val p = LocalIrisPalette.current
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        1f,
        1.15f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "mic-pulse"
    )
    val scale = if (pulsing) pulse else 1f

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (active) IrisAccentGradient else SolidColor(Color.Transparent))
            .clickable(
                role = Role.Button,
                onClickLabel = if (active) "Desactivar micrófono" else "Activar micrófono",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = if (active) "Micrófono activo" else "Hablar con Iris",
            tint = if (active) Color.White else p.text,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun IrisDrawer(
    userName: String?,
    profilePhoto: String?,
    userEmail: String?,
    avatarEnabled: Boolean,
    onAvatarChanged: (Boolean) -> Unit,
    onAction: (String) -> Unit
) {
    val p = LocalIrisPalette.current
    val photo = rememberUriBitmap(profilePhoto, 160).bitmap

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IrisLogo(Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Iris", color = p.text, fontSize = 24.sp)
            }

            Spacer(Modifier.height(24.dp))

            if (userName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(IrisAccentGradient)
                        .clickable(role = Role.Button, onClickLabel = "Iniciar sesión") { onAction("sign_in") }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Iniciar sesión", color = Color.White, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.ArrowOutward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onAction("account") }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (photo != null) {
                        Image(
                            bitmap = photo,
                            contentDescription = "Perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(p.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = p.text)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(userName, color = p.text)
                        Text(
                            userEmail ?: "Tu espacio personal",
                            color = p.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = p.muted)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerAction("Buscar", "Chats y archivos", Icons.Default.Search, Modifier.weight(1f)) {
                    onAction("search")
                }
                DrawerAction("Galería", "Contenido de Iris", Icons.Default.PhotoLibrary, Modifier.weight(1f)) {
                    onAction("gallery")
                }
            }

            DrawerSectionTitle("General")
            DrawerItem("Nueva conversación", Icons.Default.Edit) { onAction("new_chat") }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = p.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Avatar 3D", color = p.text, fontSize = 14.sp)
                    Text("Animación del sistema", color = p.muted, fontSize = 11.sp)
                }
                Switch(
                    checked = avatarEnabled,
                    onCheckedChange = onAvatarChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF9564F6),
                        uncheckedThumbColor = if (p.isDark) p.muted else Color.White,
                        uncheckedTrackColor = if (p.isDark) SwitchTrackOffDark else SwitchTrackOffLight
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        DrawerItem("Ajustes", Icons.Default.Settings) { onAction("settings") }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    val p = LocalIrisPalette.current
    Text(
        text = title,
        color = p.muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp)
    )
}

@Composable
private fun DrawerItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    val p = LocalIrisPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = p.text, fontSize = 14.sp)
    }
}

@Composable
private fun DrawerAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = p.text, fontSize = 13.sp)
            Text(subtitle, color = p.muted, fontSize = 9.sp)
        }
    }
}

@Composable
fun IrisLogo(modifier: Modifier = Modifier) {
    val image = remember { renderIrisLogoWithPrism() }
    Image(bitmap = image, contentDescription = "Iris Logo", modifier = modifier)
}

private fun renderIrisLogoWithPrism(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(554, 554, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val cat = PathParser.createPathFromPathData(
        """
        M160 516
        C181 477 180 423 165 365
        C151 352 143 334 137 317
        C124 292 127 265 140 243
        C119 247 101 239 92 229
        L84 220
        Q81 217 85 213
        L94 201
        C90 191 89 180 92 166
        C94 150 104 140 120 134
        L108 106
        Q106 102 111 105
        L151 127
        L172 86
        Q174 82 178 86
        C205 106 218 142 225 176
        C229 202 246 222 275 236
        C341 254 388 276 414 349
        C434 358 440 326 430 286
        C416 244 381 200 370 157
        C351 93 387 43 448 27
        C410 56 398 104 406 139
        C413 174 446 222 463 266
        C491 350 457 456 390 522
        C406 460 374 416 332 392
        C299 372 263 376 237 395
        C211 415 195 481 160 516 Z
        """.trimIndent()
    )

    val eye = PathParser.createPathFromPathData("M113 181 Q121 174 129 175")

    val colors = intArrayOf(
        android.graphics.Color.rgb(255, 105, 108),
        android.graphics.Color.rgb(255, 173, 98),
        android.graphics.Color.rgb(248, 233, 131),
        android.graphics.Color.rgb(132, 233, 165),
        android.graphics.Color.rgb(118, 231, 238),
        android.graphics.Color.rgb(123, 158, 255),
        android.graphics.Color.rgb(190, 141, 250),
        android.graphics.Color.rgb(238, 160, 227)
    )

    val spectrum = LinearGradient(
        66.48f, 498.6f, 487.52f, 44.32f,
        colors,
        floatArrayOf(0f, .16f, .30f, .45f, .60f, .76f, .90f, 1f),
        Shader.TileMode.CLAMP
    )

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        shader = spectrum
    }

    fun drawNeon(path: android.graphics.Path, width: Float) {
        paint.strokeWidth = width * 1.6f
        paint.alpha = 120
        paint.maskFilter = BlurMaskFilter(width * .8f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, paint)

        paint.maskFilter = null
        paint.alpha = 255
        paint.strokeWidth = width
        canvas.drawPath(path, paint)
    }

    if (cat != null) drawNeon(cat, 8f)
    if (eye != null) drawNeon(eye, 4f)

    return bitmap.asImageBitmap()
}
