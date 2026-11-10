package com.agniv.hisaab.core.ml.report

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class ReportPrivacyTest {
    // ---- the totals-only request -----------------------------------------------------------------------------

    private val cats =
        listOf(
            Category("c-groc", null, "Groceries", null, "x", 0, true),
            Category("c-milk", null, "Milk", null, "x", 0, true),
            // A family typed a person's name as a category: it must not leave the phone.
            Category("c-ramesh", "f1", "Ramesh uncle", null, "x", 0, false),
        )

    private fun e(
        id: String,
        month: Int,
        rupees: Long,
        category: String,
        note: String? = "secret note",
    ) = Expense(
        id,
        "f1",
        "m1",
        "u1",
        Money.ofRupees(rupees),
        category,
        LocalDate.of(2026, month, 10),
        note,
        PayMode.UPI,
        Visibility.FAMILY,
        Source.MANUAL,
        null,
        Instant.EPOCH,
    )

    @Test
    fun `request holds only default category totals`() {
        val req =
            ReportPayloadBuilder.build(
                "f1",
                YearMonth.of(2026, 10),
                "hi",
                listOf(
                    e("1", 10, 5_000, "c-groc"),
                    e("2", 10, 300, "c-milk"),
                    e("3", 10, 700, "c-ramesh"),
                    e("4", 9, 4_000, "c-groc"),
                    e("5", 8, 999, "c-groc"),
                ),
                cats,
                budget = Money.ofRupees(20_000),
                monthlyEmi = Money.ofRupees(2_000),
            )
        assertThat(req.month).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(req.totalPaise).isEqualTo(600_000)
        assertThat(req.previousTotalPaise).isEqualTo(400_000)
        assertThat(req.budgetPaise).isEqualTo(2_000_000)
        assertThat(req.emiSharePercent).isEqualTo(33)
        assertThat(req.categories.map { it.name }).containsExactly("Groceries", "Others", "Milk").inOrder()
        assertThat(req.categories.first().thisPaise).isEqualTo(500_000)
        assertThat(req.categories.first().lastPaise).isEqualTo(400_000)
        val everything = req.toString()
        assertThat(everything).doesNotContain("Ramesh")
        assertThat(everything).doesNotContain("secret note")
    }

    @Test
    fun `no budget and no emi give nulls`() {
        val req = ReportPayloadBuilder.build("f1", YearMonth.of(2026, 10), "en", listOf(e("1", 10, 100, "c-groc")), cats, null, Money.ZERO)
        assertThat(req.budgetPaise).isNull()
        assertThat(req.emiSharePercent).isNull()
    }

    // ---- masking text for the cloud -----------------------------------------------------------------------

    @Test
    fun `personal numbers are hidden, amounts and rates are kept`() {
        val text =
            """
            HDFC Bank Home Loan letter. Loan account no 50100123456789. Sanction ₹25,00,000 at 8.50% for 240 months, EMI Rs 21,696.
            Customer mobile +91 98765 43210, email ramesh.k@gmail.com, PAN ABCDE1234F, Aadhaar 1234 5678 9012.
            IFSC HDFC0001234. UPI ramesh@okhdfcbank. Card 4111 1111 1111 1111.
            """.trimIndent()
        val masked = PrivacyMask.mask(text)
        listOf(
            "50100123456789",
            "98765 43210",
            "ramesh.k@gmail.com",
            "ABCDE1234F",
            "1234 5678 9012",
            "HDFC0001234",
            "okhdfcbank",
            "4111",
        ).forEach {
            assertThat(masked).doesNotContain(it)
        }
        listOf("₹25,00,000", "8.50%", "240 months", "Rs 21,696").forEach { assertThat(masked).contains(it) }
    }

    @Test
    fun `plain text is unchanged`() {
        val text = "Car loan from SBI, 5 years at 9.2%, EMI 10,500 on the 5th"
        assertThat(PrivacyMask.mask(text)).isEqualTo(text)
    }
}
