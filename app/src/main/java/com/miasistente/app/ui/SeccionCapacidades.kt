package com.miasistente.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miasistente.app.comandos.ResultadoComando

/**
 * Qué puede hacer el asistente: acciones locales (sin internet) y acciones online (Gemini).
 * Incluye la API Key y un campo para probar comandos escribiéndolos.
 */
@Composable
fun SeccionCapacidades(
    tieneClave: Boolean,
    probando: Boolean,
    resultado: ResultadoComando?,
    onGuardarClave: (String) -> Unit,
    onProbar: (String) -> Unit
) {
    var clave by remember { mutableStateOf("") }
    var comando by remember { mutableStateOf("") }

    Tarjeta(titulo = "Capacidades", emoji = "⚡") {

        BloqueCapacidad(titulo = "Sin internet", etiqueta = "OFFLINE", color = Paleta.Verde) {
            FilaCapacidad("📝", "Crear nota", "«crea una nota comprar leche»")
            Spacer(Modifier.height(12.dp))
            FilaCapacidad("🎵", "Reproducir en Musicolet", "«reproduce Queen»")
        }

        Spacer(Modifier.height(16.dp))

        BloqueCapacidad(titulo = "Con internet · Gemini", etiqueta = "ONLINE", color = Paleta.Azul) {
            FilaCapacidad("✨", "Búsqueda con IA", "«qué es un agujero negro»")
            Spacer(Modifier.height(12.dp))
            FilaCapacidad("🌐", "Consultas web", "«noticias de tecnología de hoy»")
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = clave,
                onValueChange = { clave = it },
                label = { Text("API Key de Gemini") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text(if (tieneClave) "✔ Clave guardada (cifrada en el teléfono)" else "Aún no hay clave guardada")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    onGuardarClave(clave)
                    clave = ""
                },
                enabled = clave.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar clave") }
        }

        Spacer(Modifier.height(16.dp))

        // Probar sin voz (hasta que el servicio transcriba comandos)
        Text(
            "Probar un comando (escribe)",
            color = Paleta.Texto,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = comando,
            onValueChange = { comando = it },
            placeholder = { Text("crea una nota llamar a Ana") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onProbar(comando) },
            enabled = comando.isNotBlank() && !probando,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (probando) "Ejecutando…" else "Ejecutar") }

        resultado?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = (if (it.exito) "✔ " else "✖ ") + it.mensaje,
                color = if (it.exito) Paleta.Verde else Paleta.Rojo,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BloqueCapacidad(
    titulo: String,
    etiqueta: String,
    color: Color,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Surface(shape = RoundedCornerShape(20.dp), color = Paleta.TarjetaAlta) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titulo,
                    color = Paleta.Texto,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Insignia(etiqueta, color)
            }
            Spacer(Modifier.height(12.dp))
            contenido()
        }
    }
}

@Composable
private fun FilaCapacidad(emoji: String, nombre: String, ejemplo: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(nombre, color = Paleta.Texto, fontWeight = FontWeight.Medium)
            Text(ejemplo, color = Paleta.TextoSuave, style = MaterialTheme.typography.bodySmall)
        }
    }
}
