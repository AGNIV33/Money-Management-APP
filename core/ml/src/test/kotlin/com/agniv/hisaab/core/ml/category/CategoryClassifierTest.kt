package com.agniv.hisaab.core.ml.category

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CategoryClassifierTest {
    // Unit tests run from the module folder, so the real shipped model file is read here.
    private val model = File("src/main/assets/category_model.txt").inputStream().use(CategoryClassifier::load)

    private fun guess(note: String) = model.classify(note)?.categoryName

    @Test
    fun `knows shop and app names the word list does not`() {
        assertThat(guess("dominos")).isEqualTo("Food Outside")
        assertThat(guess("jio recharge")).isEqualTo("Mobile/Internet")
        assertThat(guess("amul")).isEqualTo("Milk")
        assertThat(guess("netflix")).isEqualTo("Entertainment")
        assertThat(guess("lenskart")).isEqualTo("Shopping")
        assertThat(guess("bescom")).isEqualTo("Electricity")
        assertThat(guess("urban company")).isEqualTo("Maid/Help")
        assertThat(guess("car service")).isEqualTo("Transport")
    }

    @Test
    fun `copes with small spelling slips`() {
        assertThat(guess("swigy")).isEqualTo("Food Outside")
        assertThat(guess("pizzza")).isEqualTo("Food Outside")
        assertThat(guess("dawaii")).isEqualTo("Medical")
        assertThat(guess("kiraaya")).isEqualTo("Rent")
    }

    @Test
    fun `reads Hindi, with or without the nukta dot`() {
        assertThat(guess("दूध")).isEqualTo("Milk")
        assertThat(guess("सब्ज़ी")).isEqualTo("Vegetables")
        assertThat(guess("सब्जी")).isEqualTo("Vegetables")
    }

    @Test
    fun `stays quiet when unsure`() {
        assertThat(guess("ramesh")).isNull() // a name says nothing about the category
        assertThat(guess("something odd")).isNull()
        assertThat(guess("qwerty")).isNull()
        assertThat(guess("xyzabc")).isNull()
        assertThat(guess("office")).isNull()
        assertThat(guess("")).isNull()
        assertThat(guess("500")).isNull()
    }

    @Test
    fun `confidence is a real chance between 0 and 1`() {
        val g = requireNotNull(model.classify("swiggy dinner"))
        assertThat(g.confidence).isAtLeast(0.8)
        assertThat(g.confidence).isAtMost(1.0)
    }

    @Test
    fun `fast enough for every key press`() {
        val notes = listOf("swiggy dinner", "doodh 2 packet", "jio recharge", "bijli ka bill", "something odd")
        repeat(200) { notes.forEach(model::classify) } // warm up
        val start = System.nanoTime()
        repeat(1000) { notes.forEach(model::classify) }
        val perNoteMs = (System.nanoTime() - start) / 5000.0 / 1_000_000
        // Plan 4.2 budget is 30 ms on a low-end phone; a PC is ~10x faster, so 1 ms here is a safe check.
        assertThat(perNoteMs).isLessThan(1.0)
    }

    @Test
    fun `the suggester asks the model last`() {
        val ids = mapOf("Food Outside" to "c-food", "Groceries" to "c-groc", "Milk" to "c-milk")
        val today = LocalDate.of(2026, 11, 16)
        // Only the model knows "dominos".
        assertThat(CategorySuggester.suggest("dominos", emptyList(), ids, today, model)).isEqualTo("c-food")
        // The word list knows "doodh"; the model is not needed.
        assertThat(CategorySuggester.suggest("doodh", emptyList(), ids, today, model)).isEqualTo("c-milk")
        // A model category this family's list does not map gives no guess, not a crash.
        assertThat(CategorySuggester.suggest("netflix", emptyList(), ids, today, model)).isNull()
        // No model loaded yet: still works as before.
        assertThat(CategorySuggester.suggest("dominos", emptyList(), ids, today, null)).isNull()
    }
}
