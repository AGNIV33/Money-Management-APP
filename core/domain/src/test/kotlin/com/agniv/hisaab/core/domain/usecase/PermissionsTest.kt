package com.agniv.hisaab.core.domain.usecase

import com.agniv.hisaab.core.domain.model.Role
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PermissionsTest {
    @Test
    fun `creator or admin may delete, other members may not`() {
        assertThat(canDelete("u1", "u1", Role.MEMBER)).isTrue()
        assertThat(canDelete("u1", "u2", Role.ADMIN)).isTrue()
        assertThat(canDelete("u1", "u2", Role.MEMBER)).isFalse()
        assertThat(canDelete("u1", "u2", null)).isFalse()
    }
}
