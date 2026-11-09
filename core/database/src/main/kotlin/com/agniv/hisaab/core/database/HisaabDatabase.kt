package com.agniv.hisaab.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.agniv.hisaab.core.database.dao.CategoryDao
import com.agniv.hisaab.core.database.dao.ExpenseDao
import com.agniv.hisaab.core.database.dao.FamilyDao
import com.agniv.hisaab.core.database.dao.MemberDao
import com.agniv.hisaab.core.database.dao.SyncQueueDao
import com.agniv.hisaab.core.database.entity.CategoryEntity
import com.agniv.hisaab.core.database.entity.ExpenseEntity
import com.agniv.hisaab.core.database.entity.FamilyEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.database.entity.SyncQueueEntity

@Database(
    entities = [
        FamilyEntity::class,
        MemberEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HisaabDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    abstract fun familyDao(): FamilyDao

    abstract fun memberDao(): MemberDao

    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val NAME = "hisaab.db"
    }
}
