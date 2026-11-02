package com.miasistente.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrisDrawerContent(
    apiKeyActual: String,
    onGuardarApiKey: (String) -> Unit,
    onSeleccionarAvatar: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(apiKeyActual) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Color(0xFF12131C))
            .padding(20.dp)
    ) {
        // Tipografía de Iris
        Text(
            text = "Iris",
            fontSize = 36.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sección de API Key
        Text("Configuración de IA", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text("Gemini API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF8E8FFA),
                unfocusedBorderColor = Color.DarkGray,
                focusedLabelColor = Color(0xFF8E8FFA),
                unfocusedLabelColor = Color.Gray
            )
        )
        
        Button(
            onClick = { onGuardarApiKey(keyText) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E8FFA))
        ) {
            Text("Guardar Clave", color = Color.Black)
        }

        Divider(modifier = Modifier.padding(vertical = 20.dp), color = Color.DarkGray)

        // Selección de Avatar
        Text("Personalización de Avatar", color = Color.Gray, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { onSeleccionarAvatar("avatar_default") }) {
                Icon(Icons.Default.Person, contentDescription = "Default", tint = Color.White)
            }
            IconButton(onClick = { onSeleccionarAvatar("avatar_cat") }) {
                Icon(Icons.Default.Pets, contentDescription = "Gatito", tint = Color(0xFF8E8FFA))
            }
        }
    }
}
