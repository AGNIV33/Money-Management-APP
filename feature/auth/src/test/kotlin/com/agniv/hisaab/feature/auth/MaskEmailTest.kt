package com.agniv.hisaab.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MaskEmailTest {
    @Test
    fun `keeps the first letter and the domain`() {
        assertThat(maskEmail("agniv@example.com")).isEqualTo("a***@example.com")
    }

    @Test
    fun `leaves anything without a name part alone`() {
        assertThat(maskEmail("@example.com")).isEqualTo("@example.com")
        assertThat(maskEmail("not-an-email")).isEqualTo("not-an-email")
    }
}
