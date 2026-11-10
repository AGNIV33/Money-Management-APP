package com.agniv.hisaab.core.ml.statement

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** OCR-style text of typical Indian statements and passbooks. */
class StatementParserTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun parse(text: String) = StatementParser.parse(text.trimIndent(), today)

    @Test
    fun `bank statement with withdrawal and balance columns`() {
        val lines =
            parse(
                """
                HDFC BANK  Statement of account
                Date       Narration                         Chq/Ref     Withdrawal   Deposit   Balance
                01/11/26   UPI-SWIGGY-swiggy@hdfcbank-3301234567   350.00      12,450.00
                03/11/26   ATM WDL SBI NAGPUR                  2,000.00            10,450.00
                05/11/26   NEFT CR-ACME LTD-SALARY                       45,000.00  55,450.00
                Closing Balance 55,450.00
                """,
            )
        assertThat(lines).hasSize(3)
        assertThat(lines[0].date).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(lines[0].amount.paise).isEqualTo(35000)
        assertThat(lines[0].balance?.paise).isEqualTo(1245000)
        assertThat(lines[0].description).contains("SWIGGY")
        assertThat(lines[0].description).doesNotContain("3301234567")
        assertThat(lines[1].debit).isTrue() // ATM WDL
        assertThat(lines[1].amount.paise).isEqualTo(200000)
        assertThat(lines[2].debit).isFalse() // CR / SALARY
        assertThat(lines[2].amount.paise).isEqualTo(4500000)
    }

    @Test
    fun `debit credit decided by the balance when words do not say`() {
        val lines =
            parse(
                """
                01/11/2026 Shop A 500.00 9,500.00
                02/11/2026 Shop B 200.00 9,300.00
                03/11/2026 Deposit slip 1,000.00 10,300.00
                04/11/2026 Shop C 300.00 10,000.00
                """,
            )
        // The first row has no earlier balance to compare with; the rest are decided by it.
        assertThat(lines.map { it.debit }).containsExactly(null, true, false, true).inOrder()
    }

    @Test
    fun `newest first statements are compared the other way round`() {
        val lines =
            parse(
                """
                04/11/2026 Shop C 300.00 10,000.00
                03/11/2026 Deposit slip 1,000.00 10,300.00
                02/11/2026 Shop B 200.00 9,300.00
                """,
            )
        assertThat(lines.map { it.debit }.take(2)).containsExactly(true, false).inOrder()
    }

    @Test
    fun `passbook with named months, serial numbers and a second value date`() {
        val lines =
            parse(
                """
                1  02 Nov 2026  02 Nov 2026  BY CASH DEPOSIT  5,000.00  25,000.00
                2  04-Nov-26  04-Nov-26  UPI/DR/330100011/BIGBASKET  1,230.50  23,769.50
                """,
            )
        assertThat(lines).hasSize(2)
        assertThat(lines[0].date).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(lines[0].debit).isFalse()
        assertThat(lines[1].date).isEqualTo(LocalDate.of(2026, 11, 4))
        assertThat(lines[1].debit).isTrue()
        assertThat(lines[1].amount.paise).isEqualTo(123050)
        assertThat(lines[1].description).contains("BIGBASKET")
    }

    @Test
    fun `narration wrapped onto the next line is joined`() {
        val lines = parse("06/11/2026 UPI/DR/ 250.00 7,000.00\nRAMESH KIRANA STORE\n07/11/2026 POS 100.00 6,900.00")
        assertThat(lines).hasSize(2)
        assertThat(lines[0].description).contains("RAMESH KIRANA STORE")
    }

    @Test
    fun `junk, future dates and rows without paise are ignored`() {
        val lines =
            parse(
                """
                Page 1 of 3
                Opening Balance 10,000.00
                15/12/2026 Future 100.00 9,900.00
                06/11/2026 No amount here
                06/11/2026 Ref 12345 and 2000
                08/11/2026 Real one 99.00 9,901.00
                """,
            )
        assertThat(lines.map { it.amount.paise }).containsExactly(9900L)
    }

    @Test
    fun `empty text gives nothing`() {
        assertThat(parse("")).isEmpty()
        assertThat(parse("just some words")).isEmpty()
    }
}
