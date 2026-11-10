package com.agniv.hisaab.feature.tools

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The basic calculator (PRD F9a). Decimal maths, never Double, so 0.1 + 0.2 shows 0.3.
 * Expression text uses + − × ÷ and %; × and ÷ go before + and −.
 * "500 + 18%" means 500 plus 18% of 500 (like shop calculators); "18%" alone is 0.18.
 */
internal object CalcEngine {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    const val PERCENT = '%'
    private const val MAX_DIGITS = 15
    private const val SHOWN_DECIMALS = 8
    private const val PERCENT_BASE = 100
    private const val PRECISION = 32
    private val HUNDRED = BigDecimal(PERCENT_BASE)
    private val MATH = MathContext(PRECISION, RoundingMode.HALF_UP)
    private val OPS = setOf(PLUS, MINUS, TIMES, DIVIDE)

    /** Next text after a key press. Keeps the expression always readable (no "++", no "1.2.3"). */
    fun press(
        text: String,
        key: Char,
    ): String =
        when {
            key.isDigit() -> digit(text, key)
            key == '.' -> dot(text)
            key == PERCENT -> if (text.lastOrNull()?.let { it.isDigit() || it == '.' } == true) text + key else text
            key in OPS -> operator(text, key)
            else -> text
        }

    private fun currentNumber(text: String) = text.takeLastWhile { it.isDigit() || it == '.' }

    private fun digit(
        text: String,
        key: Char,
    ) = if (currentNumber(text).count(Char::isDigit) >= MAX_DIGITS || text.lastOrNull() == PERCENT) text else text + key

    private fun dot(text: String): String {
        val number = currentNumber(text)
        return when {
            '.' in number || text.lastOrNull() == PERCENT -> text
            number.isEmpty() -> text + "0."
            else -> "$text."
        }
    }

    private fun operator(
        text: String,
        key: Char,
    ) = when {
        text.isEmpty() -> if (key == MINUS) text + key else text
        text.last() in OPS -> text.dropLast(1) + key
        else -> text + key
    }

    /** Value of the expression, or null while it is incomplete or divides by zero. */
    fun evaluate(text: String): BigDecimal? =
        runCatching { Parser(text.trimEnd { it in OPS }).parse() }.getOrNull()

    fun format(value: BigDecimal): String {
        val rounded = value.setScale(SHOWN_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.signum() == 0) "0" else rounded.toPlainString()
    }

    private class Parser(
        private val s: String,
    ) {
        private var i = 0

        fun parse(): BigDecimal {
            require(s.isNotEmpty())
            var total = term()
            while (i < s.length) {
                val op = s[i++]
                val start = i
                var right = term()
                // "a + b%" → b% of a. right is already b/100.
                val termText = s.substring(start, i)
                if (termText.endsWith(PERCENT) && termText.none { it == TIMES || it == DIVIDE }) {
                    right = total.multiply(right, MATH)
                }
                total = if (op == PLUS) total.add(right, MATH) else total.subtract(right, MATH)
            }
            return total
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (i < s.length && (s[i] == TIMES || s[i] == DIVIDE)) {
                val op = s[i++]
                val right = factor()
                value = if (op == TIMES) value.multiply(right, MATH) else value.divide(right.also { require(it.signum() != 0) }, MATH)
            }
            return value
        }

        private fun factor(): BigDecimal {
            val negative = i < s.length && s[i] == MINUS
            if (negative) i++
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            var value = BigDecimal(s.substring(start, i))
            if (i < s.length && s[i] == PERCENT) {
                value = value.divide(HUNDRED, MATH)
                i++
            }
            return if (negative) value.negate() else value
        }
    }
}
