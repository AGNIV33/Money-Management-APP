package com.agniv.hisaab.core.data.di

import com.agniv.hisaab.core.data.auth.SupabaseAuthRepository
import com.agniv.hisaab.core.data.family.LinkingFamilyRepository
import com.agniv.hisaab.core.data.repository.OfflineFirstExpenseRepository
import com.agniv.hisaab.core.data.repository.RoomCategoryRepository
import com.agniv.hisaab.core.data.repository.RoomMemberRepository
import com.agniv.hisaab.core.data.session.LocalSessionRepository
import com.agniv.hisaab.core.domain.repository.AuthRepository
import com.agniv.hisaab.core.domain.repository.CategoryRepository
import com.agniv.hisaab.core.domain.repository.ExpenseRepository
import com.agniv.hisaab.core.domain.repository.FamilyRepository
import com.agniv.hisaab.core.domain.repository.MemberRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.util.IdGenerator
import com.agniv.hisaab.core.domain.util.SystemTimeProvider
import com.agniv.hisaab.core.domain.util.TimeProvider
import com.agniv.hisaab.core.domain.util.UuidGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds abstract fun expenseRepository(impl: OfflineFirstExpenseRepository): ExpenseRepository

    @Binds abstract fun categoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds abstract fun memberRepository(impl: RoomMemberRepository): MemberRepository

    @Binds abstract fun sessionRepository(impl: LocalSessionRepository): SessionRepository

    @Binds abstract fun authRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds abstract fun familyRepository(impl: LinkingFamilyRepository): FamilyRepository

    @Binds abstract fun timeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds abstract fun idGenerator(impl: UuidGenerator): IdGenerator
}
