package com.agniv.hisaab.core.ml.statement

import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.Loan
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.usecase.LoanItem
import com.agniv.hisaab.core.ml.voice.VoiceParser
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What Kinly found out about one payment on the statement. */
sealed interface MatchResult {
    /** An expense with the same amount, within a few days, is already in Kinly. */
    data class Added(
        val expense: Expense,
    ) : MatchResult

    /** It is an EMI of [loan] for [month] and that month is already ticked as paid. */
    data class EmiMarked(
        val loan: Loan,
        val month: YearMonth,
    ) : MatchResult

    /** It is an EMI of [loan] for [month] that is not ticked as paid yet: Kinly can tick it. */
    data class EmiUnmarked(
        val loan: Loan,
        val month: YearMonth,
    ) : MatchResult

    /** Same amount but dates or words do not agree well enough to be sure. */
    data class Unsure(
        val expense: Expense,
    ) : MatchResult

    /** Nothing in Kinly looks like it: a candidate to add, with a suggested category, mode and note. */
    data class Missing(
        val categoryName: String?,
        val mode: PayMode?,
        val note: String?,
    ) : MatchResult
}

/** How the payments on a statement split up, for the summary line. */
data class MatchSummary(
    val added: Int,
    val emiToMark: Int,
    val unsure: Int,
    val missing: Int,
)

fun List<MatchedLine>.summary() =
    MatchSummary(
        added = count { it.result is MatchResult.Added || it.result is MatchResult.EmiMarked },
        emiToMark = count { it.result is MatchResult.EmiUnmarked },
        unsure = count { it.result is MatchResult.Unsure },
        missing = count { it.result is MatchResult.Missing },
    )

data class MatchedLine(
    val line: StatementLine,
    val result: MatchResult,
)

/**
 * Compares statement payments with what is already in Kinly (PRD F21). Only money that left the account
 * is checked; each expense is used for one payment at most, so two ₹200 chai payments do not both match
 * one ₹200 expense.
 */
@Suppress("ReturnCount") // first rule that fits wins: EMI, then close expense, then loose, else missing
object StatementMatcher {
    private const val SAME_DAY_WINDOW = 3L
    private const val LOOSE_WINDOW = 10L
    private const val EMI_WINDOW = 7L
    private const val MAX_NOTE = 60

    fun match(
        lines: List<StatementLine>,
        expenses: List<Expense>,
        loans: List<LoanItem>,
    ): List<MatchedLine> {
        val used = mutableSetOf<String>()
        return lines
            .filter { it.debit != false }
            .sortedBy { it.date }
            .map { line -> MatchedLine(line, resolve(line, expenses, loans, used)) }
    }

    private fun resolve(
        line: StatementLine,
        expenses: List<Expense>,
        loans: List<LoanItem>,
        used: MutableSet<String>,
    ): MatchResult {
        emi(line, loans)?.let { return it }
        val sameAmount = expenses.filter { !it.deleted && it.id !in used && it.amount == line.amount }
        val close = sameAmount.filter { days(it.date, line.date) <= SAME_DAY_WINDOW }.minByOrNull { days(it.date, line.date) }
        if (close != null) {
            used += close.id
            return MatchResult.Added(close)
        }
        val loose =
            sameAmount.filter { days(it.date, line.date) <= LOOSE_WINDOW }.minByOrNull { days(it.date, line.date) }
        if (loose != null) {
            used += loose.id
            return MatchResult.Unsure(loose)
        }
        val parsed = VoiceParser.parse(line.description, line.date)
        return MatchResult.Missing(parsed.categoryName, guessMode(line.description) ?: parsed.mode, note(line.description))
    }

    private fun emi(
        line: StatementLine,
        loans: List<LoanItem>,
    ): MatchResult? {
        for (item in loans) {
            if (item.loan.emi != line.amount) continue
            for (offset in -1..1) {
                val month = YearMonth.from(line.date).plusMonths(offset.toLong())
                val due = month.atDay(minOf(item.loan.dueDay, month.lengthOfMonth()))
                if (days(due, line.date) > EMI_WINDOW) continue
                val paid = month in item.paidMonths || due < item.loan.addedOn
                return if (paid) MatchResult.EmiMarked(item.loan, month) else MatchResult.EmiUnmarked(item.loan, month)
            }
        }
        return null
    }

    private fun days(
        a: LocalDate,
        b: LocalDate,
    ): Long = abs(ChronoUnit.DAYS.between(a, b))

    private fun guessMode(description: String): PayMode? {
        val d = description.lowercase()
        return when {
            "upi" in d -> PayMode.UPI
            "atm" in d || "cash" in d -> PayMode.CASH
            "pos" in d || "card" in d -> PayMode.CARD
            else -> null
        }
    }

    /** Bank text like "UPI/DR/ RAMESH KIRANA STORE" becomes "Ramesh kirana store"; refs are already stripped. */
    private fun note(description: String): String? =
        description
            .replace(Regex("""(?i)\b(upi|dr|cr|neft|imps|pos|ach|nach|wdl|atm)\b"""), " ")
            .replace(Regex("""[/\-_|]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(MAX_NOTE)
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .takeIf { it.isNotEmpty() }
}
