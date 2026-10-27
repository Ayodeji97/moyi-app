package com.moyi.app.core

/** Failures from the data layer. */
sealed interface DataError : Error {
    /**
     * A remote call that did not succeed. Either the request never got an
     * answer ([NoInternet], [Timeout]), the answer could not be read
     * ([Serialization], [Server], [Unknown]), or the server refused it and
     * said why ([Api]). Callers switch on [ApiProblem.code], never on prose.
     */
    sealed interface Network : DataError {
        /** No route to the server: offline, DNS failure, connection refused or reset. */
        data object NoInternet : Network

        /** The server did not answer in time. */
        data object Timeout : Network

        /** A 2xx whose body did not match the contract. */
        data object Serialization : Network

        /** A 5xx without a readable problem body, such as a proxy's HTML error page. */
        data class Server(val status: Int) : Network

        /** The server refused the request with a problem response (doc 06 §2). */
        data class Api(val problem: ApiProblem) : Network

        /** Anything else. */
        data object Unknown : Network
    }

    enum class Local : DataError {
        NOT_FOUND,
        UNKNOWN,
    }
}
