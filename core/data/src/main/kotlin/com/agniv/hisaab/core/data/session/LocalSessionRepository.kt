package com.agniv.hisaab.core.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.entity.FamilyEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.database.seed.DefaultCategories
import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.model.Session
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Until login exists (task 1.3), the app runs as a phone-only family so screens work offline
 * from day one. Signing in later will attach this local family to the user's Supabase account.
 */
@Singleton
class LocalSessionRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val db: HisaabDatabase,
        private val ids: IdGenerator,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : SessionRepository {
        override val session: Flow<Session?> =
            context.sessionStore.data.map { prefs ->
                val user = prefs[USER_ID]
                val family = prefs[FAMILY_ID]
                val member = prefs[MEMBER_ID]
                if (user != null && family != null && member != null) Session(user, family, member) else null
            }

        /** Creates the local family, the "me" member and default categories once. Safe to call every launch. */
        suspend fun ensureLocalSession(
            familyName: String,
            memberName: String,
        ): Session =
            withContext(dispatchers.io) {
                val now = time.now().toEpochMilli()
                if (db.categoryDao().defaultCount() == 0) {
                    db.categoryDao().upsertAll(DefaultCategories.all(now))
                }
                session.first() ?: createLocal(familyName, memberName, now)
            }

        private suspend fun createLocal(
            familyName: String,
            memberName: String,
            now: Long,
        ): Session {
            val created = Session(userId = ids.newId(), familyId = ids.newId(), memberId = ids.newId())
            db.withTransaction {
                db.familyDao().upsert(
                    FamilyEntity(
                        id = created.familyId,
                        name = familyName,
                        inviteCode = null,
                        monthlyBudgetPaise = null,
                        updatedAt = now,
                    ),
                )
                db.memberDao().upsert(
                    MemberEntity(
                        id = created.memberId,
                        familyId = created.familyId,
                        userId = created.userId,
                        name = memberName,
                        role = Role.ADMIN.name,
                        updatedAt = now,
                    ),
                )
            }
            context.sessionStore.edit {
                it[USER_ID] = created.userId
                it[FAMILY_ID] = created.familyId
                it[MEMBER_ID] = created.memberId
            }
            return created
        }

        /** Points the app at a different family/member, e.g. after linking to a signed-in account. */
        suspend fun replace(session: Session) {
            context.sessionStore.edit {
                it[USER_ID] = session.userId
                it[FAMILY_ID] = session.familyId
                it[MEMBER_ID] = session.memberId
            }
        }

        private companion object {
            val USER_ID = stringPreferencesKey("user_id")
            val FAMILY_ID = stringPreferencesKey("family_id")
            val MEMBER_ID = stringPreferencesKey("member_id")
        }
    }
