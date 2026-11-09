package com.moyi.app

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.legal.AcceptedVersions
import com.moyi.app.domain.legal.LegalDocuments
import com.moyi.app.domain.legal.LegalRepository
import com.moyi.app.domain.legal.LegalStatus
import com.moyi.app.domain.legal.PublishedDocument
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val outbox = StubOutbox()
    private val legal = StubLegalRepository()
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
        val viewModel = AppViewModel(repo, outbox, push, legal)
        assertEquals(1, calls)
        assertEquals(SessionState.Unknown, viewModel.session.value)

        viewModel.onForeground()
        assertEquals(2, calls)
    }

    @Test
    fun aDecidedStateIsNotRestoredAgainOnForeground() {
        var calls = 0
        val repo = StubAuthRepository { calls++ }
        val viewModel = AppViewModel(repo, outbox, push, legal)
        repo.state.value = SessionState.SignedOut
        viewModel.onForeground()
        assertEquals(1, calls)
    }

    @Test
    fun theShellStartsTheOutboxOnceAndEveryForegroundTriesItAgain() {
        val viewModel = AppViewModel(StubAuthRepository {}, outbox, push, legal)
        assertEquals(1, outbox.starts)
        assertEquals(0, outbox.retries)

        viewModel.onForeground()
        viewModel.onForeground()

        assertEquals(1, outbox.starts)
        assertEquals(2, outbox.retries, "whatever the session, a foreground tries the queue again")
    }

    @Test
    fun theShellStartsThePushTokenRegistrationOnceAndEveryForegroundRetriesIt() {
        val viewModel = AppViewModel(StubAuthRepository {}, outbox, push, legal)
        assertEquals(1, push.starts)

        viewModel.onForeground()

        assertEquals(1, push.starts)
        assertEquals(1, push.retries)
    }

    private fun signedIn(id: String = "u1") =
        SessionState.SignedIn(User(id, "$id@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE))

    @Test
    fun noConsentCheckRunsBeforeTodayShows() {
        val repo = StubAuthRepository {}
        repo.state.value = signedIn()
        AppViewModel(repo, outbox, push, legal)
        assertEquals(0, legal.statusCalls, "the cached day opens straight to Today: nothing is checked first")
    }

    @Test
    fun anOlderConsentAsksOnceTodayShows() {
        val repo = StubAuthRepository {}
        repo.state.value = signedIn()
        legal.accepted = AcceptedVersions(terms = "v1", privacy = "v1")
        val viewModel = AppViewModel(repo, outbox, push, legal)

        viewModel.onTodayShowing(true)
        assertTrue(viewModel.legalConsentDue.value)

        viewModel.onLegalConsentShown()
        assertFalse(viewModel.legalConsentDue.value)

        // Not now: back on Today, and every foreground after, asks nothing more this launch.
        viewModel.onTodayShowing(false)
        viewModel.onTodayShowing(true)
        viewModel.onForeground()
        assertFalse(viewModel.legalConsentDue.value)
        assertEquals(1, legal.statusCalls)
    }

    @Test
    fun aCurrentConsentAsksNothing() {
        val repo = StubAuthRepository {}
        repo.state.value = signedIn()
        val viewModel = AppViewModel(repo, outbox, push, legal)
        viewModel.onTodayShowing(true)
        assertFalse(viewModel.legalConsentDue.value)
    }

    @Test
    fun aCheckThatCannotBeMadeShowsNothingAndTheNextForegroundTriesAgain() {
        val repo = StubAuthRepository {}
        repo.state.value = signedIn()
        legal.failure = DataError.Network.NoInternet
        val viewModel = AppViewModel(repo, outbox, push, legal)

        viewModel.onTodayShowing(true)
        assertFalse(viewModel.legalConsentDue.value, "offline: Today stays, nothing is asked")

        legal.failure = null
        legal.accepted = AcceptedVersions(terms = "v1", privacy = "v2")
        viewModel.onForeground()
        assertTrue(viewModel.legalConsentDue.value)
        assertEquals(2, legal.statusCalls)
    }

    @Test
    fun aForegroundAwayFromTodayChecksNothing() {
        val repo = StubAuthRepository {}
        repo.state.value = signedIn()
        legal.failure = DataError.Network.NoInternet
        val viewModel = AppViewModel(repo, outbox, push, legal)
        viewModel.onTodayShowing(true)
        viewModel.onTodayShowing(false)
        viewModel.onForeground()
        assertEquals(1, legal.statusCalls)
    }

    @Test
    fun anotherPersonSigningInIsAskedAfresh() {
        val repo = StubAuthRepository {}
        repo.state.value = signedIn("u1")
        val viewModel = AppViewModel(repo, outbox, push, legal)
        viewModel.onTodayShowing(true)
        assertEquals(1, legal.statusCalls)

        repo.state.value = SessionState.SignedOut
        repo.state.value = signedIn("u2")
        legal.accepted = AcceptedVersions(terms = null, privacy = null)
        viewModel.onTodayShowing(true)
        assertEquals(2, legal.statusCalls)
        assertTrue(viewModel.legalConsentDue.value)
    }
}

internal class StubLegalRepository : LegalRepository {
    private val documents = LegalDocuments(
        terms = PublishedDocument("v2", "https://example.test/terms"),
        privacy = PublishedDocument("v2", "https://example.test/privacy"),
        dataProcessingSummary = PublishedDocument("v1", "https://example.test/data"),
    )
    var accepted = AcceptedVersions(terms = "v2", privacy = "v2")
    var failure: DataError.Network? = null
    var statusCalls = 0

    override suspend fun documents(): Result<LegalDocuments, DataError.Network> = Result.Success(documents)

    override suspend fun status(): Result<LegalStatus, DataError.Network> {
        statusCalls++
        return failure?.let { Result.Failure(it) } ?: Result.Success(LegalStatus(documents, accepted))
    }

    override suspend fun acceptCurrent(): EmptyResult<DataError.Network> = Result.Success(Unit)
}
