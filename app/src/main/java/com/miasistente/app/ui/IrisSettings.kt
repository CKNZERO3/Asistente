package com.miasistente.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IrisSettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAction: (String) -> Unit
) {
    val p = LocalIrisPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = p.text)
            }
            Text("Configuración", color = p.text, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Tarjetas Superiores (Cuentas y Límites)
            SettingsCard {
                SettingsItem("Administrar cuenta", null, Icons.Outlined.PersonOutline) { onAction("account") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingsCard {
                SettingsItem("Límites de uso", null, Icons.Outlined.Schedule) { onAction("limits") }
            }

            // Sección de Preferencias
            SettingsSection("Preferencias")
            SettingsCard {
                SettingsItem("Notificaciones", null, Icons.Outlined.Notifications) { onAction("notifications") }
                SettingsDivider()
                SettingsItem("Inteligencia personalizada", null, Icons.Outlined.AutoAwesome) { onAction("intelligence") }
                SettingsDivider()
                
                // Toggle Modo Día/Noche
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDarkThemeChange(!darkTheme) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DarkMode,
                            contentDescription = null,
                            tint = p.text,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("Tema", color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(if (darkTheme) "Oscuro" else "Claro", color = p.muted, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = p.muted)
                    }
                }

                SettingsDivider()
                SettingsItem("Habilidades", null, Icons.Outlined.Extension) { onAction("skills") }
                SettingsDivider()
                SettingsItem("Voz", null, Icons.Outlined.RecordVoiceOver) { onAction("voice") }
                SettingsDivider()
                SettingsItem("Contenido multimedia", null, Icons.Outlined.MusicNote) { onAction("media") }
                SettingsDivider()
                SettingsItem("Controles de dispositivos", null, Icons.Outlined.Devices) { onAction("devices") }
                SettingsDivider()
                SettingsItem("Iris Notebook", null, Icons.Outlined.Book) { onAction("notebook") }
            }

            // Sección de Datos y Privacidad
            SettingsSection("Datos y privacidad")
            SettingsCard {
                SettingsItem("Importar memoria", null, Icons.Outlined.UploadFile) { onAction("import") }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    val p = LocalIrisPalette.current
    Text(
        text = title,
        color = p.muted,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card), // Usa el color de la tarjeta del tema
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    val p = LocalIrisPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(p.outline.copy(alpha = 0.5f))
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val p = LocalIrisPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, color = p.muted, fontSize = 14.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = p.muted)
    }
}
