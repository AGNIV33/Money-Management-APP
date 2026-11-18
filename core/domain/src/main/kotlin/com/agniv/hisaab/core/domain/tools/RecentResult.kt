package com.agniv.hisaab.core.domain.tools

/**
 * One finished calculation for the Tools "Recently used" list. Numbers only (no names or notes), so the row is drawn
 * in the current language. [a], [b] and [c] mean different things per [tool]; the Tools screen decides.
 */
data class RecentResult(
    val tool: String,
    val at: Long,
    val a: Long,
    val b: Long,
    val c: Long,
) {
    /** Same tool and same numbers: saving it again only moves it to the top. */
    fun sameAs(other: RecentResult) = tool == other.tool && a == other.a && b == other.b && c == other.c

    fun encode() = listOf(tool, at, a, b, c).joinToString(SEPARATOR)

    companion object {
        const val MAX_RECENT = 5
        private const val SEPARATOR = ";"
        private const val FIELDS = 5
        private const val LAST = 3

        fun decode(line: String): RecentResult? {
            val parts = line.split(SEPARATOR)
            val numbers = parts.drop(1).mapNotNull(String::toLongOrNull)
            if (parts.size != FIELDS || numbers.size != FIELDS - 1) return null
            return RecentResult(parts[0], numbers[0], numbers[1], numbers[2], numbers[LAST])
        }
    }
}

/** Newest first, at most [RecentResult.MAX_RECENT], no repeats of the same result. */
fun List<RecentResult>.withNewest(result: RecentResult): List<RecentResult> =
    (listOf(result) + filterNot { it.sameAs(result) }).take(RecentResult.MAX_RECENT)
