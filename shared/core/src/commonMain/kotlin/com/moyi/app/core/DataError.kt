package com.moyi.app.core

/**
 * Failures from the data layer. The network cases follow the backend's error
 * envelope and are refined in PR #3, when ProblemDetail mapping lands.
 */
sealed interface DataError : Error {
    enum class Network : DataError {
        BAD_REQUEST,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        GONE,
        UNPROCESSABLE,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        SERVER_ERROR,
        SERIALIZATION,
        UNKNOWN,
    }

    enum class Local : DataError {
        NOT_FOUND,
        UNKNOWN,
    }
}
