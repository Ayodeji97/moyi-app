package com.moyi.app

import com.moyi.app.domain.legal.LegalRepository
import com.moyi.app.feature.auth.legal.LegalViewModel
import com.moyi.app.debug.PreviewNotificationSettingsRepository
import com.moyi.app.domain.notification.NotificationSettingsRepository
import com.moyi.app.domain.notification.PushTokenRegistration
import com.moyi.app.feature.bond.notifications.NotificationSettingsViewModel
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.debug.PreviewBondRepository
import com.moyi.app.debug.PreviewBondSettingsRepository
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.auth.di.authFeatureModule
import com.moyi.app.feature.bond.di.bondFeatureModule
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.di.onboardingFeatureModule
import com.moyi.app.feature.onboarding.gate.UnverifiedGateViewModel
import com.moyi.app.feature.onboarding.invite.InviteHandoffViewModel
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeViewModel
import com.moyi.app.feature.onboarding.start.SignedInStartViewModel
import com.moyi.app.feature.auth.login.LoginViewModel
import com.moyi.app.feature.auth.register.RegisterViewModel
import com.moyi.app.feature.auth.verify.VerifyViewModel
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.TodayCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxPass
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.RefusedEntry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The presentation half of the graph resolves, including the ViewModels that
 * take navigation arguments as Koin parameters (a mistyped parameter fails
 * here, not on the device). The data half is KoinModulesTest in :shared:data:repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppKoinTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun everyScreenViewModelResolves() {
        val restores = mutableListOf<Unit>()
        val repo = StubAuthRepository { restores += Unit }
        val koin = koinApplication {
            modules(
                authFeatureModule,
                onboardingFeatureModule,
                bondFeatureModule,
                appModule,
                module {
                    single<AuthRepository> { repo }
                    single<BondRepository> { PreviewBondRepository() }
                    single<BondSettingsRepository> { PreviewBondSettingsRepository() }
                    single<AccountRepository> { StubAccountRepository }
                    single<LegalRepository> { StubLegalRepository() }
                    single<TodayCache> { NoCachedDay }
                    single<OutboxSender> { StubOutbox() }
                    single<EntryOutbox> { StubOutbox() }
                    single<NotificationSettingsRepository> { PreviewNotificationSettingsRepository() }
                    single<PushTokenRegistration> {
                        object : PushTokenRegistration {
                            override fun start() = Unit

                            override fun retry() = Unit
                        }
                    }
                },
            )
        }.koin
        try {
            koin.get<RegisterViewModel>()
            koin.get<VerifyViewModel> { parametersOf("amara@example.com") }
            assertEquals(true, koin.get<LegalViewModel> { parametersOf(true) }.state.value.prompt)
            val login = koin.get<LoginViewModel> { parametersOf("amara@example.com", true) }
            assertEquals("amara@example.com", login.state.value.email.value)
            koin.get<SignedInStartViewModel>()
            koin.get<UnverifiedGateViewModel>()
            koin.get<BondSettingsViewModel> { parametersOf("preview-bond") }
            koin.get<NotificationSettingsViewModel> { parametersOf("preview-bond") }
            koin.get<RelationshipTypeViewModel>()
            koin.get<CreateBondViewModel> { parametersOf(RelationshipType.Couple) }
            koin.get<InviteHandoffViewModel> { parametersOf("K7Q2PD", "https://getmoyi.app/i/K7Q2PD") }
            koin.get<JoinCodeViewModel> { parametersOf(null) }
            koin.get<JoinCodeViewModel> { parametersOf("K7Q2PD") }
            val preview = InvitePreview("Amara", "Amara and Tunde", BondType.COUPLE)
            assertEquals("Amara", koin.get<JoinConfirmViewModel> { parametersOf("K7Q2PD", preview) }.state.value.inviterName)
            koin.get<AppViewModel>()
            assertEquals(1, restores.size, "the shell restores the session once at launch")
        } finally {
            koin.close()
        }
    }
}

/** An outbox with nothing in it, counting the shell's calls. */
internal class StubOutbox : OutboxSender, EntryOutbox {
    var starts = 0
    var retries = 0
    override val changes: Flow<Unit> = emptyFlow()
    override fun start() {
        starts++
    }
    override fun retrySoon() {
        retries++
    }
    override suspend fun sendQueued(): OutboxPass = OutboxPass.Empty
    override suspend fun enqueue(entry: QueuedEntry) = Unit
    override suspend fun queued(): List<QueuedEntry> = emptyList()
    override suspend fun queuedFor(bondId: String): QueuedEntry? = null
    override suspend fun remove(idempotencyKey: String) = Unit
    override suspend fun refuse(entry: QueuedEntry, reason: OutboxRefusal) = Unit
    override suspend fun refusalFor(bondId: String): RefusedEntry? = null
    override suspend fun clearRefusal(bondId: String) = Unit
}

/** Session stays as set; restoreSession() runs [onRestore], which may throw. */
internal class StubAuthRepository(private val onRestore: () -> Unit) : AuthRepository {
    val state = MutableStateFlow<SessionState>(SessionState.Unknown)
    override val session: StateFlow<SessionState> = state
    override val sessionEvents: Flow<SessionEvent> = emptyFlow()

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String?,
        over18: Boolean,
    ): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun requestPasswordReset(email: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resetPassword(token: String, password: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun login(email: String, password: String): Result<User, DataError> =
        Result.Failure(DataError.Network.Unknown)

    override suspend fun logout() = Unit

    override suspend fun restoreSession(): SessionState {
        onRestore()
        return state.value
    }
}

private object StubAccountRepository : AccountRepository {
    override suspend fun currentUser(): Result<User, DataError.Network> = Result.Failure(DataError.Network.Unknown)
}

/** A device with nothing cached. */
private object NoCachedDay : TodayCache {
    override suspend fun read(): CachedDay? = null
    override suspend fun write(day: CachedDay) = Unit
    override suspend fun clear() = Unit
}
