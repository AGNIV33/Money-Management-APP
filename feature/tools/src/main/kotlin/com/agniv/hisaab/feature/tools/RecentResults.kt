package com.agniv.hisaab.feature.tools

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One finished calculation for the "Recently used" list. Numbers only, so the row is drawn in the current language.
 * [a], [b] and [c] mean different things per tool; see [RecentResult.Companion].
 */
@Immutable
data class RecentResult(
    val tool: String,
    val at: Long,
    val a: Long,
    val b: Long,
    val c: Long,
) {
    /** Same tool and same numbers: saving it again only moves it to the top. */
    fun sameAs(other: RecentResult) = tool == other.tool && a == other.a && b == other.b && c == other.c

    internal fun encode() = listOf(tool, at, a, b, c).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ";"
        private const val FIELDS = 5
        private const val LAST = 3

        internal fun decode(line: String): RecentResult? {
            val parts = line.split(SEPARATOR)
            val numbers = parts.drop(1).mapNotNull(String::toLongOrNull)
            if (parts.size != FIELDS || numbers.size != FIELDS - 1) return null
            return RecentResult(parts[0], numbers[0], numbers[1], numbers[2], numbers[LAST])
        }
    }
}

/** Newest first, at most [MAX_RECENT], no repeats of the same result. Pure so it can be unit tested. */
internal fun List<RecentResult>.withNewest(result: RecentResult): List<RecentResult> =
    (listOf(result) + filterNot { it.sameAs(result) }).take(MAX_RECENT)

internal const val MAX_RECENT = 5

internal fun recent(
    tool: Tool,
    a: Long,
    b: Long = 0,
    c: Long = 0,
) = RecentResult(tool.name, System.currentTimeMillis(), a, b, c)

private val Context.toolsStore: DataStore<Preferences> by preferencesDataStore(name = "tools_recent")
private val RECENT = stringPreferencesKey("recent")

/**
 * Kept on this phone only: it is a convenience list, not family data, so it is not synced and not in Room.
 * Only numbers are stored, never names or notes.
 */
@Singleton
class RecentResultsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val recent: Flow<List<RecentResult>> =
            context.toolsStore.data.map { prefs ->
                prefs[RECENT].orEmpty().lines().mapNotNull(RecentResult::decode)
            }

        suspend fun add(result: RecentResult) {
            context.toolsStore.edit { prefs ->
                val now = prefs[RECENT].orEmpty().lines().mapNotNull(RecentResult::decode)
                prefs[RECENT] = now.withNewest(result).joinToString("\n") { it.encode() }
            }
        }
    }
