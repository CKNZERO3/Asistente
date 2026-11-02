package com.miasistente.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun IrisHomeScreen(
    respuestaAsistente: String,
    estaCargando: Boolean,
    onSendMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Manejo local de SharedPreferences para la API Key
    val prefs = remember { context.getSharedPreferences("miasistente_prefs", Context.MODE_PRIVATE) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                IrisDrawerContent(
                    apiKeyActual = apiKey,
                    onGuardarApiKey = { nuevaKey ->
                        apiKey = nuevaKey
                        prefs.edit().putString("gemini_api_key", nuevaKey).apply()
                        scope.launch { drawerState.close() }
                    },
                    onSeleccionarAvatar = { avatarId ->
                        prefs.edit().putString("selected_avatar", avatarId).apply()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Aquí va tu barra superior existente de Iris
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Área de respuesta del asistente
                if (respuestaAsistente.isNotEmpty()) {
                    Text(
                        text = respuestaAsistente,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                if (estaCargando) {
                    CircularProgressIndicator()
                }

                Spacer(modifier = Modifier.weight(1f))

                // Componente de entrada de mensaje (IrisComposer)
                IrisComposer(
                    onSendMessage = onSendMessage
                )
            }
        }
    }
}
