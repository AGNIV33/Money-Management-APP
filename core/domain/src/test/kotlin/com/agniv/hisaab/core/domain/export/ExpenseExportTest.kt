package com.agniv.hisaab.core.domain.export

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.domain.model.Visibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ExpenseExportTest {
    private val labels = ExportLabels("Date", "Amount", "Category", "Paid by", "Mode", "Note", "Who sees", "Family", "Only me")

    private fun e(
        id: String,
        date: LocalDate,
        paise: Long,
        note: String? = null,
        visibility: Visibility = Visibility.FAMILY,
        deleted: Boolean = false,
    ) = Expense(
        id,
        "f1",
        "m1",
        "u1",
        Money(paise),
        "food",
        date,
        note,
        PayMode.UPI,
        visibility,
        Source.MANUAL,
        null,
        Instant.EPOCH,
        deleted,
    )

    private fun rows(vararg list: Expense) = ExpenseExport.rows(list.toList(), { "Groceries" }, { "Agniv" }, { "UPI" })

    @Test
    fun `amounts are exact from paise`() {
        assertThat(ExpenseExport.plainAmount(Money(123_450))).isEqualTo("1234.50")
        assertThat(ExpenseExport.plainAmount(Money(5))).isEqualTo("0.05")
        assertThat(ExpenseExport.plainAmount(Money(-1_001))).isEqualTo("-10.01")
    }

    @Test
    fun `csv has bom, header, oldest first, deleted skipped`() {
        val csv =
            ExpenseExport.csv(
                rows(
                    e("b", LocalDate.of(2026, 11, 2), 50_000),
                    e("a", LocalDate.of(2026, 11, 1), 12_345, visibility = Visibility.PRIVATE),
                    e("c", LocalDate.of(2026, 11, 3), 99, deleted = true),
                ),
                labels,
            )
        assertThat(csv).startsWith("﻿")
        val lines = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        assertThat(lines)
            .containsExactly(
                "Date,Amount,Category,Paid by,Mode,Note,Who sees",
                "2026-11-01,123.45,Groceries,Agniv,UPI,,Only me",
                "2026-11-02,500.00,Groceries,Agniv,UPI,,Family",
            ).inOrder()
    }

    @Test
    fun `commas quotes and new lines are escaped`() {
        assertThat(ExpenseExport.cell("milk, bread")).isEqualTo("\"milk, bread\"")
        assertThat(ExpenseExport.cell("5\" pipe")).isEqualTo("\"5\"\" pipe\"")
        assertThat(ExpenseExport.cell("line1\nline2")).isEqualTo("line1 line2")
    }

    @Test
    fun `formulas are turned into text`() {
        assertThat(ExpenseExport.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"")
        assertThat(ExpenseExport.cell("+919876543210")).isEqualTo("'+919876543210")
        assertThat(ExpenseExport.cell("@sum")).isEqualTo("'@sum")
        assertThat(ExpenseExport.cell("Hindi दूध")).isEqualTo("Hindi दूध")
    }

    @Test
    fun `total adds paise`() {
        assertThat(ExpenseExport.total(rows(e("a", LocalDate.of(2026, 1, 1), 10), e("b", LocalDate.of(2026, 1, 2), 20))))
            .isEqualTo(Money(30))
    }

    @Test
    fun `ranges`() {
        val today = LocalDate.of(2026, 11, 14)
        assertThat(ExportRange.THIS_MONTH.bounds(today, null)).isEqualTo(LocalDate.of(2026, 11, 1) to today)
        assertThat(ExportRange.LAST_MONTH.bounds(today, null)).isEqualTo(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31))
        assertThat(ExportRange.LAST_3_MONTHS.bounds(today, null)).isEqualTo(LocalDate.of(2026, 9, 1) to today)
        assertThat(ExportRange.THIS_YEAR.bounds(today, null)).isEqualTo(LocalDate.of(2026, 1, 1) to today)
        assertThat(ExportRange.ALL.bounds(today, LocalDate.of(2024, 5, 3))).isEqualTo(LocalDate.of(2024, 5, 3) to today)
        assertThat(ExportRange.ALL.bounds(today, null)).isEqualTo(today to today)
    }
}
