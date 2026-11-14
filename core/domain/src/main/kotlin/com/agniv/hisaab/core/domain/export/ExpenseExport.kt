package com.agniv.hisaab.core.domain.export

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Visibility
import java.time.LocalDate
import java.time.YearMonth

/** The date ranges offered for export (5.6). Simple choices instead of a date picker: fewer taps, fewer mistakes. */
enum class ExportRange {
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    THIS_YEAR,
    ALL,
    ;

    /** First and last day included. ALL starts at the oldest entry, or today when there are none. */
    fun bounds(
        today: LocalDate,
        oldest: LocalDate?,
    ): Pair<LocalDate, LocalDate> {
        val month = YearMonth.from(today)
        return when (this) {
            THIS_MONTH -> month.atDay(1) to today
            LAST_MONTH -> month.minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
            LAST_3_MONTHS -> month.minusMonths(2).atDay(1) to today
            THIS_YEAR -> today.withDayOfYear(1) to today
            ALL -> (oldest?.takeIf { it < today } ?: today) to today
        }
    }
}

/** One expense, already turned into words the reader understands (names, not ids). */
data class ExportRow(
    val date: LocalDate,
    val amount: Money,
    val category: String,
    val paidBy: String,
    val mode: String,
    val note: String,
    val onlyMe: Boolean,
)

/** Column titles in the user's language. */
data class ExportLabels(
    val date: String,
    val amount: String,
    val category: String,
    val paidBy: String,
    val mode: String,
    val note: String,
    val visibility: String,
    val family: String,
    val onlyMe: String,
)

object ExpenseExport {
    /**
     * Expenses the viewer may see, oldest first, as rows. The caller passes only what the viewer's own queries
     * returned, so another member's "Only me" entries can never reach a file (CLAUDE.md rule 7).
     */
    fun rows(
        expenses: List<Expense>,
        categoryName: (String) -> String,
        memberName: (String) -> String,
        modeName: (String) -> String,
    ): List<ExportRow> =
        expenses
            .filter { !it.deleted }
            .sortedWith(compareBy<Expense> { it.date }.thenBy { it.id })
            .map {
                ExportRow(
                    date = it.date,
                    amount = it.amount,
                    category = categoryName(it.categoryId),
                    paidBy = memberName(it.paidBy),
                    mode = modeName(it.mode.name),
                    note = it.note.orEmpty(),
                    onlyMe = it.visibility == Visibility.PRIVATE,
                )
            }

    fun total(rows: List<ExportRow>): Money = Money(rows.sumOf { it.amount.paise })

    /** Plain amount for spreadsheets: 1234.50, no ₹ sign or commas, from paise without floating point. */
    fun plainAmount(amount: Money): String {
        val sign = if (amount.isNegative) "-" else ""
        val abs = kotlin.math.abs(amount.paise)
        return "$sign${abs / PAISE}.${(abs % PAISE).toString().padStart(2, '0')}"
    }

    /**
     * RFC 4180 CSV with a UTF-8 byte order mark so Excel shows Hindi correctly. Dates are ISO (2026-11-14) so
     * every spreadsheet reads them the same way.
     */
    fun csv(
        rows: List<ExportRow>,
        labels: ExportLabels,
    ): String =
        buildString {
            append(BOM)
            appendLine(
                listOf(labels.date, labels.amount, labels.category, labels.paidBy, labels.mode, labels.note, labels.visibility),
            )
            rows.forEach {
                appendLine(
                    listOf(
                        it.date.toString(),
                        plainAmount(it.amount),
                        it.category,
                        it.paidBy,
                        it.mode,
                        it.note,
                        if (it.onlyMe) labels.onlyMe else labels.family,
                    ),
                )
            }
        }

    private fun StringBuilder.appendLine(cells: List<String>) {
        append(cells.joinToString(",") { cell(it) })
        append("\r\n")
    }

    /**
     * Quotes when needed, and defuses spreadsheet formulas: a note typed as "=HYPERLINK(...)" or "+91..." must
     * open as text, never run (CSV injection).
     */
    internal fun cell(raw: String): String {
        val text = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val safe = if (text.isNotEmpty() && text[0] in FORMULA_START) "'$text" else text
        return if (safe.any { it == ',' || it == '"' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private const val PAISE = 100L
    private const val BOM = "﻿"
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t')
}
