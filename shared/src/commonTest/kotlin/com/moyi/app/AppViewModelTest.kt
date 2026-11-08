package com.moyi.app

import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.notification.PushTokenRegistration
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

    private val outbox = StubOutbox()
    private val push = object : PushTokenRegistration {
        var starts = 0
        var retries = 0

        override fun start() {
            starts++
        }

        override fun retry() {
            retries++
        }
    }

    @Test
    fun aRestoreThatThrowsKeepsUnknownAndTheNextForegroundTriesAgain() {
        var calls = 0
        val repo = StubAuthRepository {
            calls++
            if (calls == 1) throw IllegalStateException("store locked") else Unit
        }
        val viewModel = AppViewModel(repo, outbox, push)
        assertEquals(1, calls)
        assertEquals(SessionState.Unknown, viewModel.session.value)

        viewModel.onForeground()
        assertEquals(2, calls)
    }

    @Test
    fun aDecidedStateIsNotRestoredAgainOnForeground() {
        var calls = 0
        val repo = StubAuthRepository { calls++ }
        val viewModel = AppViewModel(repo, outbox, push)
        repo.state.value = SessionState.SignedOut
        viewModel.onForeground()
        assertEquals(1, calls)
    }

    @Test
    fun theShellStartsTheOutboxOnceAndEveryForegroundTriesItAgain() {
        val viewModel = AppViewModel(StubAuthRepository {}, outbox, push)
        assertEquals(1, outbox.starts)
        assertEquals(0, outbox.retries)

        viewModel.onForeground()
        viewModel.onForeground()

        assertEquals(1, outbox.starts)
        assertEquals(2, outbox.retries, "whatever the session, a foreground tries the queue again")
    }

    @Test
    fun theShellStartsThePushTokenRegistrationOnceAndEveryForegroundRetriesIt() {
        val viewModel = AppViewModel(StubAuthRepository {}, outbox, push)
        assertEquals(1, push.starts)

        viewModel.onForeground()

        assertEquals(1, push.starts)
        assertEquals(1, push.retries)
    }
}
