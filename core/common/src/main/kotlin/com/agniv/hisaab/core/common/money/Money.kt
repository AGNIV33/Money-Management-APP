package com.agniv.hisaab.core.common.money

/**
 * An amount of Indian money, stored as whole paise (1 rupee = 100 paise).
 *
 * Money is never held as Float/Double: binary floats can't represent 0.10 exactly,
 * and family totals must add up to the last paisa.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {
    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0L

    /** Whole-rupee part, truncated toward zero. */
    val rupeesPart: Long get() = paise / PAISE_PER_RUPEE

    /** Leftover paise (0..99), always non-negative. */
    val paisePart: Long get() = kotlin.math.abs(paise % PAISE_PER_RUPEE)

    operator fun plus(other: Money): Money = Money(Math.addExact(paise, other.paise))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(paise, other.paise))

    operator fun times(factor: Int): Money = Money(Math.multiplyExact(paise, factor.toLong()))

    operator fun times(factor: Long): Money = Money(Math.multiplyExact(paise, factor))

    operator fun unaryMinus(): Money = Money(Math.negateExact(paise))

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    fun abs(): Money = if (isNegative) -this else this

    companion object {
        const val PAISE_PER_RUPEE = 100L
        private const val MAX_PAISE_DIGITS = 2

        val ZERO = Money(0L)

        fun ofRupees(rupees: Long): Money = Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE))

        fun ofRupees(rupees: Long, paise: Int): Money {
            require(paise in 0 until PAISE_PER_RUPEE) { "paise must be 0..99, was $paise" }
            val sign = if (rupees < 0) -1L else 1L
            return Money(Math.addExact(Math.multiplyExact(rupees, PAISE_PER_RUPEE), sign * paise))
        }

        /**
         * Parses user input like "250", "1,25,000.5", "₹ 99.99" or "-40".
         * Returns null for anything that isn't a clean amount (e.g. three decimal places),
         * so the UI can show an error instead of silently rounding.
         */
        @Suppress("ReturnCount")
        fun parse(input: String): Money? {
            val cleaned =
                input
                    .trim()
                    .removePrefix("₹")
                    .replace(",", "")
                    .replace(" ", "")
            if (cleaned.isEmpty()) return null
            val negative = cleaned.startsWith("-")
            val body = cleaned.removePrefix("-")
            val parts = body.split('.')
            if (parts.size > 2) return null
            val rupeeText = parts[0].ifEmpty { "0" }
            val paiseText = parts.getOrNull(1) ?: ""
            if (!rupeeText.all(Char::isDigit) || !paiseText.all(Char::isDigit)) return null
            if (paiseText.length > MAX_PAISE_DIGITS) return null
            if (parts.size == 2 && parts[0].isEmpty() && paiseText.isEmpty()) return null
            val rupees = rupeeText.toLongOrNull() ?: return null
            val paise = paiseText.padEnd(MAX_PAISE_DIGITS, '0').toLong()
            val total =
                runCatching {
                    Math.addExact(Math.multiplyExact(rupees, PAISE_PER_RUPEE), paise)
                }.getOrNull() ?: return null
            return Money(if (negative) -total else total)
        }
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
