package com.agniv.hisaab.core.ml.statement

import com.agniv.hisaab.core.common.money.Money
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month

/**
 * One row of a bank passbook or statement. [debit] is true for money that left the account, false for
 * money that came in, null when the text does not say. [description] has long reference numbers removed.
 */
data class StatementLine(
    val date: LocalDate,
    val description: String,
    val amount: Money,
    val debit: Boolean?,
    val balance: Money?,
)

/**
 * Reads the text of a statement (from ML Kit on a photo, or a PDF page) into rows (PRD F21).
 * Banks lay columns out differently, so this goes by shape: a line that starts with a date and has one or
 * two amounts with paise is a row; the last amount is the balance when there are two. Debit or credit
 * comes from words (DR, WDL, UPI/DR ... CR, SALARY ...), else from whether the balance went down.
 */
@Suppress("TooManyFunctions", "MagicNumber", "ReturnCount") // date shapes, keyword tables, early exits for non-rows
object StatementParser {
    private const val MAX_DESCRIPTION = 120
    private const val MAX_CONTINUATION = 60
    private const val MAX_AGE_DAYS = 1100L

    private val amountPattern = Regex("""(?<![\w.])(\d{1,3}(?:,\d{2,3})+\.\d{2}|\d+\.\d{2})(?![\w])""")
    private val longNumber = Regex("""\b\d{6,}\b""")
    private val dateAtStart =
        listOf(
            Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b"""),
            Regex("""^(\d{1,2})[\s\-]+([A-Za-z]{3,9})[\s\-,.]+(\d{4}|\d{2})\b"""),
            Regex("""^(\d{4})-(\d{2})-(\d{2})\b"""),
        )
    private val skipLine =
        Regex("""opening balance|closing balance|brought forward|carried forward|\btotal\b|statement of|page \d""", RegexOption.IGNORE_CASE)
    private val headerWords =
        setOf(
            "balance",
            "narration",
            "particulars",
            "date",
            "withdrawal",
            "withdrawals",
            "deposit",
            "deposits",
            "chq",
            "ref",
            "debit",
            "credit",
        )
    private val debitWords =
        setOf("dr", "wdl", "withdrawal", "withdraw", "debit", "pos", "atm", "purchase", "paid", "emi", "nach", "ach", "sent")
    private val creditWords = setOf("cr", "credit", "deposit", "salary", "refund", "interest", "reversal", "cashback", "received")

    fun parse(
        text: String,
        today: LocalDate,
    ): List<StatementLine> {
        val rows = mutableListOf<Row>()
        for (raw in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            if (skipLine.containsMatchIn(raw)) continue
            val row = dated(raw)
            if (row != null) {
                rows += row
            } else if (rows.isNotEmpty() && isContinuation(raw)) {
                rows.last().extra += " " + raw
            }
        }
        val lines =
            rows
                .filter { it.date <= today && it.date >= today.minusDays(MAX_AGE_DAYS) }
                .map { it.toLine() }
        return fillByBalance(lines)
    }

    private class Row(
        val date: LocalDate,
        val text: String,
        val amount: Money,
        val balance: Money?,
        var extra: String = "",
    ) {
        fun toLine(): StatementLine {
            val full = (text + extra).trim()
            val words = full.lowercase().split(Regex("""[^a-z]+""")).toSet()
            val debit =
                when {
                    words.any { it in debitWords } && words.none { it in creditWords } -> true
                    words.any { it in creditWords } && words.none { it in debitWords } -> false
                    else -> null
                }
            return StatementLine(date, clean(full), amount, debit, balance)
        }
    }

    private fun clean(text: String): String =
        text
            .replace(longNumber, " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '/', '-', '|')
            .take(MAX_DESCRIPTION)

    private fun isContinuation(line: String): Boolean {
        if (amountPattern.containsMatchIn(line) || line.length > MAX_CONTINUATION) return false
        val words = line.lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.none { it in headerWords }
    }

    private fun dated(raw: String): Row? {
        var rest = raw
        var date = dateAt(rest)
        if (date == null) {
            // A serial number may come first: "12  05/11/2026 ...".
            val stripped = rest.replaceFirst(Regex("""^\d{1,3}\s+"""), "")
            date = dateAt(stripped)
            if (date != null) rest = stripped
        }
        val (d, afterDate) = date ?: return null
        rest = afterDate.trim()
        // Many statements print a second "value date" right after the first.
        dateAt(rest)?.let { rest = it.second.trim() }
        val amounts = amountPattern.findAll(rest).toList()
        if (amounts.isEmpty()) return null
        val values =
            amounts.mapNotNull { m ->
                m.value
                    .replace(",", "")
                    .toBigDecimalOrNull()
                    ?.let { Money(it.movePointRight(2).toLong()) }
            }
        if (values.isEmpty()) return null
        val amount = if (values.size >= 2) values[values.size - 2] else values[0]
        val balance = if (values.size >= 2) values.last() else null
        if (amount.paise <= 0) return null
        val description = amountPattern.replace(rest, " ")
        return Row(d, description, amount, balance)
    }

    /** Date at the very start of [text] and what follows it, or null. */
    private fun dateAt(text: String): Pair<LocalDate, String>? {
        for ((i, regex) in dateAtStart.withIndex()) {
            val m = regex.find(text) ?: continue
            val g = m.groupValues
            val date =
                when (i) {
                    0 -> build(year(g[3]), g[2].toInt(), g[1].toInt())
                    1 -> monthOf(g[2])?.let { build(year(g[3]), it, g[1].toInt()) }
                    else -> build(g[1].toInt(), g[2].toInt(), g[3].toInt())
                }
            if (date != null) return date to text.substring(m.range.last + 1)
        }
        return null
    }

    private fun year(text: String): Int = text.toInt().let { if (text.length == 2) 2000 + it else it }

    private fun build(
        y: Int,
        m: Int,
        d: Int,
    ): LocalDate? =
        try {
            LocalDate.of(y, m, d)
        } catch (_: DateTimeException) {
            null
        }

    private fun monthOf(name: String): Int? {
        val key = name.lowercase().take(3)
        return Month.entries.firstOrNull { it.name.lowercase().startsWith(key) }?.value
    }

    /** Rows that words could not classify: debit if the balance went down since the neighbouring row. */
    private fun fillByBalance(lines: List<StatementLine>): List<StatementLine> {
        if (lines.none { it.debit == null }) return lines
        val oldestFirst = lines.size < 2 || lines.first().date <= lines.last().date
        return lines.mapIndexed { i, line ->
            if (line.debit != null || line.balance == null) return@mapIndexed line
            val neighbour = lines.getOrNull(if (oldestFirst) i - 1 else i + 1)?.balance ?: return@mapIndexed line
            line.copy(
                debit =
                    if (line.balance < neighbour) {
                        true
                    } else if (line.balance > neighbour) {
                        false
                    } else {
                        null
                    },
            )
        }
    }
}
