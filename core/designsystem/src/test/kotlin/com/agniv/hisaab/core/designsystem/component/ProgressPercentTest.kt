package com.agniv.hisaab.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressPercentTest {
    @Test
    fun roundsToWholePercent() {
        assertThat(progressPercent(0f)).isEqualTo(0)
        assertThat(progressPercent(0.854f)).isEqualTo(85)
        assertThat(progressPercent(0.857f)).isEqualTo(86)
        assertThat(progressPercent(1f)).isEqualTo(100)
    }

    @Test
    fun overBudgetSaysTheRealShare() {
        assertThat(progressPercent(1.2f)).isEqualTo(120)
    }

    @Test
    fun brokenInputSaysZero() {
        assertThat(progressPercent(Float.NaN)).isEqualTo(0)
        assertThat(progressPercent(-0.5f)).isEqualTo(0)
    }
}
