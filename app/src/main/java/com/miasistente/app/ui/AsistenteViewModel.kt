package com.miasistente.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel managing the runtime state of the hybrid voice assistant.
 * Exposes a reactive StateFlow backed by AssistantState synchronized with AsistenteService.
 */
class AsistenteViewModel : ViewModel() {

    val assistantState: StateFlow<AssistantState> = AsistenteService.assistantState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AssistantState.IDLE
        )

    fun setIdle() {
        AsistenteService.assistantState.value.let { 
            // Optional direct manual control hook if needed
        }
    }
}
