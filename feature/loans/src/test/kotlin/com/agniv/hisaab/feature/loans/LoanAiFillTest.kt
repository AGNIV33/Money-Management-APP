package com.agniv.hisaab.feature.loans

import com.agniv.hisaab.core.domain.loan.LoanExtract
import com.agniv.hisaab.core.domain.model.LoanType
import com.agniv.hisaab.core.ml.report.PrivacyMask
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class LoanAiFillTest {
    private val start = AddLoanUiState(firstEmiDate = LocalDate.of(2026, 12, 10), lender = "Old bank", amount = "999")

    private fun found(
        type: LoanType? = null,
        lender: String? = null,
        principal: Long? = null,
        rate: Double? = null,
        months: Int? = null,
        emi: Long? = null,
        date: LocalDate? = null,
    ) = LoanExtract(type, lender, principal, rate, months, emi, date)

    @Test
    fun `a full result fills every field and whole years show as years`() {
        val state =
            start.withExtract(
                found(LoanType.HOME, "HDFC Bank", 2_500_000, 8.75, 240, 22_093, LocalDate.of(2026, 12, 5)),
            )
        assertThat(state.type).isEqualTo(LoanType.HOME)
        assertThat(state.lender).isEqualTo("HDFC Bank")
        assertThat(state.amount).isEqualTo("2500000")
        assertThat(state.rate).isEqualTo("8.75")
        assertThat(state.tenure).isEqualTo("20")
        assertThat(state.tenureInYears).isTrue()
        assertThat(state.emi).isEqualTo("22093")
        assertThat(state.firstEmiDate).isEqualTo(LocalDate.of(2026, 12, 5))
        assertThat(state.toDraft()).isNotNull()
    }

    @Test
    fun `months that are not whole years stay in months`() {
        val state = start.withExtract(found(months = 18))
        assertThat(state.tenure).isEqualTo("18")
        assertThat(state.tenureInYears).isFalse()
    }

    @Test
    fun `fields the AI did not find keep what the person already typed`() {
        val state = start.withExtract(found(principal = 50_000))
        assertThat(state.lender).isEqualTo("Old bank")
        assertThat(state.amount).isEqualTo("50000")
        assertThat(state.firstEmiDate).isEqualTo(LocalDate.of(2026, 12, 10))
    }

    @Test
    fun `numbers outside the loan limits are ignored instead of breaking the form`() {
        val state = start.withExtract(found(principal = 5_000_000_000_000, rate = 55.0, months = 9_999, emi = 9_999_999_999_999))
        assertThat(state.amount).isEqualTo("999")
        assertThat(state.rate).isEmpty()
        assertThat(state.tenure).isEmpty()
        assertThat(state.emi).isEmpty()
    }

    @Test
    fun `account, phone and id numbers are hidden before the text is sent`() {
        val letter = "Dear Ramesh, loan account 123456789012 sanctioned Rs 5,00,000 at 9.5% call 9876543210 PAN ABCDE1234F mail a@b.com"
        val masked = PrivacyMask.mask(letter)
        assertThat(masked).doesNotContain("123456789012")
        assertThat(masked).doesNotContain("9876543210")
        assertThat(masked).doesNotContain("ABCDE1234F")
        assertThat(masked).doesNotContain("a@b.com")
        assertThat(masked).contains("5,00,000")
        assertThat(masked).contains("9.5%")
    }
}
