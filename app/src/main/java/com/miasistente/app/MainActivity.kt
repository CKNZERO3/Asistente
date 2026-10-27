package com.miasistente.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.PantallaPrincipal

/**
 * Actividad principal. Solo arranca la interfaz: toda la pantalla vive en la carpeta ui/.
 * El tema es oscuro, así que las barras del sistema se dibujan con iconos claros.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            AsistenteTema {
                PantallaPrincipal()
            }
        }
    }
}
