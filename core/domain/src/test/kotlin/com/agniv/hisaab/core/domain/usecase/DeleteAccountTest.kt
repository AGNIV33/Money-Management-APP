package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.model.Role
import com.agniv.hisaab.core.domain.repository.AccountError
import com.agniv.hisaab.core.domain.repository.AccountRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeleteAccountTest {
    private class FakeAccount(
        val signedIn: Boolean,
        val server: Result<Unit> = Result.success(Unit),
    ) : AccountRepository {
        val calls = mutableListOf<String>()

        override suspend fun hasServerAccount() = signedIn

        override suspend fun deleteServerAccount(): Result<Unit> {
            calls += "server"
            return server
        }

        override suspend fun wipeThisPhone(
            localFamilyName: String,
            localMemberName: String,
        ) {
            calls += "wipe:$localFamilyName/$localMemberName"
        }

        override suspend fun renameMe(
            memberId: String,
            name: String,
        ) = error("not used")

        override suspend fun setRole(
            memberId: String,
            role: Role,
        ) = error("not used")

        override suspend fun removeMember(memberId: String) = error("not used")

        override suspend fun pendingChanges() = 0

        override suspend fun signOut(
            force: Boolean,
            localFamilyName: String,
            localMemberName: String,
        ) = error("not used")
    }

    @Test
    fun `server first, then this phone`() =
        runTest {
            val fake = FakeAccount(signedIn = true)
            assertThat(DeleteAccount(fake)("My family", "Me").isSuccess).isTrue()
            assertThat(fake.calls).containsExactly("server", "wipe:My family/Me").inOrder()
        }

    @Test
    fun `server refuses, phone is kept`() =
        runTest {
            val fake = FakeAccount(signedIn = true, server = Result.failure(AccountError.LastAdmin))
            val result = DeleteAccount(fake)("F", "M")
            assertThat(result.exceptionOrNull()).isEqualTo(AccountError.LastAdmin)
            assertThat(fake.calls).containsExactly("server")
        }

    @Test
    fun `offline, phone is kept`() =
        runTest {
            val fake = FakeAccount(signedIn = true, server = Result.failure(AccountError.Network))
            assertThat(DeleteAccount(fake)("F", "M").exceptionOrNull()).isEqualTo(AccountError.Network)
            assertThat(fake.calls).containsExactly("server")
        }

    @Test
    fun `no account, only this phone is wiped`() =
        runTest {
            val fake = FakeAccount(signedIn = false)
            assertThat(DeleteAccount(fake)("F", "M").isSuccess).isTrue()
            assertThat(fake.calls).containsExactly("wipe:F/M")
        }

    @Test
    fun `typed confirmation`() {
        assertThat(DeleteAccount.isConfirmed(" delete ")).isTrue()
        assertThat(DeleteAccount.isConfirmed("DELETE")).isTrue()
        assertThat(DeleteAccount.isConfirmed("डिलीट")).isTrue()
        assertThat(DeleteAccount.isConfirmed("delet")).isFalse()
        assertThat(DeleteAccount.isConfirmed("")).isFalse()
    }
}
