
package com.miasistente.app.ui

import com.google.common.truth.Truth.assertThat
import com.miasistente.app.dominio.AssistantState
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        viewModel.assistantState.test {
            // Receive the initial StateFlow value.
            assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)

            // Update the source while the collector is active.
            stateSource.value = AssistantState.LISTENING
            runCurrent()

            // Verify the external update is emitted.
            assertThat(awaitItem()).isEqualTo(AssistantState.LISTENING)

            // Ensure there are no unexpected duplicate emissions.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setIdleResetsActiveStates() = runTest(testDispatcher) {
        viewModel.assistantState.test {
            assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)

            val activeStates = listOf(
                AssistantState.LISTENING,
                AssistantState.THINKING,
                AssistantState.SPEAKING
            )

            activeStates.forEach { activeState ->
                viewModel.setState(activeState)
                runCurrent()
                assertThat(awaitItem()).isEqualTo(activeState)

                viewModel.setIdle()
                runCurrent()
                assertThat(awaitItem()).isEqualTo(AssistantState.IDLE)
            }

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
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
