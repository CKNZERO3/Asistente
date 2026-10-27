package com.miasistente.app.dominio

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

sealed interface PersonalityProfile {
    val id: String
    val name: String
    val systemPromptHook: String
    val responseFormatter: (String) -> String

    data object Academic : PersonalityProfile {
        override val id = "academic"
        override val name = "Academic"
        override val systemPromptHook = "Respond with formal tone, precise technical vocabulary, and objective syntax."
        override val responseFormatter: (String) -> String = { text -> "[$name]: $text" }
    }

    data object Jarvis : PersonalityProfile {
        override val id = "jarvis"
        override val name = "Butler/Jarvis"
        override val systemPromptHook = "Respond with refined, respectful, sharp, and concise situational awareness."
        override val responseFormatter: (String) -> String = { text -> "Sir, $text" }
    }
}
