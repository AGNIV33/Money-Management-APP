package com.agniv.hisaab.feature.loans

import com.agniv.hisaab.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LoanFormTest {
    @Test
    fun `rate text becomes basis points`() {
        assertThat(LoanForm.parseRateBps("8.5")).isEqualTo(850)
        assertThat(LoanForm.parseRateBps(" 10 ")).isEqualTo(1_000)
        assertThat(LoanForm.parseRateBps("8.75%")).isEqualTo(875)
        assertThat(LoanForm.parseRateBps("0")).isEqualTo(0)
        assertThat(LoanForm.parseRateBps("42")).isEqualTo(4_200)
    }

    @Test
    fun `bad rates are rejected`() {
        listOf("", ".5", "8.125", "42.01", "abc", "8..5", "-1", "100").forEach {
            assertThat(LoanForm.parseRateBps(it)).isNull()
        }
    }

    @Test
    fun `rate round trips to text`() {
        assertThat(LoanForm.rateText(850)).isEqualTo("8.5")
        assertThat(LoanForm.rateText(875)).isEqualTo("8.75")
        assertThat(LoanForm.rateText(1_000)).isEqualTo("10")
        assertThat(LoanForm.rateText(5)).isEqualTo("0.05")
    }

    @Test
    fun `tenure in years or months`() {
        assertThat(LoanForm.parseTenureMonths("20", inYears = true)).isEqualTo(240)
        assertThat(LoanForm.parseTenureMonths("18", inYears = false)).isEqualTo(18)
        assertThat(LoanForm.parseTenureMonths("41", inYears = true)).isNull()
        assertThat(LoanForm.parseTenureMonths("0", inYears = false)).isNull()
        assertThat(LoanForm.parseTenureMonths("1.5", inYears = true)).isNull()
    }

    @Test
    fun `formula emi appears once all three fields are valid`() {
        assertThat(LoanForm.formulaEmi("1000000", "8.5", "20", inYears = true)).isEqualTo(Money.ofRupees(8_678, 23))
        assertThat(LoanForm.formulaEmi("1000000", "", "20", inYears = true)).isNull()
        assertThat(LoanForm.formulaEmi("100000", "42", "40", inYears = true)).isNull()
    }

    @Test
    fun `amount text has no symbol or commas`() {
        assertThat(LoanForm.amountText(Money.ofRupees(500_000))).isEqualTo("500000")
        assertThat(LoanForm.amountText(Money.ofRupees(8_678, 5))).isEqualTo("8678.05")
    }
}
