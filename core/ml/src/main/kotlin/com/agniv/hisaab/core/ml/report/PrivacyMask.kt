package com.agniv.hisaab.core.ml.report

/**
 * Hides personal numbers in text before any of it goes to a cloud AI (IMPLEMENTATION_PLAN 4.6, CLAUDE.md rule 6).
 * Used for the "fill a loan with AI" text (4.8): letters and statements are full of account, phone and ID numbers.
 * Amounts and rates are kept, because the AI needs them. When unsure it hides more, not less.
 */
object PrivacyMask {
    private const val HIDDEN = "[hidden]"

    private val rules =
        listOf(
            // Email addresses and UPI ids (name@bank).
            Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)*"""),
            // PAN: 5 letters, 4 digits, 1 letter.
            Regex("""\b[A-Z]{5}\d{4}[A-Z]\b"""),
            // GSTIN: 15 characters starting with a 2 digit state code.
            Regex("""\b\d{2}[A-Z]{5}\d{4}[A-Z]\d[A-Z\d][A-Z\d]\b"""),
            // Aadhaar: 12 digits, with or without spaces/dashes.
            Regex("""\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b"""),
            // Indian mobile numbers, with or without +91 / 0.
            Regex("""(?<![\d.])(?:\+?91[\s-]?|0)?[6-9]\d{4}[\s-]?\d{5}(?!\d)"""),
            // Card numbers: 13 to 19 digits, grouped or not.
            Regex("""\b(?:\d[\s-]?){13,19}\b"""),
            // Account, loan and reference numbers: any run of 9 or more digits.
            Regex("""\b\d{9,}\b"""),
            // Letters-and-digits account ids such as HDFC0001234 (IFSC) or LN12345678.
            Regex("""\b[A-Z]{4}0[A-Z\d]{6}\b"""),
            Regex("""\b[A-Z]{1,4}\d{7,}\b"""),
        )

    fun mask(text: String): String = rules.fold(text) { acc, rule -> rule.replace(acc, HIDDEN) }
}
