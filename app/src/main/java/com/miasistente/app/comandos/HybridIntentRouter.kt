package com.miasistente.app.comandos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HybridIntentRouter(
    private val localEngine: LocalOneTokenEngine? = null
) {
    interface LocalOneTokenEngine {
        val isLoaded: Boolean
        /** Configurar el motor con maxOutputToken = 2 y temperature = 0.0 */
        suspend fun inferSingleToken(prompt: String): String
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")

        private val COMPLEX_KEYWORDS = setOf(
            "analiza", "código", "codigo", "programa", "compara", "paso a paso",
            "por qué", "porque", "explica", "resume", "arquitectura", "optimiza",
            "diferencia", "evalúa", "evalua", "diseña", "disena", "debug", "error",
            "investiga", "desarrolla", "implementa", "planifica", "estrategia"
        )

        private val TRIVIAL_PREFIXES = listOf(
            "hola", "gracias", "qué hora", "que hora", "quién eres", "quien eres",
            "pon ", "abre ", "llama ", "alarma", "clima", "tiempo en"
        )

        // Sin espacio final para evitar gastar el primer token en un espacio vacío
        private const val CLASSIFIER_TEMPLATE =
            "Responde solo 1 (requiere análisis, código o explicación profunda) o 0 (charla o dato rápido).\nTexto: %s\nRespuesta:"
    }

    /**
     * Devuelve `true` para enrutar a Gemini avanzado (razonamiento)
     * o `false` para ir directo a Gemini ligero con thinkingBudget = 0.
     */
    suspend fun isComplexQuery(rawPrompt: String): Boolean {
        val clean = rawPrompt.trim().lowercase()
        if (clean.isEmpty()) return false

        // Nivel 0: filtro heurístico en memoria (< 1 ms, 0 % batería)
        if (TRIVIAL_PREFIXES.any { clean.startsWith(it) }) return false
        if (COMPLEX_KEYWORDS.any { clean.contains(it) }) return true

        val wordCount = clean.split(WHITESPACE_REGEX).size
        if (wordCount <= 6) return false
        if (wordCount >= 22) return true

        // Nivel 1: modelo local a 2 tokens (solo si ya está caliente en RAM)
        val engine = localEngine ?: return false
        if (!engine.isLoaded) return false

        return withContext(Dispatchers.Default) {
            runCatching {
                val formatted = CLASSIFIER_TEMPLATE.format(rawPrompt.trim())
                engine.inferSingleToken(formatted).trim().startsWith("1")
            }.getOrDefault(false)
        }
    }
}
