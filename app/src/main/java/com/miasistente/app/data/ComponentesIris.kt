package com.miasistente.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.miasistente.app.comandos.ResultadoComando

/**
 * Qué puede hacer el asistente (acciones locales y online) y, al fondo, una sección
 * colapsable para ver o cambiar la clave de Gemini sin tener que recompilar la app.
 *
 * La prueba de comandos escritos ya no vive aquí: ahora se hace desde la barra inferior
 * de la pantalla principal.
 */
@Composable
fun SeccionCapacidades(
    tieneClave: Boolean,
    verificando: Boolean,
    resultadoVerificacion: ResultadoComando?,
    onGuardarClave: (String) -> Unit,
    onBorrarClave: () -> Unit,
    onVerificarClave: () -> Unit
) {
    var claveEscrita by remember { mutableStateOf("") }
    var abierto by rememberSaveable { mutableStateOf(false) }

    Tarjeta(titulo = "Capacidades", emoji = "⚡") {

        BloqueCapacidad(titulo = "Sin internet", etiqueta = "OFFLINE", color = Paleta.Verde) {
            FilaCapacidad("📝", "Crear nota", "«crea una nota comprar leche»")
            Spacer(Modifier.height(12.dp))
            FilaCapacidad("🎵", "Reproducir en Musicolet", "«reproduce Queen»")
        }

        Spacer(Modifier.height(12.dp))

        BloqueCapacidad(titulo = "Con internet · Gemini", etiqueta = "ONLINE", color = Paleta.Azul) {
            FilaCapacidad("✨", "Búsqueda con IA", "«qué es un agujero negro»")
            Spacer(Modifier.height(12.dp))
            FilaCapacidad("🌐", "Consultas web", "«noticias de tecnología de hoy»")
        }

        Spacer(Modifier.height(12.dp))

        // --- Clave de Gemini (colapsable) -------------------------------------------------------
        Surface(shape = RoundedCornerShape(20.dp), color = Paleta.TarjetaAlta) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { abierto = !abierto }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔑", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Clave de Gemini",
                        color = Paleta.Texto,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Insignia(
                        texto = if (tieneClave) "GUARDADA" else "SIN CLAVE",
                        color = if (tieneClave) Paleta.Verde else Paleta.Ambar
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (abierto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (abierto) "Ocultar" else "Mostrar",
                        tint = Paleta.TextoSuave
                    )
                }

                AnimatedVisibility(visible = abierto) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        OutlinedTextField(
                            value = claveEscrita,
                            onValueChange = { claveEscrita = it },
                            label = { Text(if (tieneClave) "Nueva clave (reemplaza la actual)" else "Pega tu clave") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            supportingText = { Text("Se guarda cifrada solo en este teléfono.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    onGuardarClave(claveEscrita)
                                    claveEscrita = ""
                                },
                                enabled = claveEscrita.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("Guardar") }

                            OutlinedButton(
                                onClick = onVerificarClave,
                                enabled = tieneClave && !verificando,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (verificando) "Probando…" else "Verificar") }
                        }

                        if (tieneClave) {
                            TextButton(onClick = onBorrarClave) {
                                Text("Borrar clave", color = Paleta.Rojo)
                            }
                        }

                        resultadoVerificacion?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = (if (it.exito) "✔ " else "✖ ") + it.mensaje,
                                color = if (it.exito) Paleta.Verde else Paleta.Rojo,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
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
