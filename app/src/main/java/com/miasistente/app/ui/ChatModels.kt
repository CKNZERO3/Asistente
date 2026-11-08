package com.miasistente.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean
)

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()

    var isThinking by mutableStateOf(false)
        private set

    private var nextId = 0L

    fun addUser(text: String) {
        messages.add(ChatMessage(nextId++, text, fromUser = true))
    }

    fun addAssistant(text: String) {
        messages.add(ChatMessage(nextId++, text, fromUser = false))
    }

    fun updateThinking(value: Boolean) {
        isThinking = value
    }

    fun clear() {
        messages.clear()
        isThinking = false
    }
}
