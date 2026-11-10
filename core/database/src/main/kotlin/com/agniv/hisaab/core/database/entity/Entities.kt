package com.agniv.hisaab.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Column names match the Supabase tables (snake_case) so sync mapping stays 1:1.
// Dates are epoch days, timestamps epoch millis, money is paise (Long).

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "invite_code") val inviteCode: String?,
    @ColumnInfo(name = "monthly_budget_paise") val monthlyBudgetPaise: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "members",
    indices = [Index("family_id")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val role: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "categories",
    indices = [Index("family_id")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String?,
    val name: String,
    @ColumnInfo(name = "name_hi") val nameHi: String?,
    val icon: String,
    val color: Long,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["family_id", "date"]),
        Index("category_id"),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "paid_by") val paidBy: String,
    @ColumnInfo(name = "owner_user") val ownerUser: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "category_id") val categoryId: String,
    /** Epoch day (LocalDate.toEpochDay). */
    val date: Long,
    val note: String?,
    val mode: String,
    val visibility: String,
    val source: String,
    @ColumnInfo(name = "recurring_id") val recurringId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Pending local changes waiting to be pushed to Supabase, oldest first. */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["table_name", "row_id"])],
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** UPSERT or DELETE. */
    val op: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "loans",
    indices = [Index("family_id")],
)
data class LoanEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "owner_user") val ownerUser: String,
    val type: String,
    val lender: String,
    @ColumnInfo(name = "principal_paise") val principalPaise: Long,
    @ColumnInfo(name = "annual_rate_bps") val annualRateBps: Int,
    @ColumnInfo(name = "tenure_months") val tenureMonths: Int,
    @ColumnInfo(name = "emi_paise") val emiPaise: Long,
    /** Epoch day of instalment 1. */
    @ColumnInfo(name = "first_emi_date") val firstEmiDate: Long,
    @ColumnInfo(name = "due_day") val dueDay: Int,
    /** Epoch day the loan was entered in the app. */
    @ColumnInfo(name = "added_on") val addedOn: Long,
    val visibility: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "loan_payments",
    indices = [Index("loan_id"), Index("family_id")],
)
data class LoanPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "loan_id") val loanId: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "owner_user") val ownerUser: String,
    /** First day of the month the EMI was due in, as epoch day. */
    val month: Long,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "paid_on") val paidOn: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "recurring",
    indices = [Index("family_id")],
)
data class RecurringEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "owner_user") val ownerUser: String,
    @ColumnInfo(name = "paid_by") val paidBy: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val mode: String,
    val note: String?,
    val visibility: String,
    val rule: String,
    /** Epoch days. */
    @ColumnInfo(name = "start_date") val startDate: Long,
    @ColumnInfo(name = "last_generated") val lastGenerated: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "udhaar",
    indices = [Index("family_id")],
)
data class UdhaarEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "owner_user") val ownerUser: String,
    val direction: String,
    val person: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    /** Epoch days. */
    val date: Long,
    @ColumnInfo(name = "due_date") val dueDate: Long?,
    val note: String?,
    @ColumnInfo(name = "expense_id") val expenseId: String?,
    val visibility: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "udhaar_payments",
    indices = [Index("udhaar_id"), Index("family_id")],
)
data class UdhaarPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "udhaar_id") val udhaarId: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "owner_user") val ownerUser: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "paid_on") val paidOn: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
)
