package com.agniv.hisaab.core.domain.repository

import com.agniv.hisaab.core.domain.model.Role

sealed class AccountError(
    message: String,
) : Exception(message) {
    data object Network : AccountError("Needs internet")

    data object NotAllowed : AccountError("Only an admin can do this")

    data object LastAdmin : AccountError("A family needs at least one admin")

    data object BadName : AccountError("Enter a name (up to 40 letters)")

    /** Some entries are only on this phone. Signing out now would lose them. */
    data class PendingChanges(
        val count: Int,
    ) : AccountError("$count changes are not uploaded yet")
}

/** Profile and account actions (IMPLEMENTATION_PLAN 4.10). These need internet: they change the family on the server. */
interface AccountRepository {
    suspend fun renameMe(
        memberId: String,
        name: String,
    ): Result<Unit>

    suspend fun setRole(
        memberId: String,
        role: Role,
    ): Result<Unit>

    suspend fun removeMember(memberId: String): Result<Unit>

    /** How many changes are waiting to upload. */
    suspend fun pendingChanges(): Int

    /**
     * Uploads what it can, then signs out and erases this phone's copy, so the next person to sign in never
     * sees (or merges) this account's entries. With [force] false it stops with [AccountError.PendingChanges]
     * when something could not be uploaded.
     */
    suspend fun signOut(
        force: Boolean,
        localFamilyName: String,
        localMemberName: String,
    ): Result<Unit>
}
