package com.intec.immichfire.data

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Single error shape that every repository call can surface, so ViewModels and composables
 * don't have to pick apart Retrofit/OkHttp exceptions in each place.
 */
sealed class ImmichError(val displayMessage: String) {
    data object NotConfigured : ImmichError("Server not configured")
    data object NoNetwork : ImmichError("No network connection")
    data object Timeout : ImmichError("Request timed out")
    data object Unauthorized : ImmichError("API key rejected by the server")
    data class Http(val code: Int) : ImmichError("Server returned HTTP $code")
    data class Unknown(val reason: String) : ImmichError(reason)

    companion object {
        fun from(t: Throwable): ImmichError = when (t) {
            is UnknownHostException -> NoNetwork
            is SocketTimeoutException -> Timeout
            is HttpException -> when (t.code()) {
                401, 403 -> Unauthorized
                else -> Http(t.code())
            }
            is IOException -> NoNetwork
            else -> Unknown(t.message ?: t.javaClass.simpleName)
        }
    }
}

/** Typed result the repository returns. Replaces ad-hoc runCatching per screen. */
sealed class ApiResult<out T> {
    data class Success<out T>(val value: T) : ApiResult<T>()
    data class Failure(val error: ImmichError) : ApiResult<Nothing>()
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> = also {
    if (this is ApiResult.Success) block(value)
}

inline fun <T> ApiResult<T>.onFailure(block: (ImmichError) -> Unit): ApiResult<T> = also {
    if (this is ApiResult.Failure) block(error)
}
