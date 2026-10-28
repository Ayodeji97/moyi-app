package com.moyi.app.feature.auth.home

import com.moyi.app.feature.auth.FakeAuthRepository
import com.moyi.app.feature.auth.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomePlaceholderViewModelTest : MainDispatcherTest() {
    private val repo = FakeAuthRepository().apply { signIn() }

    // Lazy: the ViewModel starts sharing its state at construction, which must
    // happen after the test Main dispatcher is in place.
    private val viewModel by lazy { HomePlaceholderViewModel(repo) }

    @Test
    fun signingOutKeepsTheLastNameSoTheGreetingNeverEmpties() = runTest {
        // The state is shared WhileSubscribed, so keep a subscriber, as the screen does.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        runCurrent()
        assertEquals("Amara", viewModel.state.value.displayName)

        viewModel.onAction(HomePlaceholderAction.OnSignOutClick)
        runCurrent()

        // The session is SignedOut now; the screen still says "Hello, Amara" until the shell routes away.
        assertEquals(1, repo.logoutCalls)
        assertTrue(viewModel.state.value.signingOut)
        assertEquals("Amara", viewModel.state.value.displayName)
    }
}
