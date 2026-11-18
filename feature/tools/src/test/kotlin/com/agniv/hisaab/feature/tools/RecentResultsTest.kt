package com.agniv.hisaab.feature.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentResultsTest {
    private fun r(
        at: Long,
        a: Long,
    ) = RecentResult(Tool.EMI.name, at, a, 240, 300_000_000)

    @Test
    fun `encode and decode round trip`() {
        val one = r(1L, 1_245_000)
        assertThat(RecentResult.decode(one.encode())).isEqualTo(one)
        assertThat(RecentResult.decode("junk")).isNull()
        assertThat(RecentResult.decode("EMI;x;1;2;3")).isNull()
    }

    @Test
    fun `newest first, no repeats, at most five`() {
        val list = (1L..5L).map { r(it, it) }
        val again = r(9L, 3L)
        val out = list.withNewest(again)
        assertThat(out.first()).isEqualTo(again)
        assertThat(out.count { it.a == 3L }).isEqualTo(1)
        assertThat(list.withNewest(r(10L, 99L))).hasSize(MAX_RECENT)
    }
}
