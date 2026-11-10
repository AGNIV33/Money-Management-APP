package com.agniv.hisaab.core.domain.loan

import com.agniv.hisaab.core.common.money.Money

/** Text-field rules for the loan form. Pure so they can be unit tested. */
object LoanForm {
    private const val MAX_RATE_DECIMALS = 2
    private const val BPS_PER_PERCENT = 100
    private const val MONTHS_PER_YEAR = 12
    private const val MAX_TENURE_DIGITS = 3

    /** "8.5" → 850, "10" → 1000, "8.75%" → 875. Null when not a clean 0–42% with up to 2 decimals. */
    fun parseRateBps(text: String): Int? {
        val cleaned = text.trim().removeSuffix("%").trim()
        val parts = cleaned.split('.')
        val whole = parts[0]
        val frac = parts.getOrNull(1).orEmpty()
        val valid =
            cleaned.isNotEmpty() &&
                parts.size <= 2 &&
                whole.isNotEmpty() &&
                whole.all(Char::isDigit) &&
                frac.all(Char::isDigit) &&
                frac.length <= MAX_RATE_DECIMALS &&
                whole.length <= 2
        if (!valid) return null
        val bps = whole.toInt() * BPS_PER_PERCENT + frac.padEnd(MAX_RATE_DECIMALS, '0').toInt()
        return bps.takeIf { it <= LoanTerms.MAX_RATE_BPS }
    }

    fun parseTenureMonths(
        text: String,
        inYears: Boolean,
    ): Int? {
        val n = text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TENURE_DIGITS && it.all(Char::isDigit) }?.toInt() ?: return null
        val months = if (inYears) n * MONTHS_PER_YEAR else n
        return months.takeIf { it in 1..LoanTerms.MAX_TENURE_MONTHS }
    }

    /** Positive whole-paise amount, or null. */
    fun parseAmount(text: String): Money? = Money.parse(text)?.takeIf { it.paise > 0 }

    /** EMI the formula gives for what is typed so far, or null while the fields are incomplete. */
    fun formulaEmi(
        principalText: String,
        rateText: String,
        tenureText: String,
        inYears: Boolean,
    ): Money? {
        val principal = parseAmount(principalText)?.takeIf { it.paise <= LoanTerms.MAX_PRINCIPAL_PAISE }
        val bps = parseRateBps(rateText)
        val months = parseTenureMonths(tenureText, inYears)
        val terms = if (principal != null && bps != null && months != null) LoanTerms(principal, bps, months) else null
        return terms?.takeIf(EmiMath::isRepayable)?.let(EmiMath::emi)
    }

    /** Rate for showing in a text field: 850 → "8.5", 1000 → "10". */
    fun rateText(bps: Int): String {
        val whole = bps / BPS_PER_PERCENT
        val frac = (bps % BPS_PER_PERCENT).toString().padStart(MAX_RATE_DECIMALS, '0').trimEnd('0')
        return if (frac.isEmpty()) "$whole" else "$whole.$frac"
    }

    /** Amount for a text field without symbol or commas: 50000000 paise → "500000". */
    fun amountText(money: Money): String =
        if (money.paisePart == 0L) "${money.rupeesPart}" else "${money.rupeesPart}.${money.paisePart.toString().padStart(2, '0')}"
}
