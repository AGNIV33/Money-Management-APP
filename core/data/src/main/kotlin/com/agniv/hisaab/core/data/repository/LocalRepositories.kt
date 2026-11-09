package com.agniv.hisaab.core.data.repository

import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.data.mapper.toDomain
import com.agniv.hisaab.core.database.dao.CategoryDao
import com.agniv.hisaab.core.database.dao.MemberDao
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Member
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomCategoryRepository
    @Inject
    constructor(
        private val dao: CategoryDao,
        private val dispatchers: DispatcherProvider,
    ) : CategoryRepository {
        override fun observeCategories(familyId: String): Flow<List<Category>> =
            dao
                .observeForFamily(familyId)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(dispatchers.default)
    }

class RoomMemberRepository
    @Inject
    constructor(
        private val dao: MemberDao,
        private val dispatchers: DispatcherProvider,
    ) : MemberRepository {
        override fun observeMembers(familyId: String): Flow<List<Member>> =
            dao
                .observeForFamily(familyId)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(dispatchers.default)
    }
