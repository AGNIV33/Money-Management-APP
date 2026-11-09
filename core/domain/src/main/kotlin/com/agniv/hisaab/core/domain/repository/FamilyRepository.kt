package com.agniv.hisaab.core.domain.repository

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.domain.model.Family
import kotlinx.coroutines.flow.Flow

sealed class FamilyError(
    message: String,
) : Exception(message) {
    data object InvalidCode : FamilyError("No family has this code")

    data object Full : FamilyError("Family is full")

    data object Network : FamilyError("Needs internet")

    data object NotSignedIn : FamilyError("Not signed in")
}

interface FamilyRepository {
    fun observeFamily(familyId: String): Flow<Family?>

    /**
     * After sign-in: if this account already has a family on the server, use it on this phone.
     * Returns true when one was found and linked.
     */
    suspend fun linkExistingFamily(): Result<Boolean>

    /** Creates a family on the server, keeping everything already saved on this phone. */
    suspend fun createFamily(
        familyName: String,
        myName: String,
    ): Result<Unit>

    /** Joins with a 6-letter invite code; this phone's expenses move into that family. */
    suspend fun joinFamily(
        code: String,
        myName: String,
    ): Result<Unit>

    /** Family monthly budget (PRD F6). Null removes it. Saved on the phone first, then synced. */
    suspend fun setMonthlyBudget(
        familyId: String,
        budget: Money?,
    ): Result<Unit>
}
