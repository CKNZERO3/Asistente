package com.miasistente.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
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

/**
 * Pantalla de Ajustes: apariencia (día/noche), permisos, acerca de y contacto.
 * Acciones que emite: "permissions", "about", "privacy", "support".
 */
@Suppress("DEPRECATION")
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
            // Evita que los toques atraviesen la pantalla hacia el chat
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = p.text)
            }
            Text("Ajustes", color = p.text, fontSize = 22.sp, modifier = Modifier.padding(start = 4.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            
            SettingsSection("Apariencia")
            SettingsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onDarkThemeChange(!darkTheme) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = p.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Modo día / noche", color = p.text, fontSize = 14.sp)
                        Text(
                            if (darkTheme) "Oscuro" else "Claro",
                            color = p.muted,
                            fontSize = 12.sp
                        )
                    }
                    IrisDayNightSwitch(
                        isNight = darkTheme,
                        onToggle = onDarkThemeChange,
                        trackWidth = 96.dp,
                        trackHeight = 52.dp
                    )
                }
            }

            SettingsSection("Permisos de la app")
            SettingsCard {
                SettingsItem(
                    title = "Micrófono, fotos y archivos",
                    subtitle = "Se administran en los ajustes del sistema",
                    icon = Icons.Default.Security
                ) { onAction("permissions") }
            }

            SettingsSection("Acerca de la app")
            SettingsCard {
                SettingsItem("Sobre Iris", null, Icons.Default.Info) { onAction("about") }
                SettingsDivider()
                SettingsItem("Privacidad y datos", null, Icons.Default.Lock) { onAction("privacy") }
            }

            SettingsSection("Contacto")
            SettingsCard {
                SettingsItem("Ayuda y sugerencias", null, Icons.Default.ChatBubbleOutline) {
                    onAction("support")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    val p = LocalIrisPalette.current
    Text(
        text = title,
        color = p.muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val p = LocalIrisPalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.outline, shape),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    val p = LocalIrisPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 52.dp)
            .height(1.dp)
            .background(p.outline)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = p.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = p.muted, fontSize = 12.sp)
            }
        }
    }
}
