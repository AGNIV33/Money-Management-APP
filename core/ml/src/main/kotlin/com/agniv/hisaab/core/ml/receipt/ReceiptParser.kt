package com.agniv.hisaab.core.ml.receipt

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.ml.voice.Confidence
import com.agniv.hisaab.core.ml.voice.VoiceParser
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month

/** What was read from a bill. Nothing is saved from it until the user confirms (CLAUDE.md rule 4). */
data class ReceiptScan(
    val amount: Money?,
    val date: LocalDate?,
    val merchant: String?,
    val categoryName: String?,
    val confidence: Confidence,
)

/**
 * Reads the text ML Kit found on a bill or receipt (PRD F14): the amount to pay, the bill date, the shop name.
 * Rules, not AI: look for TOTAL-type lines first, then fall back to the biggest money-looking number.
 * Works on the phone, offline. Money is decimal, never Double.
 */
@Suppress("TooManyFunctions", "ReturnCount", "MagicNumber") // small rule tables and date shapes; splitting would only scatter them
object ReceiptParser {
    private const val MAX_PAISE = 99_999_999_900L
    private const val MERCHANT_SEARCH_LINES = 6
    private const val MIN_MERCHANT_LETTERS = 3
    private const val MAX_MERCHANT_LENGTH = 40
    private const val MAX_YEAR_AGE_DAYS = 800L
    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val SCALE = 2
    private const val STRONG = 2
    private const val PLAIN_TOTAL = 1

    private val number = Regex("""(?<![\w.])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?![\w%])""")
    private val moneyMarker = Regex("""(₹|\brs\b\.?|\binr\b)\s*\d""", RegexOption.IGNORE_CASE)

    private val strongTotals =
        listOf(
            "grand total",
            "net payable",
            "amount payable",
            "total payable",
            "net amount",
            "bill amount",
            "amount due",
            "balance due",
            "to pay",
            "total amount",
            "net total",
            "total due",
            "amt payable",
        )
    private val notTotals =
        listOf(
            "sub total",
            "subtotal",
            "sub-total",
            "total qty",
            "total item",
            "total items",
            "total gst",
            "total tax",
            "total discount",
            "total savings",
            "total saving",
            "total cgst",
            "total sgst",
            "total igst",
            "total mrp",
            "total quantity",
            "total pcs",
        )
    private val merchantSkip =
        Regex(
            """invoice|bill|receipt|\btax\b|gst|\btin\b|\bcin\b|phone|\btel\b|mob|date|welcome|thank|""" +
                """cash memo|original|duplicate|\bwww\b|\.com""",
            RegexOption.IGNORE_CASE,
        )

    fun parse(
        text: String,
        today: LocalDate,
    ): ReceiptScan {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val (amount, fromTotalLine) = findAmount(lines)
        val merchant = findMerchant(lines)
        val category = merchant?.let { VoiceParser.parse(it, today).categoryName } ?: VoiceParser.parse(text, today).categoryName
        val confidence =
            when {
                amount == null -> Confidence.LOW
                fromTotalLine -> Confidence.HIGH
                else -> Confidence.MEDIUM
            }
        return ReceiptScan(amount, findDate(text, today), merchant, category, confidence)
    }

    // ---- amount ------------------------------------------------------------------------------------------------

    private fun numbersIn(line: String): List<BigDecimal> =
        number
            .findAll(line)
            .mapNotNull {
                it.value.replace(",", "").toBigDecimalOrNull()
            }.toList()

    private fun priority(line: String): Int {
        val l = line.lowercase()
        return when {
            notTotals.any { it in l } -> 0
            strongTotals.any { it in l } -> STRONG
            Regex("""\btotal\b""").containsMatchIn(l) -> PLAIN_TOTAL
            else -> 0
        }
    }

    /** Pair of (amount, found on a TOTAL-type line). */
    private fun findAmount(lines: List<String>): Pair<Money?, Boolean> {
        for (level in STRONG downTo PLAIN_TOTAL) {
            // The last such line wins: grand totals sit at the bottom of a bill.
            val index = lines.indices.lastOrNull { priority(lines[it]) == level && amountOn(lines, it) != null }
            index?.let { amountOn(lines, it)?.toMoney() }?.let { return it to true }
        }
        val marked = lines.filter { moneyMarker.containsMatchIn(it) }.flatMap(::numbersIn)
        marked.maxOrNull()?.toMoney()?.let { return it to false }
        val decimals =
            lines.flatMap { l ->
                number
                    .findAll(l)
                    .map { it.value }
                    .filter { '.' in it }
                    .toList()
            }
        val biggest = decimals.mapNotNull { it.replace(",", "").toBigDecimalOrNull() }.maxOrNull()
        return biggest?.toMoney() to false
    }

    /** The amount on a TOTAL line: the last number on it, or the first on the next line ("TOTAL" then "441.00"). */
    private fun amountOn(
        lines: List<String>,
        index: Int,
    ): BigDecimal? =
        numbersIn(lines[index]).lastOrNull { it.signum() > 0 }
            ?: lines.getOrNull(index + 1)?.let(::numbersIn)?.firstOrNull { it.signum() > 0 }

    private fun BigDecimal.toMoney(): Money? {
        val paise = setScale(SCALE, RoundingMode.HALF_UP).movePointRight(SCALE).toBigInteger()
        return if (paise.signum() > 0 && paise <= java.math.BigInteger.valueOf(MAX_PAISE)) Money(paise.toLong()) else null
    }

    // ---- shop name ---------------------------------------------------------------------------------------------

    private fun findMerchant(lines: List<String>): String? =
        lines
            .take(MERCHANT_SEARCH_LINES)
            .firstOrNull { l ->
                l.count(Char::isLetter) >= MIN_MERCHANT_LETTERS &&
                    l.length <= MAX_MERCHANT_LENGTH &&
                    !merchantSkip.containsMatchIn(l) &&
                    l.count(Char::isDigit) <= l.count(Char::isLetter)
            }?.trim(' ', '*', '-', '=', '#', '|')
            ?.takeIf { it.isNotEmpty() }

    // ---- date --------------------------------------------------------------------------------------------------

    private val numericDate = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val isoDate = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")
    private val namedDate = Regex("""\b(\d{1,2})[\s\-]+([A-Za-z]{3,9})[\s\-,.]+(\d{4}|\d{2})\b""")

    private fun findDate(
        text: String,
        today: LocalDate,
    ): LocalDate? {
        val found = mutableListOf<Pair<Int, LocalDate>>()
        isoDate.findAll(text).forEach { m ->
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { found += m.range.first to it }
        }
        numericDate.findAll(text).forEach { m ->
            // Indian bills write day first: 08/11/2026 is 8 November.
            date(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { found += m.range.first to it }
        }
        namedDate.findAll(text).forEach { m ->
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            date(year(m.groupValues[3]), month, m.groupValues[1].toInt())?.let { found += m.range.first to it }
        }
        // A bill cannot be from the future, and a very old date is more likely a printed batch or licence date.
        return found
            .sortedBy { it.first }
            .map { it.second }
            .firstOrNull { it <= today && it >= today.minusDays(MAX_YEAR_AGE_DAYS) }
    }

    private fun year(text: String): Int = text.toInt().let { if (text.length == 2) TWO_DIGIT_YEAR_BASE + it else it }

    private fun date(
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
}
