package com.agniv.hisaab.core.data.tools

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agniv.hisaab.core.domain.repository.ToolsHistory
import com.agniv.hisaab.core.domain.tools.RecentResult
import com.agniv.hisaab.core.domain.tools.withNewest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolsStore: DataStore<Preferences> by preferencesDataStore(name = "tools_recent")
private val RECENT = stringPreferencesKey("recent")

@Singleton
class DataStoreToolsHistory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ToolsHistory {
        override val recent: Flow<List<RecentResult>> =
            context.toolsStore.data.map { prefs -> decode(prefs[RECENT]) }

        override suspend fun add(result: RecentResult) {
            context.toolsStore.edit { prefs ->
                prefs[RECENT] = decode(prefs[RECENT]).withNewest(result).joinToString("\n") { it.encode() }
            }
        }

        private fun decode(text: String?) = text.orEmpty().lines().mapNotNull(RecentResult::decode)
    }
