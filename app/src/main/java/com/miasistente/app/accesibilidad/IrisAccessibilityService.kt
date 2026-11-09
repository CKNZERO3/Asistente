package com.miasistente.app.accesibilidad

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IrisAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile private var instancia: IrisAccessibilityService? = null
        fun estaActivo(): Boolean = instancia != null
        fun textoVisible(): String? = instancia?.recolectarTexto()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instancia = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instancia = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun recolectarTexto(): String? {
        val raiz = rootInActiveWindow ?: return null
        val sb = StringBuilder()

        fun recorrer(nodo: AccessibilityNodeInfo?, profundidad: Int) {
            if (nodo == null || profundidad > 40 || sb.length > 12_000) return
            if (nodo.isVisibleToUser) {
                val texto = nodo.text?.toString() ?: nodo.contentDescription?.toString()
                if (!texto.isNullOrBlank()) sb.append(texto.trim()).append('\n')
            }
            for (i in 0 until nodo.childCount) {
                recorrer(nodo.getChild(i), profundidad + 1)
            }
        }

        recorrer(raiz, 0)
        return sb.toString().trim().ifBlank { null }
    }
}
