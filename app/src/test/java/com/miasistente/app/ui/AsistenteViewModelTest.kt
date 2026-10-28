package com.miasistente.app.ui

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.miasistente.app.dominio.AssistantState
import kotlinx.coroutines.Dispatchers
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

    private lateinit var dispatcher: StandardTestDispatcher
    private lateinit var source: MutableStateFlow<AssistantState>
    private lateinit var viewModel: AsistenteViewModel
    private lateinit var viewModelStore: ViewModelStore

    @BeforeEach
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)

        source = MutableStateFlow(AssistantState.IDLE)
        viewModelStore = ViewModelStore()

        viewModel = AsistenteViewModel(
            stateSource = source,
            updateState = { state -> source.value = state }
        )

        viewModelStore.put("assistant", viewModel)
    }

    @AfterEach
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE`() = runTest(dispatcher) {
        assertThat(viewModel.assistantState.value)
            .isEqualTo(AssistantState.IDLE)

        viewModel.assistantState.test {
            assertThat(awaitItem())
                .isEqualTo(AssistantState.IDLE)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits complete transition sequence`() = runTest(dispatcher) {
        viewModel.assistantState.test {
            assertThat(awaitItem())
                .isEqualTo(AssistantState.IDLE)

            val expectedStates = listOf(
                AssistantState.LISTENING,
                AssistantState.THINKING,
                AssistantState.SPEAKING,
                AssistantState.IDLE
            )

            expectedStates.forEach { nextState ->
                viewModel.setState(nextState)
                runCurrent()

                assertThat(awaitItem()).isEqualTo(nextState)
            }

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reflects updates from external state owner`() =
        runTest(dispatcher) {
            viewModel.assistantState.test {
                assertThat(awaitItem())
                    .isEqualTo(AssistantState.IDLE)

                source.value = AssistantState.LISTENING
                runCurrent()

                assertThat(awaitItem())
                    .isEqualTo(AssistantState.LISTENING)

                source.value = AssistantState.THINKING
                runCurrent()

                assertThat(awaitItem())
                    .isEqualTo(AssistantState.THINKING)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setIdle resets every active state`() = runTest(dispatcher) {
        viewModel.assistantState.test {
            assertThat(awaitItem())
                .isEqualTo(AssistantState.IDLE)

            listOf(
                AssistantState.LISTENING,
                AssistantState.THINKING,
                AssistantState.SPEAKING
            ).forEach { activeState ->
                viewModel.setState(activeState)
                runCurrent()

                assertThat(awaitItem()).isEqualTo(activeState)

                viewModel.setIdle()
                runCurrent()

                assertThat(awaitItem())
                    .isEqualTo(AssistantState.IDLE)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `assigning same state does not emit duplicate`() =
        runTest(dispatcher) {
            viewModel.assistantState.test {
                assertThat(awaitItem())
                    .isEqualTo(AssistantState.IDLE)

                viewModel.setState(AssistantState.LISTENING)
                runCurrent()

                assertThat(awaitItem())
                    .isEqualTo(AssistantState.LISTENING)

                viewModel.setState(AssistantState.LISTENING)
                runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `latest state propagates when collector is active`() =
        runTest(dispatcher) {
            viewModel.assistantState.test {
                assertThat(awaitItem())
                    .isEqualTo(AssistantState.IDLE)

                viewModel.setState(AssistantState.THINKING)
                runCurrent()

                assertThat(awaitItem())
                    .isEqualTo(AssistantState.THINKING)

                assertThat(viewModel.assistantState.value)
                    .isEqualTo(AssistantState.THINKING)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
