package com.agniv.hisaab.core.ml.statement

import com.agniv.hisaab.core.common.money.Money
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

/**
 * Reads a bank statement saved as CSV (net banking "Download as CSV/Excel") into the same rows the PDF and
 * photo reader makes, so the review screen stays one flow. Columns are found by their header words, because
 * every bank names and orders them a little differently:
 * - date: "Date", "Txn Date", "Transaction Date", "Value Date" (the first one wins)
 * - text: "Narration", "Description", "Particulars", "Remarks", "Details"
 * - money out / in: "Withdrawal", "Debit", "Dr" / "Deposit", "Credit", "Cr"
 * - or one "Amount" column with a "Dr/Cr" or "Type" column (or a minus sign) for the direction
 * - "Balance" when present
 * Lines above the header (account details) and below the rows (totals) are skipped.
 */
@Suppress("MagicNumber", "ReturnCount", "CyclomaticComplexMethod") // column word tables and early exits for non-rows
object CsvStatementParser {
    private const val MAX_DESCRIPTION = 120
    private const val MAX_AGE_DAYS = 1100L

    private class Columns(
        val date: Int,
        val text: Int?,
        val debit: Int?,
        val credit: Int?,
        val amount: Int?,
        val type: Int?,
        val balance: Int?,
    )

    /** True when [text] looks like a CSV statement this parser can read (has a header row). */
    fun looksLikeCsv(text: String): Boolean = findHeader(text.lines()) != null

    fun parse(
        text: String,
        today: LocalDate,
    ): List<StatementLine> {
        val lines = text.removePrefix("\uFEFF").lines()
        val (headerAt, sep) = findHeader(lines) ?: return emptyList()
        val cols = header(split(lines[headerAt], sep)) ?: return emptyList()
        return lines
            .drop(headerAt + 1)
            .mapNotNull { row(split(it, sep), cols) }
            .filter { it.date <= today && it.date >= today.minusDays(MAX_AGE_DAYS) }
    }

    /** Index of the header line and the separator it uses (comma, semicolon or tab), or null. */
    private fun findHeader(lines: List<String>): Pair<Int, Char>? {
        for ((i, line) in lines.take(HEADER_SEARCH_LINES).withIndex()) {
            for (sep in SEPARATORS) {
                if (sep in line && header(split(line, sep)) != null) return i to sep
            }
        }
        return null
    }

    private fun header(cells: List<String>): Columns? {
        val names = cells.map { it.lowercase().replace(Regex("""[^a-z/ ]"""), " ").trim() }

        fun find(vararg words: String): Int? =
            names.indexOfFirst { name -> words.any { w -> name == w || name.split(' ', '/').contains(w) } }.takeIf { it >= 0 }

        val date = find("date", "txn date", "transaction date", "value date") ?: return null
        val text = find("narration", "description", "particulars", "remarks", "details", "transaction details")
        val debit = find("withdrawal", "withdrawals", "debit", "dr amount", "withdrawal amt")
        val credit = find("deposit", "deposits", "credit", "cr amount", "deposit amt")
        val amount = find("amount", "amt", "transaction amount")
        val type = find("dr/cr", "cr/dr", "type", "txn type")
        val balance = find("balance", "closing balance", "running balance")
        // Debit/Credit header words can also match a single "Dr/Cr" type column; keep them apart.
        val realDebit = debit?.takeIf { it != type }
        val realCredit = credit?.takeIf { it != type }
        if (realDebit == null && realCredit == null && amount == null) return null
        return Columns(date, text, realDebit, realCredit, amount, type, balance)
    }

    private fun row(
        cells: List<String>,
        c: Columns,
    ): StatementLine? {
        val date = cells.getOrNull(c.date)?.let(::date) ?: return null
        val description = c.text?.let { cells.getOrNull(it) }.orEmpty().replace(Regex("""\s+"""), " ").trim().take(MAX_DESCRIPTION)
        val out = c.debit?.let { cells.getOrNull(it) }?.let(::money)?.takeIf { it.paise > 0 }
        val inn = c.credit?.let { cells.getOrNull(it) }?.let(::money)?.takeIf { it.paise > 0 }
        val balance = c.balance?.let { cells.getOrNull(it) }?.let(::money)
        val (amount, debit) =
            when {
                out != null -> out to true
                inn != null -> inn to false
                c.amount != null -> {
                    val raw = cells.getOrNull(c.amount).orEmpty().trim()
                    val value = money(raw) ?: return null
                    if (value.paise == 0L) return null
                    val type = c.type?.let { cells.getOrNull(it) }.orEmpty().trim().lowercase()
                    val dir =
                        when {
                            raw.startsWith("-") || raw.endsWith("dr", ignoreCase = true) -> true
                            raw.endsWith("cr", ignoreCase = true) -> false
                            type.startsWith("d") || type == "withdrawal" -> true
                            type.startsWith("c") || type == "deposit" -> false
                            else -> null
                        }
                    Money(abs(value.paise)) to dir
                }
                else -> return null
            }
        return StatementLine(date, description, amount, debit, balance)
    }

    /** "1,234.50", "₹ 1234.5", "-200", "500.00 Dr" → paise; blank or text → null. */
    internal fun money(text: String): Money? {
        val clean = text.replace(Regex("""(?i)\b(dr|cr|inr|rs)\b\.?|[₹,\s]"""), "")
        if (clean.isEmpty() || clean == "-") return null
        val number = clean.toBigDecimalOrNull() ?: return null
        return Money(number.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())
    }

    /** Uses the line parser's date shapes (dd/mm/yyyy, dd-MMM-yy, yyyy-mm-dd). */
    private fun date(text: String): LocalDate? = StatementParser.dateOf(text.trim())

    /** One CSV line into cells; handles quotes and "" inside quotes. */
    internal fun split(
        line: String,
        sep: Char,
    ): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && line.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> {
                    cells += cell.toString().trim()
                    cell.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        cells += cell.toString().trim()
        return cells
    }

    private const val HEADER_SEARCH_LINES = 40
    private val SEPARATORS = charArrayOf(',', ';', '\t')
}
