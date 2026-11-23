package com.miasistente.app.laboratorio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.miasistente.app.ui.AsistenteTema
import java.util.Locale

/**
 * Pantalla de pruebas de rendimiento de la IA local. Por ahora solo se instala en compilaciones
 * debug (ver app/src/debug/AndroidManifest.xml) y aparece como un segundo icono: "Iris Laboratorio".
 */
class LaboratorioRendimientoActivity : ComponentActivity() {

    private val vm: LaboratorioViewModel by viewModels()

    private val guardarJson = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.guardarEn(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Si la pantalla se apaga durante una prueba larga, el sistema puede frenar la app.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            AsistenteTema {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PantallaLaboratorio(
                        vm = vm,
                        onExportar = { guardarJson.launch(vm.nombreArchivoSugerido()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PantallaLaboratorio(vm: LaboratorioViewModel, onExportar: () -> Unit) {
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { vm.refrescarModelos() }
    val libre = !vm.corriendo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Laboratorio de rendimiento", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Mide tokens/s, memoria y temperatura de la IA local. Cierra Iris antes de abrir este " +
                "laboratorio para medir limpio, y mantén esta pantalla abierta durante las pruebas.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Seccion("Modelo")
        if (vm.modelos.isEmpty()) {
            Text(
                "No hay modelos. Importa uno desde Ajustes de Iris, o cópialo por adb a:\n" +
                    (vm.carpetaModelosExterna()?.absolutePath ?: "(carpeta no disponible)"),
                fontSize = 13.sp
            )
        } else {
            vm.modelos.forEach { f ->
                val sel = f == vm.modeloSel
                Text(
                    text = (if (sel) "● " else "○ ") + vm.nombre(f) + " (${vm.tamanoMb(f)} MB)",
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = libre) { vm.modeloSel = f }
                        .padding(vertical = 8.dp)
                )
            }
        }
        OutlinedButton(onClick = { vm.refrescarModelos() }, enabled = libre) { Text("Buscar modelos") }

        Seccion("Configuración de la prueba")
        Opciones("Backend", listOf("CPU", "GPU", "NPU"), vm.backend, { if (it == "NPU") "NPU (experimental)" else it }, libre) {
            vm.backend = it
        }
        if (vm.backend == "NPU") {
            Text(
                "NPU: necesita un modelo compilado para tu NPU y las librerías de NPU dentro de la app. " +
                    "No hay respaldo automático: si falla, verás el error real en el resultado.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Opciones("Tokens de entrada (prefill)", listOf(256, 1024, 2048, 4096), vm.prefill, { "$it" }, libre) {
            vm.prefill = it
        }
        Opciones("Tokens de salida (decode)", listOf(64, 128, 256), vm.decode, { "$it" }, libre) {
            vm.decode = it
        }
        Opciones("Repeticiones", listOf(1, 3, 5), vm.repeticiones, { "$it" }, libre) {
            vm.repeticiones = it
        }
        Opciones("Duración de la prueba sostenida", listOf(5, 10, 15), vm.minutosSostenida, { "$it min" }, libre) {
            vm.minutosSostenida = it
        }
        Opciones("¿Disipador puesto?", listOf(false, true), vm.disipador, { if (it) "Sí" else "No" }, libre) {
            vm.disipador = it
        }
        Text(
            "La repetición 1 suele ser más lenta (se crean cachés). Anota el disipador y no cargues " +
                "el teléfono durante la prueba.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { vm.iniciar(sostenida = false) }, enabled = libre && vm.modeloSel != null) {
                Text("Iniciar prueba")
            }
            Button(onClick = { vm.iniciar(sostenida = true) }, enabled = libre && vm.modeloSel != null) {
                Text("Sostenida")
            }
            OutlinedButton(onClick = { vm.detener() }, enabled = vm.corriendo) { Text("Detener") }
        }

        Seccion("Estado en vivo")
        Text(vm.estado, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        vm.ultimaMuestra?.let { m ->
            Text(
                "t=${f(m.tSeg, 0)}s · RAM libre ${m.ramLibreMb}/${m.ramTotalMb} MB\n" +
                    "RSS ${m.rssMb} MB (pico ${m.hwmMb}) · CPU proceso ${f(m.cpuProcesoPct, 0)}%\n" +
                    "batería ${m.tempBateriaC?.let { f(it, 1) } ?: "?"} °C · " +
                    "térmico ${m.estadoTermico ?: "?"} · margen ${m.margenTermico?.let { f(it, 2) } ?: "?"}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Seccion("Resultados (${vm.resultados.size})")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportar, enabled = vm.resultados.isNotEmpty()) { Text("Exportar JSON") }
            OutlinedButton(
                onClick = {
                    val cm = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("rendimiento", vm.resumenTexto()))
                    vm.estado = "Resumen copiado al portapapeles"
                },
                enabled = vm.resultados.isNotEmpty()
            ) { Text("Copiar resumen") }
            OutlinedButton(onClick = { vm.borrarResultados() }, enabled = libre && vm.resultados.isNotEmpty()) {
                Text("Borrar")
            }
        }
        vm.resultados.asReversed().forEach { r ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    r.resumen(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

private fun f(v: Double, dec: Int): String = String.format(Locale.US, "%.${dec}f", v)

@Composable
private fun Seccion(titulo: String) {
    Text(
        titulo,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun <T> Opciones(
    titulo: String,
    valores: List<T>,
    actual: T,
    etiqueta: (T) -> String,
    habilitado: Boolean,
    onElegir: (T) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            valores.forEach { v ->
                val sel = v == actual
                Text(
                    text = etiqueta(v),
                    fontSize = 14.sp,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = habilitado) { onElegir(v) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
