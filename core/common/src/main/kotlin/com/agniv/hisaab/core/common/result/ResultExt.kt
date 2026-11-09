package com.agniv.hisaab.core.common.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but lets coroutine cancellation through.
 * Swallowing CancellationException would keep a cancelled save/sync running.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> resultOf(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Chains another Result-returning step, keeping the first failure. */
inline fun <T, R> Result<T>.andThen(next: (T) -> Result<R>): Result<R> =
    fold(onSuccess = next, onFailure = { Result.failure(it) })
