package com.moyi.app

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.debug.PreviewBondRepository
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.feature.auth.di.authFeatureModule
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.di.onboardingFeatureModule
import com.moyi.app.feature.onboarding.gate.UnverifiedGateViewModel
import com.moyi.app.feature.onboarding.invite.InviteHandoffViewModel
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.join.JoinConfirmViewModel
import com.moyi.app.feature.onboarding.relationship.RelationshipTypeViewModel
import com.moyi.app.feature.onboarding.start.SignedInStartViewModel
import com.moyi.app.feature.auth.home.HomePlaceholderViewModel
import com.moyi.app.feature.auth.login.LoginViewModel
import com.moyi.app.feature.auth.register.RegisterViewModel
import com.moyi.app.feature.auth.verify.VerifyViewModel
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
                appModule,
                module {
                    single<AuthRepository> { repo }
                    single<BondRepository> { PreviewBondRepository() }
                    single<AccountRepository> { StubAccountRepository }
                },
            )
        }.koin
        try {
            koin.get<RegisterViewModel>()
            koin.get<HomePlaceholderViewModel>()
            koin.get<VerifyViewModel> { parametersOf("amara@example.com") }
            val login = koin.get<LoginViewModel> { parametersOf("amara@example.com", true) }
            assertEquals("amara@example.com", login.state.value.email.value)
            koin.get<SignedInStartViewModel>()
            koin.get<UnverifiedGateViewModel>()
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
        acceptedTermsVersion: String,
        over18: Boolean,
    ): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> = Result.Success(Unit)

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
