
package com.miasistente.app.ui

import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.miasistente.app.dominio.AssistantState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import app.cash.turbine.test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AsistenteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var stateSource: MutableStateFlow<AssistantState>
    private lateinit var viewModel: AsistenteViewModel

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        stateSource = MutableStateFlow(AssistantState.IDLE)
        viewModel = AsistenteViewModel(
            stateSource = stateSource,
            updateState = { stateSource.value = it }
        )
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() = runTest(testDispatcher) {
        assertThat(viewModel.assistantState.value)
            .isEqualTo(AssistantState.IDLE)
    }

    @Test
    fun stateChangesArePropagated() = runTest(testDispatcher) {
        viewModel.assistantState.test {
            assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)

            stateSource.value = AssistantState.LISTENING
            runCurrent()
            assertThat(awaitItem()).isEqualTo(AssistantState.LISTENING)

            stateSource.value = AssistantState.THINKING
            runCurrent()
            assertThat(awaitItem()).isEqualTo(AssistantState.THINKING)

            stateSource.value = AssistantState.SPEAKING
            runCurrent()
            assertThat(awaitItem()).isEqualTo(AssistantState.SPEAKING)

            stateSource.value = AssistantState.IDLE
            runCurrent()
            assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun externalSourceUpdatesAreObserved() = runTest(testDispatcher) {
        stateSource.value = AssistantState.LISTENING
        runCurrent()

        assertThat(viewModel.assistantState.value)
            .isEqualTo(AssistantState.LISTENING)
    }

    @Test
    fun setIdleResetsActiveStates() = runTest(testDispatcher) {
        val activeStates = listOf(
            AssistantState.LISTENING,
            AssistantState.THINKING,
            AssistantState.SPEAKING
        )

        activeStates.forEach { state ->
            viewModel.setState(state)
            runCurrent()

            assertThat(viewModel.assistantState.value).isEqualTo(state)

            viewModel.setIdle()
            runCurrent()

            assertThat(viewModel.assistantState.value)
                .isEqualTo(AssistantState.IDLE)
        }
    }

    @Test
    fun duplicateStateDoesNotCreateAnotherEmission() = runTest(testDispatcher) {
        viewModel.assistantState.test {
            assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)

            stateSource.value = AssistantState.IDLE
            runCurrent()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setStateUpdatesObservedState() = runTest(testDispatcher) {
        viewModel.assistantState.test {
            assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)

            viewModel.setState(AssistantState.THINKING)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AssistantState.THINKING)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
