package com.agniv.hisaab.core.common.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {
    @Test
    fun `ofRupees stores paise`() {
        assertThat(Money.ofRupees(250).paise).isEqualTo(25_000L)
        assertThat(Money.ofRupees(12, 5).paise).isEqualTo(1_205L)
        assertThat(Money.ofRupees(-5, 50).paise).isEqualTo(-550L)
    }

    @Test
    fun `arithmetic is exact`() {
        // 0.10 + 0.20 is the classic Double trap; paise must give exactly 0.30
        assertThat(Money(10) + Money(20)).isEqualTo(Money(30))
        assertThat(Money(500) - Money(750)).isEqualTo(Money(-250))
        assertThat(Money(333) * 3).isEqualTo(Money(999))
        assertThat(-Money(40)).isEqualTo(Money(-40))
        assertThat(Money(-40).abs()).isEqualTo(Money(40))
    }

    @Test
    fun `sum of list`() {
        assertThat(listOf(Money(100), Money(250), Money(-50)).sum()).isEqualTo(Money(300))
        assertThat(emptyList<Money>().sum()).isEqualTo(Money.ZERO)
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow throws instead of wrapping`() {
        Money(Long.MAX_VALUE) + Money(1)
    }

    @Test
    fun `rupee and paise parts`() {
        val m = Money(-12_345)
        assertThat(m.rupeesPart).isEqualTo(-123L)
        assertThat(m.paisePart).isEqualTo(45L)
    }

    @Test
    fun `parse accepts common inputs`() {
        assertThat(Money.parse("250")).isEqualTo(Money(25_000))
        assertThat(Money.parse("250.5")).isEqualTo(Money(25_050))
        assertThat(Money.parse("250.05")).isEqualTo(Money(25_005))
        assertThat(Money.parse("₹ 1,25,000.50")).isEqualTo(Money(12_500_050))
        assertThat(Money.parse(".5")).isEqualTo(Money(50))
        assertThat(Money.parse("7.")).isEqualTo(Money(700))
        assertThat(Money.parse("-40")).isEqualTo(Money(-4_000))
        assertThat(Money.parse("  99.99 ")).isEqualTo(Money(9_999))
    }

    @Test
    fun `parse rejects bad inputs`() {
        listOf("", " ", ".", "abc", "12.345", "1.2.3", "--5", "5-", "1e3", "₹").forEach {
            assertThat(Money.parse(it)).isNull()
        }
    }

    @Test
    fun `parse rejects overflow`() {
        assertThat(Money.parse("999999999999999999999")).isNull()
    }

    @Test
    fun `compare`() {
        assertThat(Money(100)).isGreaterThan(Money(99))
        assertThat(Money(-1).isNegative).isTrue()
        assertThat(Money.ZERO.isZero).isTrue()
    }
}
