package com.miasistente.app.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import kotlinx.coroutines.launch

private val IrisWhite = Color(0xFFF4F0FF)
private val IrisMuted = Color(0xFFB4A9D1)
private val IrisAccent = Color(0xFFA78BFA)

private val IrisBackground = Brush.verticalGradient(
    listOf(
        Color(0xFF080C21),
        Color(0xFF12112E),
        Color(0xFF24133D)
    )
)

private val IrisAccentGradient = Brush.horizontalGradient(
    listOf(Color(0xFFB996FF), Color(0xFF8056E8))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrisScreen(
    userName: String? = null,
    profilePicture: Painter? = null,
    avatarEnabled: Boolean = false,
    onAvatarChanged: (Boolean) -> Unit = {},
    onSend: (String) -> Unit = {},
    onVoice: () -> Unit = {},
    onAttach: () -> Unit = {},
    onAction: (String) -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf("") }

    fun navigate(action: String) {
        scope.launch {
            drawerState.close()
            onAction(action)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IrisAccent,
            background = Color(0xFF080C21),
            surface = Color(0xFF17112C),
            onSurface = IrisWhite,
            onBackground = IrisWhite
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.55f),
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
                        profilePicture = profilePicture,
                        avatarEnabled = avatarEnabled,
                        onAvatarChanged = onAvatarChanged,
                        onAction = ::navigate
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IrisBackground)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp)
            ) {
                // Cabecera superior minimalista: solo el título "Iris" que abre el menú
                Text(
                    text = "Iris",
                    color = IrisWhite,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "Abrir menú") {
                            scope.launch { drawerState.open() }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )

                // Contenido central limpio sin líneas divisorias
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    IrisLogo(modifier = Modifier.size(112.dp))

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = userName?.takeIf { it.isNotBlank() }?.let { "Hola, $it." } ?: "Hola.",
                        color = IrisMuted,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "¿Qué tienes en mente?",
                        color = IrisWhite,
                        fontSize = 25.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Un espacio para tus ideas.",
                        color = IrisMuted,
                        fontSize = 13.sp
                    )
                }

                // Barra flotante inferior tipo píldora
                IrisComposer(
                    value = message,
                    onValueChange = { message = it },
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

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun IrisComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF30234D), Color(0xFF38264F))
                )
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttach) {
            Icon(Icons.Default.Add, contentDescription = "Adjuntar", tint = IrisMuted)
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textStyle = TextStyle(color = IrisWhite, fontSize = 13.sp),
            cursorBrush = SolidColor(IrisAccent),
            maxLines = 4,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text("Pregúntale algo a Iris...", color = IrisMuted, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVoice) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Hablar con Iris",
                    tint = IrisWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(IrisAccentGradient)
                    .clickable(role = Role.Button, onClickLabel = "Enviar", onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "Enviar",
                    tint = IrisWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun IrisDrawer(
    userName: String?,
    profilePicture: Painter?,
    avatarEnabled: Boolean,
    onAvatarChanged: (Boolean) -> Unit,
    onAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IrisBackground)
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
                Text("Iris", color = IrisWhite, fontSize = 24.sp)
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
                    Text("Iniciar sesión", color = IrisWhite, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = IrisWhite, modifier = Modifier.size(18.dp))
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAction("account") }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (profilePicture != null) {
                        Image(
                            painter = profilePicture,
                            contentDescription = "Perfil",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(IrisAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = IrisWhite)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(userName, color = IrisWhite)
                        Text("Tu espacio personal", color = IrisMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = null, tint = IrisMuted)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Botones de acción rápida superiores (Buscar y Galería)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerAction("Buscar", "Chats y archivos", Icons.Default.Search, Modifier.weight(1f)) { onAction("search") }
                DrawerAction("Galería", "Contenido de Iris", Icons.Default.PhotoLibrary, Modifier.weight(1f)) { onAction("gallery") }
            }

            DrawerSectionTitle("General")
            DrawerItem("Nueva conversación", Icons.Default.Edit) { onAction("new_chat") }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = IrisAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Avatar 3D", color = IrisWhite, fontSize = 14.sp)
                    Text("Animación del sistema", color = IrisMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = avatarEnabled,
                    onCheckedChange = onAvatarChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = IrisWhite,
                        checkedTrackColor = Color(0xFF9564F6),
                        uncheckedThumbColor = IrisMuted,
                        uncheckedTrackColor = Color(0xFF39304D)
                    )
                )
            }

            DrawerSectionTitle("Permisos de la app")
            DrawerItem("Micrófono, fotos y archivos", Icons.Default.Security) { onAction("permissions") }

            DrawerSectionTitle("Acerca de la app")
            DrawerItem("Sobre Iris", Icons.Default.Info) { onAction("about") }
            DrawerItem("Privacidad y datos", Icons.Default.Lock) { onAction("privacy") }

            DrawerSectionTitle("Contacto")
            DrawerItem("Ayuda y sugerencias", Icons.Default.ChatBubbleOutline) { onAction("support") }
        }

        Spacer(Modifier.height(16.dp))

        // Ajustes fijo en la parte inferior izquierda del panel lateral
        DrawerItem("Ajustes", Icons.Default.Settings) { onAction("settings") }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title,
        color = IrisMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp)
    )
}

@Composable
private fun DrawerItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = IrisMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = IrisWhite, fontSize = 14.sp)
    }
}

@Composable
private fun DrawerAction(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF292342))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = IrisMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = IrisWhite, fontSize = 13.sp)
            Text(subtitle, color = IrisMuted, fontSize = 9.sp)
        }
    }
}

/**
 * Logo oficial de Iris: silueta moderna de línea de la gatita con el prisma tetraedro
 * minúsculo y el haz de luz refractado en arcoíris.
 */
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
