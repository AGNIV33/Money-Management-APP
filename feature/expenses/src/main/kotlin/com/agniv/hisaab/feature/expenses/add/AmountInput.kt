package com.agniv.hisaab.feature.expenses.add

import com.agniv.hisaab.core.designsystem.component.PadKey

/** Rules for the amount typed on the keypad. Kept as text so "12." can show while typing. */
internal object AmountInput {
    // ₹99,99,99,999: more than any household expense, far below Long overflow.
    private const val MAX_RUPEE_DIGITS = 9
    private const val MAX_PAISE_DIGITS = 2

    fun apply(
        text: String,
        key: PadKey,
    ): String =
        when (key) {
            PadKey.Backspace -> text.dropLast(1)
            PadKey.Dot -> if ('.' in text) text else text.ifEmpty { "0" } + "."
            is PadKey.Digit -> appendDigit(text, key.value)
        }

    private fun appendDigit(
        text: String,
        digit: Int,
    ): String {
        val dot = text.indexOf('.')
        val next =
            when {
                text == "0" -> digit.toString()
                dot >= 0 && text.length - dot - 1 >= MAX_PAISE_DIGITS -> text
                dot < 0 && text.length >= MAX_RUPEE_DIGITS -> text
                else -> text + digit
            }
        return next
    }
}
