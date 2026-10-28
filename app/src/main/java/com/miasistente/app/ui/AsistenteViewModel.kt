package com.miasistente.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miasistente.app.dominio.AssistantState
import com.miasistente.app.service.AsistenteService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AsistenteViewModel(
    private val stateSource: StateFlow<AssistantState> =
        AsistenteService.assistantState,
    private val updateState: (AssistantState) -> Unit =
        AsistenteService::updateAssistantState
) : ViewModel() {

    val assistantState: StateFlow<AssistantState> =
        stateSource.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = stateSource.value
        )

    fun setState(state: AssistantState) {
        updateState(state)
    }

    fun setIdle() {
        setState(AssistantState.IDLE)
    }
}
