package com.agniv.hisaab.lock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinAttemptsTest {
    @Test
    fun `four wrong tries say how many are left`() {
        val a = PinAttempts()
        assertThat(a.onWrong(now = 0)).isEqualTo(PinCheck.Wrong(4))
        repeat(2) { a.onWrong(now = 0) }
        assertThat(a.onWrong(now = 0)).isEqualTo(PinCheck.Wrong(1))
    }

    @Test
    fun `fifth wrong try waits 30 seconds, then the count starts again`() {
        val a = PinAttempts()
        repeat(4) { a.onWrong(now = 1_000) }
        assertThat(a.onWrong(now = 1_000)).isEqualTo(PinCheck.LockedOut(31_000))
        assertThat(a.lockedAt(30_999)).isTrue()
        assertThat(a.lockedAt(31_000)).isFalse()
        assertThat(a.onWrong(now = 31_000)).isEqualTo(PinCheck.Wrong(4))
    }

    @Test
    fun `right PIN clears the count`() {
        val a = PinAttempts()
        repeat(3) { a.onWrong(now = 0) }
        a.onRight()
        assertThat(a.wrong).isEqualTo(0)
        assertThat(a.onWrong(now = 0)).isEqualTo(PinCheck.Wrong(4))
    }
}
