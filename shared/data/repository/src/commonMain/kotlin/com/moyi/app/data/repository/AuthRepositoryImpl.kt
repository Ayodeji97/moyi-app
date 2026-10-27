package com.moyi.app.data.repository

import com.moyi.app.api.apis.LoginControllerApi
import com.moyi.app.api.apis.LogoutControllerApi
import com.moyi.app.api.apis.RegistrationControllerApi
import com.moyi.app.api.apis.VerificationControllerApi
import com.moyi.app.api.models.LoginRequest
import com.moyi.app.api.models.LogoutRequest
import com.moyi.app.api.models.RegisterRequest
import com.moyi.app.api.models.ResendVerificationRequest
import com.moyi.app.api.models.VerifyEmailRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.session.toSessionGrant
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * [AuthRepository] over the generated auth APIs.
 *
 * Every call here goes over the PUBLIC client, which the type of [publicClient]
 * enforces: these endpoints are unauthenticated, and a `401` from login is a
 * wrong password, not an expired token, so it must never start a refresh.
 */
class AuthRepositoryImpl(
    publicClient: PublicHttpClient,
    config: ApiConfig,
    private val sessionManager: SessionManager,
    private val deviceInfo: DeviceInfoProvider,
) : AuthRepository {
    private val registration = RegistrationControllerApi(config.origin, publicClient.client)
    private val verification = VerificationControllerApi(config.origin, publicClient.client)
    private val loginApi = LoginControllerApi(config.origin, publicClient.client)
    private val logoutApi = LogoutControllerApi(config.origin, publicClient.client)

    override val session: StateFlow<SessionState> = sessionManager.sessionState
    override val sessionEvents: Flow<SessionEvent> = sessionManager.events

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
        locale: String,
        acceptedTermsVersion: String,
        over18: Boolean,
    ): EmptyResult<DataError.Network> = safeApiCallEmpty {
        // 201 with no body: only the status is checked, the body is never decoded.
        registration.register(
            RegisterRequest(
                acceptedTermsVersion = acceptedTermsVersion,
                displayName = displayName,
                email = email,
                locale = locale,
                password = password,
                over18 = over18,
            ),
        )
    }

    override suspend fun verifyEmail(token: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { verification.verify(VerifyEmailRequest(token)) }

    override suspend fun resendVerification(email: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { verification.resend(ResendVerificationRequest(email)) }

    override suspend fun login(email: String, password: String): Result<User, DataError> {
        val request = LoginRequest(email = email, password = password, device = deviceInfo.deviceInfo().toRequest())
        val grant = when (val response = safeApiCall { loginApi.login(request) }) {
            is Result.Success -> response.data.toSessionGrant()
            is Result.Failure -> return response
        }
        return when (grant) {
            is Result.Failure -> grant
            is Result.Success -> try {
                sessionManager.startSession(grant.data)
                Result.Success(grant.data.user)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Signed in on the server, but the session could not be kept
                // here; the refresh family simply expires unused.
                Result.Failure(DataError.Local.UNKNOWN)
            }
        }
    }

    /**
     * Ends the session here first, then tells the server (best effort; it
     * answers `204` whatever the token). The local wipe cannot be skipped by
     * a dead network or a closed screen.
     */
    override suspend fun logout() {
        val refreshToken = sessionManager.storedRefreshToken()
        sessionManager.endSession(SessionEndReason.SignedOut)
        if (refreshToken != null) {
            safeApiCallEmpty { logoutApi.logout(LogoutRequest(refreshToken)) }
        }
    }

    override suspend fun restoreSession(): SessionState = sessionManager.restore()
}
