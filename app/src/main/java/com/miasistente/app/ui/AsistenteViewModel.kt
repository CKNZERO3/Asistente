package com.miasistente.app.ui

import androidx.lifecycle.ViewModel
import com.miasistente.app.dominio.AssistantState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing the runtime state of the hybrid voice assistant.
 * Exposes a reactive StateFlow backed by AssistantState.
 */
class AsistenteViewModel : ViewModel() {

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    fun setIdle() {
        _assistantState.value = AssistantState.IDLE
    }

    fun setListening() {
        _assistantState.value = AssistantState.LISTENING
    }

    fun setThinking() {
        _assistantState.value = AssistantState.THINKING
    }

    fun setSpeaking() {
        _assistantState.value = AssistantState.SPEAKING
    }
}
