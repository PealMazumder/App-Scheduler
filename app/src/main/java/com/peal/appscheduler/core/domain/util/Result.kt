package com.peal.appscheduler.core.domain.util

/**
 * Created by Peal Mazumder on 8/3/25.
 */

sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Failure<out E : Error>(val error: E) : Result<Nothing, E>
}


inline fun <T, E : Error, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> {
    return when (this) {
        is Result.Failure -> Result.Failure(error)
        is Result.Success -> Result.Success(transform(data))
    }
}

inline fun <T, E : Error> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Failure -> this
        is Result.Success -> {
            action(data)
            this
        }
    }
}

inline fun <T, E : Error> Result<T, E>.onError(action: (E) -> Unit): Result<T, E> {
    return when (this) {
        is Result.Failure -> {
            action(error)
            this
        }

        is Result.Success -> this
    }
}