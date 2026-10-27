package com.moyi.app.data.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * OkHttp's silent retry is off. With it on, OkHttp may resend a request that
 * already reached the server after a connection failure, without the app
 * knowing. For `POST /auth/refresh` that is a second use of the same refresh
 * token, which ADR-0021 treats as theft and answers by signing every session
 * out (docs/session.md, "The lost response"). The app retries on its own terms.
 */
actual fun platformEngine(): HttpClientEngine = OkHttp.create {
    config { retryOnConnectionFailure(false) }
}
