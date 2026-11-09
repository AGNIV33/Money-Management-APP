package com.agniv.hisaab.core.common.result

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Test

class ResultExtTest {
    @Test
    fun `resultOf wraps success and failure`() {
        assertThat(resultOf { 42 }.getOrNull()).isEqualTo(42)
        val failure = resultOf { error("boom") }
        assertThat(failure.isFailure).isTrue()
        assertThat(failure.exceptionOrNull()).hasMessageThat().isEqualTo("boom")
    }

    @Test(expected = CancellationException::class)
    fun `resultOf rethrows cancellation`() {
        resultOf { throw CancellationException("stop") }
    }

    @Test
    fun `andThen chains and keeps first failure`() {
        assertThat(Result.success(2).andThen { Result.success(it * 3) }.getOrNull()).isEqualTo(6)
        val first = IllegalStateException("first")
        val chained = Result.failure<Int>(first).andThen { Result.success(it) }
        assertThat(chained.exceptionOrNull()).isSameInstanceAs(first)
    }
}
