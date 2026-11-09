package com.agniv.hisaab.core.common.money

/**
 * Formats [Money] the Indian way: ₹1,25,000.50 (last 3 digits, then groups of 2).
 *
 * Done by hand instead of NumberFormat(Locale("en", "IN")) because the JVM and older
 * Android versions disagree on lakh grouping, and amounts must look identical everywhere.
 */
object MoneyFormatter {
    private const val RUPEE = "₹"
    private const val FIRST_GROUP = 3
    private const val OTHER_GROUP = 2

    enum class Paise {
        /** Show ".50" only when the amount has paise. Default for lists and totals. */
        WHEN_NEEDED,

        /** Always show two decimals, e.g. ₹250.00. */
        ALWAYS,

        /** Drop paise (truncates), e.g. for chart labels. */
        NEVER,
    }

    fun format(money: Money, paise: Paise = Paise.WHEN_NEEDED, withSymbol: Boolean = true): String {
        val sign = if (money.isNegative) "-" else ""
        val rupees = groupIndian(kotlin.math.abs(money.rupeesPart).toString())
        val fraction =
            when {
                paise == Paise.NEVER -> ""
                paise == Paise.ALWAYS || money.paisePart != 0L -> "." + money.paisePart.toString().padStart(2, '0')
                else -> ""
            }
        val symbol = if (withSymbol) RUPEE else ""
        return "$sign$symbol$rupees$fraction"
    }

    internal fun groupIndian(digits: String): String {
        if (digits.length <= FIRST_GROUP) return digits
        val head = digits.dropLast(FIRST_GROUP)
        val tail = digits.takeLast(FIRST_GROUP)
        val headGroups =
            head
                .reversed()
                .chunked(OTHER_GROUP)
                .joinToString(",")
                .reversed()
        return "$headGroups,$tail"
    }
}

fun Money.formatted(paise: MoneyFormatter.Paise = MoneyFormatter.Paise.WHEN_NEEDED): String =
    MoneyFormatter.format(this, paise)
