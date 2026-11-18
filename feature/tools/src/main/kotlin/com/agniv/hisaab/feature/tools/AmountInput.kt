package com.agniv.hisaab.feature.tools

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
}
