package com.agniv.hisaab.core.domain.repository

import com.agniv.hisaab.core.domain.tools.RecentResult
import kotlinx.coroutines.flow.Flow

/**
 * The Tools "Recently used" list. Kept only on this phone: it is a convenience list, not family data, so it is not
 * synced and not in Room.
 */
interface ToolsHistory {
    /** Newest first. */
    val recent: Flow<List<RecentResult>>

    suspend fun add(result: RecentResult)
}
