package com.agniv.hisaab.core.ml.category

import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.ml.voice.VoiceParser
import java.time.LocalDate

/**
 * Guesses a category from the note being typed (PRD F16). Two sources, strongest first:
 * 1. Your own habits: words you have used before, and the category you filed them under. If "bhaiya" went to
 *    Vegetables 4 times out of 5, it will be Vegetables again. Learns from your own entries, so a correction
 *    today shapes tomorrow's guess; it never leaves the phone.
 * 2. The built-in word list (doodh, petrol, swiggy ...) shared with voice and bill scan.
 * Only a suggestion: the screen shows it and the user can change it.
 */
object CategorySuggester {
    private const val MIN_WORD_LETTERS = 3
    private const val MIN_USES = 2
    private const val MIN_SHARE_PERCENT = 70
    private const val PERCENT = 100

    private val splitter = Regex("""[^\p{L}\p{M}]+""")

    /** [categoryIdByName] maps the English default names (Milk, Fuel ...) to this family's category ids. */
    fun suggest(
        note: String,
        history: List<Expense>,
        categoryIdByName: Map<String, String>,
        today: LocalDate,
    ): String? {
        val words = tokens(note)
        if (words.isEmpty()) return null
        return personal(words, history) ?: VoiceParser.parse(note, today).categoryName?.let(categoryIdByName::get)
    }

    private fun tokens(text: String): List<String> =
        text
            .lowercase()
            .split(splitter)
            .filter { it.length >= MIN_WORD_LETTERS }
            .distinct()

    private fun personal(
        words: List<String>,
        history: List<Expense>,
    ): String? {
        // word -> category -> times
        val seen = mutableMapOf<String, MutableMap<String, Int>>()
        for (e in history) {
            if (e.deleted || e.note.isNullOrBlank()) continue
            for (w in tokens(e.note.orEmpty()).filter { it in words }) {
                seen.getOrPut(w) { mutableMapOf() }.merge(e.categoryId, 1, Int::plus)
            }
        }
        val votes = mutableMapOf<String, Int>()
        for ((_, byCategory) in seen) {
            val total = byCategory.values.sum()
            val (top, count) = byCategory.maxByOrNull { it.value } ?: continue
            if (total >= MIN_USES && count * PERCENT / total >= MIN_SHARE_PERCENT) votes.merge(top, total, Int::plus)
        }
        return votes.maxByOrNull { it.value }?.key
    }
}
