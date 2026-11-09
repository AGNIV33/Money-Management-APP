package com.agniv.hisaab.core.common.money

import com.agniv.hisaab.core.common.money.MoneyFormatter.Paise
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun `indian grouping`() {
        assertThat(MoneyFormatter.groupIndian("0")).isEqualTo("0")
        assertThat(MoneyFormatter.groupIndian("999")).isEqualTo("999")
        assertThat(MoneyFormatter.groupIndian("1000")).isEqualTo("1,000")
        assertThat(MoneyFormatter.groupIndian("100000")).isEqualTo("1,00,000")
        assertThat(MoneyFormatter.groupIndian("125000")).isEqualTo("1,25,000")
        assertThat(MoneyFormatter.groupIndian("12345678")).isEqualTo("1,23,45,678")
        assertThat(MoneyFormatter.groupIndian("123456789")).isEqualTo("12,34,56,789")
    }

    @Test
    fun `format with paise when needed`() {
        assertThat(Money(12_500_050).formatted()).isEqualTo("₹1,25,000.50")
        assertThat(Money.ofRupees(32_450).formatted()).isEqualTo("₹32,450")
        assertThat(Money(5).formatted()).isEqualTo("₹0.05")
        assertThat(Money.ZERO.formatted()).isEqualTo("₹0")
    }

    @Test
    fun `format always and never`() {
        assertThat(Money.ofRupees(250).formatted(Paise.ALWAYS)).isEqualTo("₹250.00")
        assertThat(Money(25_099).formatted(Paise.NEVER)).isEqualTo("₹250")
    }

    @Test
    fun `format negative`() {
        assertThat(Money(-230_000).formatted()).isEqualTo("-₹2,300")
        assertThat(Money(-550).formatted()).isEqualTo("-₹5.50")
        assertThat(Money(-50).formatted()).isEqualTo("-₹0.50")
    }

    @Test
    fun `format without symbol`() {
        assertThat(MoneyFormatter.format(Money.ofRupees(1_500), withSymbol = false)).isEqualTo("1,500")
    }
}
