package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.repository.AccountRepository
import javax.inject.Inject

/**
 * Delete my account and data (5.6). Order matters: the server is asked first, and this phone is wiped only after
 * the server said yes, so a failed delete (offline, last admin) never leaves the user signed in with an empty
 * phone. Without a server account there is nothing online, so only this phone is wiped.
 */
class DeleteAccount
    @Inject
    constructor(
        private val account: AccountRepository,
    ) {
        suspend operator fun invoke(
            localFamilyName: String,
            localMemberName: String,
        ): Result<Unit> {
            if (account.hasServerAccount()) {
                val server = account.deleteServerAccount()
                if (server.isFailure) return server
            }
            account.wipeThisPhone(localFamilyName, localMemberName)
            return Result.success(Unit)
        }

        companion object {
            /** What the user must type to confirm, in either language. Case and spaces around it do not matter. */
            val CONFIRM_WORDS = setOf("DELETE", "डिलीट")

            fun isConfirmed(typed: String): Boolean = typed.trim().uppercase() in CONFIRM_WORDS
        }
    }
