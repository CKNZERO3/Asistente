package com.miasistente.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miasistente.app.data.Ajustes
import com.miasistente.app.data.AvatarRepositorio
import com.miasistente.app.data.Permisos
import com.miasistente.app.data.SecureStorage
import kotlinx.coroutines.launch

@Composable
fun IrisDrawerContent(
    context: Context,
    onCerrar: () -> Unit
) {
    val secureStorage = remember { SecureStorage(context) }
    val ajustes = remember { Ajustes(context) }
    val scope = rememberCoroutineScope()

    var claveEscrita by remember { mutableStateOf(secureStorage.obtenerApiKey() ?: "") }
    var claveGuardadaOk by remember { mutableStateOf(false) }
    var avatarSeleccionado by remember { mutableStateOf(ajustes.avatar) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Título Iris
        Text(
            text = "Iris",
            fontSize = 38.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Asistente Personal IA",
            fontSize = 13.sp,
            color = PaletaIris.TextoSuave
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(20.dp))

        // Sección API Key
        Text("Configuración de Gemini", color = PaletaIris.Acento, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = claveEscrita,
            onValueChange = { claveEscrita = it },
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                secureStorage.guardarApiKey(claveEscrita)
                claveGuardadaOk = true
                scope.launch { kotlinx.coroutines.delay(1500); claveGuardadaOk = false }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PaletaIris.BotonClaro)
        ) {
            Text(if (claveGuardadaOk) "¡Guardada con éxito!" else "Guardar API Key")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(20.dp))

        // Sección Estado de Permisos
        Text("Permisos del Sistema", color = PaletaIris.Acento, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        val micOk = Permisos.microfono(context)
        val overlayOk = Permisos.superposicion(context)
        
        Text("🎤 Micrófono: ${if (micOk) "Concedido" else "Denegado"}", color = if (micOk) Paleta.Verde else Paleta.Rojo, fontSize = 13.sp)
        Text("🪟 Superposición: ${if (overlayOk) "Concedido" else "Denegado"}", color = if (overlayOk) Paleta.Verde else Paleta.Rojo, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(20.dp))

        // Botón cerrar cajón
        OutlinedButton(
            onClick = onCerrar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar Ajustes", color = Color.White)
        }
    }
}
