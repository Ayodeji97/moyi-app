package com.moyi.app.core

/** Marker for every typed error the app can return instead of throwing. */
interface Error

/**
 * A typed outcome: [Success] with data, or [Failure] with an [Error]. Expected
 * failures (no network, wrong password, invalid input) are values, not
 * exceptions.
 */
sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>

    data class Failure<out E : com.moyi.app.core.Error>(val error: E) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>

inline fun <T, E : Error, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> =
    when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Failure -> this
    }

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T, E : Error> Result<T, E>.onFailure(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) action(error)
    return this
}

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> = map { }
