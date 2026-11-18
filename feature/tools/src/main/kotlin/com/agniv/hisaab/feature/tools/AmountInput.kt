package com.agniv.hisaab.feature.tools

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Text rules for the ₹ fields. Pure so they can be unit tested. */
internal object AmountInput {
    private const val MAX_INPUT_DIGITS = 12
    private const val MAX_INPUT_DECIMALS = 2
    private const val TAIL_GROUP = 3
    private const val HEAD_GROUP = 2

    /**
     * What an amount field keeps from typing or pasting: digits and one dot, up to 2 decimals.
     * Commas are dropped because the field draws its own (see [groupIndian]).
     */
    fun cleanAmount(input: String): String {
        val kept = input.filter { it.isDigit() || it == '.' }
        val dot = kept.indexOf('.')
        val whole = (if (dot < 0) kept else kept.substring(0, dot)).take(MAX_INPUT_DIGITS)
        if (dot < 0) return whole
        val frac = kept.substring(dot + 1).replace(".", "").take(MAX_INPUT_DECIMALS)
        return "$whole.$frac"
    }

    /** "1200000" → "12,00,000", the Indian way: last three digits, then pairs. Decimals are kept as typed. */
    fun groupIndian(raw: String): String {
        val dot = raw.indexOf('.')
        val whole = if (dot < 0) raw else raw.substring(0, dot)
        val rest = if (dot < 0) "" else raw.substring(dot)
        if (whole.length <= TAIL_GROUP) return raw
        val head = whole.dropLast(TAIL_GROUP)
        val tail = whole.takeLast(TAIL_GROUP)
        val headGroups =
            head
                .reversed()
                .chunked(HEAD_GROUP)
                .joinToString(",")
                .reversed()
        return "$headGroups,$tail$rest"
    }

    /** Slider value to rate text with two decimals: 8.5 → "8.50". */
    fun rateText(value: Float): String = String.format(Locale.US, "%.2f", value)

    /** Short Indian form for tight spots: ₹59.88 L, ₹1.20 Cr; below a lakh the full amount. */
    fun compact(money: Money): String {
        val rupees = BigDecimal(money.paise).divide(BigDecimal(PAISE_PER_RUPEE))
        val abs = rupees.abs()
        return when {
            abs >= CRORE -> "₹" + rupees.divide(CRORE, 2, RoundingMode.HALF_UP).toPlainString() + " Cr"
            abs >= LAKH -> "₹" + rupees.divide(LAKH, 2, RoundingMode.HALF_UP).toPlainString() + " L"
            else -> money.formatted()
        }
    }

    /** Commas inside a calculator expression: "12000×18%" → "12,000×18%". */
    fun groupNumbers(expression: String): String = NUMBER.replace(expression) { groupIndian(it.value) }

    private val NUMBER = Regex("\\d+(\\.\\d*)?")
    private const val PAISE_PER_RUPEE = 100
    private const val LAKH_RUPEES = 100_000L
    private const val CRORE_RUPEES = 10_000_000L
    private val LAKH = BigDecimal(LAKH_RUPEES)
    private val CRORE = BigDecimal(CRORE_RUPEES)
}
