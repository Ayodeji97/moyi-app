package com.moyi.app

import com.moyi.app.domain.auth.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aRestoreThatThrowsKeepsUnknownAndTheNextForegroundTriesAgain() {
        var calls = 0
        val repo = StubAuthRepository {
            calls++
            if (calls == 1) throw IllegalStateException("store locked") else Unit
        }
        val viewModel = AppViewModel(repo)
        assertEquals(1, calls)
        assertEquals(SessionState.Unknown, viewModel.session.value)

        viewModel.onForeground()
        assertEquals(2, calls)
    }

    @Test
    fun aDecidedStateIsNotRestoredAgainOnForeground() {
        var calls = 0
        val repo = StubAuthRepository { calls++ }
        val viewModel = AppViewModel(repo)
        repo.state.value = SessionState.SignedOut
        viewModel.onForeground()
        assertEquals(1, calls)
    }
}
