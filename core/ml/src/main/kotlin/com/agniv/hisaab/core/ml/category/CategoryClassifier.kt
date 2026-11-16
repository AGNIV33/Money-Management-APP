package com.agniv.hisaab.core.ml.category

import android.content.Context
import java.io.InputStream
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln

/**
 * The small on-device category model (plan 4.2, PRD F16). Naive Bayes over words and 3-letter word pieces,
 * trained by ml/training/train.py on ml/training/dataset.csv. It covers notes the word list does not know
 * ("dominos", "jio", "lenskart") and misspellings ("swigy", "pizzza"). 67 KB, no native library, a guess
 * takes well under a millisecond.
 *
 * It only guesses when it is sure: most of the note's pieces must be known to it, and one category must
 * clearly win. Otherwise it returns null and the screen shows no guess. Text clean-up and features must
 * stay identical to train.py.
 */
class CategoryClassifier private constructor(
    private val labels: List<String>,
    private val base: DoubleArray,
    /** Per class: log of the chance of a piece this class never saw. */
    private val unseen: DoubleArray,
    /** Piece -> (class index, extra log chance over [unseen]) pairs. */
    private val pieces: Map<String, Pair<IntArray, DoubleArray>>,
    private val ngram: Int,
    private val minWord: Int,
) {
    data class Guess(
        /** English default category name, as in DefaultCategories (Milk, Fuel ...). */
        val categoryName: String,
        val confidence: Double,
    )

    fun classify(note: String): Guess? {
        val feats = features(note)
        val known = feats.filter { it in pieces }
        // Too few known pieces means a word it never learned: any winner would be noise.
        val enough = feats.isNotEmpty() && known.size * PERCENT >= feats.size * MIN_KNOWN_PERCENT
        return if (enough) best(known)?.takeIf { it.confidence >= MIN_CONFIDENCE } else null
    }

    private fun best(known: List<String>): Guess? {
        val score = DoubleArray(labels.size) { base[it] + known.size * unseen[it] }
        for (f in known) {
            val (classes, extra) = pieces.getValue(f)
            for (i in classes.indices) score[classes[i]] += extra[i]
        }
        val top = score.max()
        val winner = score.indices.maxBy { score[it] }
        // Softmax of the winner: exp(top - top) / sum.
        val confidence = 1.0 / score.sumOf { exp(it - top) }
        return labels[winner].takeIf { it != NONE }?.let { Guess(it, confidence) }
    }

    private fun features(text: String): List<String> =
        buildList {
            for (w in words(text)) {
                add("w:$w")
                val padded = "<$w>"
                for (i in 0..padded.length - ngram) add("c:" + padded.substring(i, i + ngram))
            }
        }

    private fun words(text: String): List<String> = norm(text).split(splitter).filter { it.length >= minWord }

    companion object {
        private const val NONE = "none"
        private const val MIN_CONFIDENCE = 0.8
        private const val MIN_KNOWN_PERCENT = 65
        private const val PERCENT = 100
        private const val ASSET = "category_model.txt"
        private const val NUKTA = '़'
        private const val CHANDRABINDU = 'ँ'
        private const val ANUSVARA = 'ं'
        private val splitter = Regex("""[^\p{L}\p{M}]+""")

        @Volatile private var cached: CategoryClassifier? = null

        /** Loads once per app run. Call off the main thread (reads a 67 KB file). Null if the file is missing. */
        fun fromAssets(context: Context): CategoryClassifier? =
            cached ?: runCatching { context.assets.open(ASSET).use(::load) }
                .getOrNull()
                ?.also { cached = it }

        internal fun norm(text: String): String {
            val d = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            val cleaned = d.filter { it != NUKTA }.replace(CHANDRABINDU, ANUSVARA)
            return Normalizer.normalize(cleaned, Normalizer.Form.NFC)
        }

        fun load(input: InputStream): CategoryClassifier {
            val lines = input.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            val header = lines[0].split('\t').chunked(2).associate { it[0] to it[1] }
            val alpha = header.getValue("alpha").toDouble()
            val labels = lines[1].split('\t').drop(1)
            val docs = lines[2].split('\t').drop(1).map(String::toInt)
            val raw =
                lines.drop(HEADER_LINES).associate { line ->
                    val (feat, pairs) = line.split('\t')
                    val parsed = pairs.split(' ').map { p -> p.substringBefore(':').toInt() to p.substringAfter(':').toInt() }
                    feat to parsed
                }
            val totals = IntArray(labels.size)
            raw.values.forEach { it.forEach { (c, k) -> totals[c] += k } }
            val denom = DoubleArray(labels.size) { totals[it] + alpha * raw.size }
            val docTotal = docs.sum().toDouble()
            val unseen = DoubleArray(labels.size) { ln(alpha / denom[it]) }
            val pieces =
                raw.mapValues { (_, pairs) ->
                    pairs.map { it.first }.toIntArray() to
                        pairs.map { (c, k) -> ln((k + alpha) / denom[c]) - unseen[c] }.toDoubleArray()
                }
            return CategoryClassifier(
                labels = labels,
                base = DoubleArray(labels.size) { ln(docs[it] / docTotal) },
                unseen = unseen,
                pieces = pieces,
                ngram = header.getValue("ngram").toInt(),
                minWord = header.getValue("minword").toInt(),
            )
        }

        private const val HEADER_LINES = 3
    }
}
