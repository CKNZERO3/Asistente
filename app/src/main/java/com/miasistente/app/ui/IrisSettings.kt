package com.miasistente.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun IrisSettingsScreen(
    darkTheme: Boolean,
    amoledMode: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    avatarEnabled: Boolean = false,
    onAvatarChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    initialPage: String = "main"
) {
    var currentPage by rememberSaveable { mutableStateOf(initialPage) }

    BackHandler(enabled = currentPage != "main") {
        currentPage = "main"
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            if (targetState != "main") {
                (slideInHorizontally(tween(220)) { it } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -it / 3 } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally(tween(220)) { -it / 3 } + fadeIn(tween(180)))
                    .togetherWith(slideOutHorizontally(tween(220)) { it } + fadeOut(tween(150)))
            }
        },
        label = "settings-navigation"
    ) { page ->
        when (page) {
            "main" -> MainSettingsContent(
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                avatarEnabled = avatarEnabled,
                onAvatarChanged = onAvatarChanged,
                onBack = onBack,
                onAction = onAction,
                onNavigate = { currentPage = it }
            )
            "theme" -> ThemeSubmenuPage(
                darkTheme = darkTheme,
                amoledMode = amoledMode,
                onDarkThemeChange = onDarkThemeChange,
                onAmoledChange = onAmoledChange,
                onBack = { currentPage = "main" }
            )
            "notifications" -> NotificationsPage(onBack = { currentPage = "main" })
            "voice" -> VoicePage(
                onImportVoice = { onAction("import_voice") },
                onBack = { currentPage = "main" }
            )
            "multimedia" -> MultimediaPage(onBack = { currentPage = "main" })
            "devices" -> DevicesPage(onBack = { currentPage = "main" })
            "notebook" -> NotebookMockupPage(
                onBack = {
                    if (initialPage == "notebook") onBack() else currentPage = "main"
                }
            )
        }
    }
}

@Composable
private fun MainSettingsContent(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    avatarEnabled: Boolean,
    onAvatarChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val p = LocalIrisPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        SettingsTopBar("Configuración", onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsCard {
                SettingsItem("Administrar cuenta", null, Icons.Outlined.PersonOutline) { onAction("account") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard {
                SettingsItem("Límites de uso", null, Icons.Outlined.Schedule) { onAction("limits") }
            }

            SettingsSection("Preferencias")
            SettingsCard {
                SettingsItem("Notificaciones", null, Icons.Outlined.Notifications) { onNavigate("notifications") }
                SettingsDivider()
                SettingsItem("Inteligencia personalizada", null, Icons.Outlined.AutoAwesome) { onAction("intelligence") }
                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onNavigate("theme") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DarkMode, null, tint = p.text, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Tema y apariencia", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = p.muted)
                }

                SettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAvatarChanged(!avatarEnabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.ViewInAr, null, tint = p.text, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Avatar 3D", color = p.text, fontSize = 16.sp)
                        Text("Animación del sistema", color = p.muted, fontSize = 13.sp)
                    }
                    Switch(
                        checked = avatarEnabled,
                        onCheckedChange = onAvatarChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = p.accent,
                            uncheckedThumbColor = if (p.isDark) p.muted else Color.White,
                            uncheckedTrackColor = if (p.isDark) Color(0xFF39304D) else Color(0xFFD9D1EE)
                        )
                    )
                }

                SettingsDivider()
                SettingsItem("Habilidades", null, Icons.Outlined.Extension) { onAction("skills") }
                SettingsDivider()
                SettingsItem("Voz", null, Icons.Outlined.RecordVoiceOver) { onNavigate("voice") }
                SettingsDivider()
                SettingsItem("Contenido multimedia", null, Icons.Outlined.MusicNote) { onNavigate("multimedia") }
                SettingsDivider()
                SettingsItem("Controles de dispositivos", null, Icons.Outlined.Devices) { onNavigate("devices") }
                SettingsDivider()
                SettingsItem("Iris Notebook", null, Icons.Outlined.Book) { onNavigate("notebook") }
            }

            SettingsSection("Datos y privacidad")
            SettingsCard {
                SettingsItem("Importar memoria", null, Icons.Outlined.UploadFile) { onAction("import") }
                SettingsDivider()
                SettingsItem(
                    "Importar modelo de IA local",
                    "Archivo .litertlm (p. ej. Gemma 3 1B). Resume y responde tareas ligeras sin gastar cuota de Gemini.",
                    Icons.Outlined.UploadFile
                ) { onAction("import_model") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun DraggableThemeSwitch(
    isDark: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxTravelPx = remember(density) { with(density) { 24.dp.toPx() } }

    val progress = remember { Animatable(if (isDark) 1f else 0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(isDark) {
        if (!isDragging) {
            progress.animateTo(
                targetValue = if (isDark) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val trackColor by animateColorAsState(
        targetValue = if (progress.value > 0.5f) Color(0xFF39304D) else Color(0xFFD9D1EE),
        animationSpec = tween(160),
        label = "switch-track"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (progress.value > 0.5f) Color(0xFF9564F6) else Color.White,
        animationSpec = tween(160),
        label = "switch-thumb"
    )

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val next = (progress.value + delta / maxTravelPx).coerceIn(0f, 1f)
            progress.snapTo(next)
        }
    }

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(trackColor)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStarted = { isDragging = true },
                onDragStopped = { velocity ->
                    isDragging = false
                    val targetDark = when {
                        velocity > 300f -> true
                        velocity < -300f -> false
                        else -> progress.value >= 0.5f
                    }
                    scope.launch {
                        progress.animateTo(
                            targetValue = if (targetDark) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        if (targetDark != isDark) {
                            onToggle(targetDark)
                        }
                    }
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val targetDark = !isDark
                scope.launch {
                    progress.animateTo(
                        targetValue = if (targetDark) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    onToggle(targetDark)
                }
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = progress.value * maxTravelPx
                }
                .size(24.dp)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (progress.value >= 0.5f) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = if (progress.value >= 0.5f) Color.White else Color(0xFFF59E0B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun ThemeSubmenuPage(
    darkTheme: Boolean,
    amoledMode: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val p = LocalIrisPalette.current
    val systemIsDark = isSystemInDarkTheme()
    var followSystem by rememberSaveable { mutableStateOf(false) }
    var selectedWallpaper by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(followSystem, systemIsDark) {
        if (followSystem && darkTheme != systemIsDark) {
            onDarkThemeChange(systemIsDark)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        SettingsTopBar("Tema y apariencia", onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = p.text, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Modo oscuro", color = p.text, fontSize = 16.sp)
                        Text(
                            text = if (darkTheme) "Activado" else "Desactivado",
                            color = p.muted,
                            fontSize = 13.sp
                        )
                    }
                    DraggableThemeSwitch(
                        isDark = darkTheme,
                        onToggle = {
                            followSystem = false
                            onDarkThemeChange(it)
                        }
                    )
                }

                SettingsDivider()

                AnimatedVisibility(visible = darkTheme) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAmoledChange(!amoledMode) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Contrast, null, tint = p.text, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Fondo Negro AMOLED", color = p.text, fontSize = 16.sp)
                                Text("Ahorra batería con negros puros", color = p.muted, fontSize = 13.sp)
                            }
                            Switch(
                                checked = amoledMode,
                                onCheckedChange = onAmoledChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = p.accent,
                                    uncheckedThumbColor = p.muted,
                                    uncheckedTrackColor = Color(0xFF39304D)
                                )
                            )
                        }
                        SettingsDivider()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            followSystem = !followSystem
                            if (followSystem && darkTheme != systemIsDark) {
                                onDarkThemeChange(systemIsDark)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.BrightnessAuto, contentDescription = null, tint = p.text, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sincronizar con el sistema", color = p.text, fontSize = 16.sp)
                        Text(
                            text = if (systemIsDark) "Sistema en modo oscuro" else "Sistema en modo claro",
                            color = p.muted,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = followSystem,
                        onCheckedChange = {
                            followSystem = it
                            if (it && darkTheme != systemIsDark) {
                                onDarkThemeChange(systemIsDark)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = p.accent,
                            uncheckedThumbColor = if (p.isDark) p.muted else Color.White,
                            uncheckedTrackColor = if (p.isDark) Color(0xFF39304D) else Color(0xFFD9D1EE)
                        )
                    )
                }
            }

            SettingsSection("Fondos clásicos del chat")
            val wallpapers = listOf(
                "Animado (Por defecto)" to Color(0xFF080C21),
                "Nebulosa" to Color(0xFF17112C),
                "Grafito" to Color(0xFF1E1E2E),
                "Iris Claro" to Color(0xFFF8F6FF),
                "Lavanda" to Color(0xFFECE6FA),
                "Perla" to Color(0xFFE5E5EA)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                itemsIndexed(wallpapers) { idx, (label, bgColor) ->
                    val isSelected = selectedWallpaper == idx
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) p.accent else p.outline,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedWallpaper = idx },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(p.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, color = if (isSelected) p.text else p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsPage(onBack: () -> Unit) {
    val p = LocalIrisPalette.current
    Column(Modifier.fillMaxSize().background(if(amoledMode) Color.Black else MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        SettingsTopBar("Notificaciones", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsCard {
                SettingsToggleItem("Respuestas de Iris", true)
                SettingsDivider()
                SettingsToggleItem("Recordatorios y tareas", true)
                SettingsDivider()
                SettingsToggleItem("Avisos de activación por voz", true)
                SettingsDivider()
                SettingsToggleItem("Sugerencias inteligentes", false)
            }
        }
        SettingsSaveButton("Listo", onBack)
    }
}

@Composable
fun VoicePage(onImportVoice: () -> Unit, onBack: () -> Unit) {
    val p = LocalIrisPalette.current
    var selectedVoice by rememberSaveable { mutableStateOf(0) }
    val voices = listOf(
        "Nova" to "Clara y natural",
        "Lyra" to "Cálida y cercana",
        "Orion" to "Profunda y serena",
        "Vega" to "Ágil y expresiva",
        "Echo" to "Neutra y precisa"
    )

    Column(Modifier.fillMaxSize().background(if(amoledMode) Color.Black else MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        SettingsTopBar("Voz", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsCard {
                voices.forEachIndexed { idx, (name, desc) ->
                    SettingsRadioItem(
                        title = name,
                        subtitle = desc,
                        selected = selectedVoice == idx
                    ) { selectedVoice = idx }
                    if (idx < voices.lastIndex) SettingsDivider()
                }
            }
        }
        SettingsSaveButton("Personalizar o importar voz", onImportVoice)
    }
}

@Composable
fun MultimediaPage(onBack: () -> Unit) {
    val p = LocalIrisPalette.current
    var audioQuality by rememberSaveable { mutableStateOf(0.8f) }
    Column(Modifier.fillMaxSize().background(if(amoledMode) Color.Black else MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        SettingsTopBar("Contenido multimedia", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Calidad de audio y respuesta", color = p.text, fontSize = 16.sp)
                        Text("${(audioQuality * 100).toInt()}%", color = p.accent, fontSize = 14.sp)
                    }
                    Slider(
                        value = audioQuality,
                        onValueChange = { audioQuality = it },
                        colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent)
                    )
                }
                SettingsDivider()
                SettingsToggleItem("Reproducción automática", true)
                SettingsDivider()
                SettingsToggleItem("Guardar imágenes generadas", true)
            }
        }
        SettingsSaveButton("Listo", onBack)
    }
}

@Composable
fun DevicesPage(onBack: () -> Unit) {
    val p = LocalIrisPalette.current
    Column(Modifier.fillMaxSize().background(if(amoledMode) Color.Black else MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
        SettingsTopBar("Controles de dispositivos", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsCard {
                SettingsToggleItem("Control por voz en pantalla bloqueada", true)
                SettingsDivider()
                SettingsToggleItem("Audio Bluetooth automático", true)
                SettingsDivider()
                SettingsToggleItem("Sincronizar con accesorios", false)
            }
        }
        SettingsSaveButton("Listo", onBack)
    }
}


private data class NotebookEntry(
    val id: Int,
    val title: String,
    val body: String,
    val date: String,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val paperType: String = "Líneas"
)

@Composable
fun NotebookMockupPage(onBack: () -> Unit) {
    var openedNoteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by remember {
        mutableStateOf(
            listOf(
                NotebookEntry(1, "Notas Personales", "", "12/11/2026", paperType = "Líneas"),
                NotebookEntry(2, "Proyecto de App", "", "13/11/2026", paperType = "Puntos")
            )
        )
    }

    val p = LocalIrisPalette.current

    if (openedNoteId != null) {
        val note = notes.find { it.id == openedNoteId } ?: return
        NotebookDigitalPlanner(
            note = note,
            allNotes = notes,
            onUpdate = { updated -> notes = notes.map { if (it.id == updated.id) updated else it } },
            onDelete = { id -> 
                notes = notes.filterNot { it.id == id }
                openedNoteId = null 
            },
            onNavigate = { newId -> openedNoteId = newId },
            onBack = { openedNoteId = null }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsTopBar("Mis Cuadernos", onBack)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(notes.size, key = { notes[it].id }) { idx ->
                    val note = notes[idx]
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE2E0D8)) // Tapa del cuaderno
                            .border(1.dp, p.outline, RoundedCornerShape(12.dp))
                            .clickable { openedNoteId = note.id }
                    ) {
                        // Lomo
                        Box(Modifier.fillMaxHeight().width(24.dp).background(Color(0xFF333333)).align(Alignment.CenterStart))
                        Column(modifier = Modifier.padding(start = 36.dp, top = 16.dp, end = 12.dp)) {
                            Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Spacer(Modifier.height(8.dp))
                            Text("Última mod: ${note.date}", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        val nextId = (notes.maxOfOrNull { it.id } ?: 0) + 1
                        val created = NotebookEntry(nextId, "Nuevo tema $nextId", "", "Hoy")
                        notes = notes + created
                        openedNoteId = nextId
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent)
                ) { Text("Crear Cuaderno") }
            }
        }
    }
}

@Composable
fun NotebookDigitalPlanner(
    note: NotebookEntry,
    allNotes: List<NotebookEntry>,
    onUpdate: (NotebookEntry) -> Unit,
    onDelete: (Int) -> Unit,
    onNavigate: (Int) -> Unit,
    onBack: () -> Unit
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var showMenu by remember { mutableStateOf(false) }
    val p = LocalIrisPalette.current

    val paperColors = listOf(Color(0xFFFFFFFF), Color(0xFFF9F7E8))
    
    val tabColors = listOf(
        Color(0xFFB5E6D8), Color(0xFFFFD1D1), Color(0xFFE5D4ED), 
        Color(0xFFFFF0B3), Color(0xFFD4E4FF), Color(0xFFFFCFA1)
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8E5DD)) // Fondo base del planner
            .padding(top = 16.dp, bottom = 16.dp, start = 12.dp, end = 4.dp)
    ) {
        // Area central de papel
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(paperColors[0]) // Papel blanco/crema
                .drawBehind {
                    val lineColor = Color(0xFFD3D3D3)
                    when (note.paperType) {
                        "Líneas" -> {
                            val spacing = 32.dp.toPx()
                            var y = 100.dp.toPx()
                            while (y < size.height) {
                                drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 2f)
                                y += spacing
                            }
                        }
                        "Cuadrícula" -> {
                            val spacing = 24.dp.toPx()
                            var y = spacing
                            while (y < size.height) {
                                drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 2f)
                                y += spacing
                            }
                            var x = spacing
                            while (x < size.width) {
                                drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 2f)
                                x += spacing
                            }
                        }
                        "Puntos" -> {
                            val spacing = 24.dp.toPx()
                            var y = spacing
                            while (y < size.height) {
                                var x = spacing
                                while (x < size.width) {
                                    drawCircle(color = lineColor, radius = 2f, center = androidx.compose.ui.geometry.Offset(x, y))
                                    x += spacing
                                }
                                y += spacing
                            }
                        }
                    }
                }
        ) {
            // Cabecera interna del cuaderno
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    onUpdate(note.copy(title = title, body = body))
                    onBack() 
                }) {
                    Icon(Icons.Outlined.Home, contentDescription = "Inicio", tint = Color.Black)
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it; onUpdate(note.copy(title = it, body = body)) },
                    textStyle = TextStyle(color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(Icons.Default.MoreVert, "Menú", tint = Color.Black)
                }
            }

            // Cuerpo del cuaderno
            BasicTextField(
                value = body,
                onValueChange = { body = it; onUpdate(note.copy(title = title, body = it)) },
                textStyle = TextStyle(color = Color(0xFF333333), fontSize = 18.sp, lineHeight = 28.sp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        // Solapas Laterales (Tabs)
        Column(
            modifier = Modifier.width(36.dp).fillMaxHeight().padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            allNotes.take(12).forEachIndexed { idx, tabNote ->
                val isActive = tabNote.id == note.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isActive) 52.dp else 42.dp)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(tabColors[idx % tabColors.size])
                        .clickable { onNavigate(tabNote.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${idx + 1}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Tipo de papel", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = p.text)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Lisa", "Líneas", "Cuadrícula", "Puntos").forEach { type ->
                        val selected = note.paperType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) p.accent else MaterialTheme.colorScheme.background)
                                .clickable { onUpdate(note.copy(title = title, body = body, paperType = type)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(type, color = if (selected) Color.White else p.text, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onDelete(note.id); showMenu = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Eliminar cuaderno", color = Color.White)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
