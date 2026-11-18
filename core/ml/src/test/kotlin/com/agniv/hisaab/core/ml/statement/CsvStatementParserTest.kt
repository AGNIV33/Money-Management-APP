package com.agniv.hisaab.core.ml.statement

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class CsvStatementParserTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun parse(text: String) = CsvStatementParser.parse(text.trimIndent(), today)

    @Test
    fun `withdrawal and deposit columns after account details`() {
        val lines =
            parse(
                """
                Account No,XXXX1234
                Name,Test
                Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
                01/11/26,"UPI-SWIGGY, Order 123",0000123,01/11/26,350.00,,"12,450.00"
                05/11/26,NEFT CR-ACME LTD-SALARY,0000124,05/11/26,,"45,000.00","57,450.00"
                Total,,,,350.00,45000.00,
                """,
            )
        assertThat(lines).hasSize(2)
        assertThat(lines[0].date).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(lines[0].description).isEqualTo("UPI-SWIGGY, Order 123")
        assertThat(lines[0].amount.paise).isEqualTo(35_000)
        assertThat(lines[0].debit).isTrue()
        assertThat(lines[0].balance?.paise).isEqualTo(1_245_000)
        assertThat(lines[1].debit).isFalse()
        assertThat(lines[1].amount.paise).isEqualTo(4_500_000)
    }

    @Test
    fun `one amount column with a Dr Cr column`() {
        val lines =
            parse(
                """
                Txn Date,Description,Amount,Dr/Cr,Balance
                02-Nov-2026,ATM WDL,2000,DR,10000
                03-Nov-2026,Refund,150.5,CR,10150.5
                """,
            )
        assertThat(lines.map { it.debit }).containsExactly(true, false).inOrder()
        assertThat(lines[1].amount.paise).isEqualTo(15_050)
    }

    @Test
    fun `minus sign means money out`() {
        val lines = parse("Date;Details;Amount\n2026-11-04;Electricity bill;-1,234.00")
        assertThat(lines).hasSize(1)
        assertThat(lines[0].debit).isTrue()
        assertThat(lines[0].amount.paise).isEqualTo(123_400)
    }

    @Test
    fun `no header gives nothing`() {
        assertThat(parse("hello,world\n1,2")).isEmpty()
        assertThat(CsvStatementParser.looksLikeCsv("hello,world")).isFalse()
    }

    @Test
    fun `future and very old rows are dropped`() {
        val lines = parse("Date,Narration,Debit,Credit\n01/12/2026,Later,10.00,\n01/01/2020,Old,10.00,\n09/11/2026,Now,10.00,")
        assertThat(lines.map { it.description }).containsExactly("Now")
    }
}
