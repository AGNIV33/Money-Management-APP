package com.agniv.hisaab

/**
 * Removes anything personal from crash text before it leaves the phone (plan 5.5, CLAUDE.md §4.6).
 * Error messages can quote what caused them, e.g. a note, an email or an amount in a database error,
 * so every message is cleaned; class names and line numbers are kept because they are what fixes a bug.
 */
object CrashScrubber {
    private val email = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
    private val uuid = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val url = Regex("""https?://\S+""")

    // Phone numbers, account / card numbers, amounts, OTPs: any run of 4+ digits, with spaces or dashes inside.
    private val longNumber = Regex("""\d[\d ,.\-]{2,}\d""")

    // Text inside quotes is usually a value the user typed (note, name, search).
    private val quoted = Regex("""(["'])(?:(?!\1).)*\1""")
    private val rupee = Regex("""₹\s*[\d,.]+""")

    fun scrub(text: String?): String? {
        if (text == null) return null
        return text
            .replace(url, "[url]")
            .replace(email, "[email]")
            .replace(uuid, "[id]")
            .replace(quoted, "[text]")
            .replace(rupee, "[amount]")
            .replace(longNumber) { if (it.value.count(Char::isDigit) >= MIN_DIGITS) "[number]" else it.value }
            .take(MAX_LENGTH)
    }

    private const val MIN_DIGITS = 4
    private const val MAX_LENGTH = 500
}
