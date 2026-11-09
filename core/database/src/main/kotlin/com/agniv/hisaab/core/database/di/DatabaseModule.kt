package com.agniv.hisaab.core.database.di

import android.content.Context
import androidx.room.Room
import com.agniv.hisaab.core.database.HisaabDatabase
import com.agniv.hisaab.core.database.dao.CategoryDao
import com.agniv.hisaab.core.database.dao.ExpenseDao
import com.agniv.hisaab.core.database.dao.FamilyDao
import com.agniv.hisaab.core.database.dao.MemberDao
import com.agniv.hisaab.core.database.dao.SyncQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): HisaabDatabase = Room.databaseBuilder(context, HisaabDatabase::class.java, HisaabDatabase.NAME).build()

    @Provides fun expenseDao(db: HisaabDatabase): ExpenseDao = db.expenseDao()

    @Provides fun categoryDao(db: HisaabDatabase): CategoryDao = db.categoryDao()

    @Provides fun familyDao(db: HisaabDatabase): FamilyDao = db.familyDao()

    @Provides fun memberDao(db: HisaabDatabase): MemberDao = db.memberDao()

    @Provides fun syncQueueDao(db: HisaabDatabase): SyncQueueDao = db.syncQueueDao()
}
