package com.agniv.hisaab.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.agniv.hisaab.core.database.dao.CategoryDao
import com.agniv.hisaab.core.database.dao.ExpenseDao
import com.agniv.hisaab.core.database.dao.FamilyDao
import com.agniv.hisaab.core.database.dao.GoalDao
import com.agniv.hisaab.core.database.dao.LoanDao
import com.agniv.hisaab.core.database.dao.MemberDao
import com.agniv.hisaab.core.database.dao.RecurringDao
import com.agniv.hisaab.core.database.dao.SyncQueueDao
import com.agniv.hisaab.core.database.dao.UdhaarDao
import com.agniv.hisaab.core.database.entity.CategoryEntity
import com.agniv.hisaab.core.database.entity.ExpenseEntity
import com.agniv.hisaab.core.database.entity.FamilyEntity
import com.agniv.hisaab.core.database.entity.GoalContributionEntity
import com.agniv.hisaab.core.database.entity.GoalEntity
import com.agniv.hisaab.core.database.entity.LoanEntity
import com.agniv.hisaab.core.database.entity.LoanPaymentEntity
import com.agniv.hisaab.core.database.entity.MemberEntity
import com.agniv.hisaab.core.database.entity.RecurringEntity
import com.agniv.hisaab.core.database.entity.SyncQueueEntity
import com.agniv.hisaab.core.database.entity.UdhaarEntity
import com.agniv.hisaab.core.database.entity.UdhaarPaymentEntity

@Database(
    entities = [
        FamilyEntity::class,
        MemberEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        SyncQueueEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        RecurringEntity::class,
        UdhaarEntity::class,
        UdhaarPaymentEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
    ],
    version = 5,
    exportSchema = true,
    // v2 only adds the loan tables, so Room can migrate on its own (no data loss on update).
    autoMigrations = [
        AutoMigration(
            from = 1,
            to = 2,
        ), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
    ],
)
abstract class HisaabDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    abstract fun familyDao(): FamilyDao

    abstract fun memberDao(): MemberDao

    abstract fun syncQueueDao(): SyncQueueDao

    abstract fun loanDao(): LoanDao

    abstract fun recurringDao(): RecurringDao

    abstract fun udhaarDao(): UdhaarDao

    abstract fun goalDao(): GoalDao

    companion object {
        const val NAME = "hisaab.db"
    }
}
