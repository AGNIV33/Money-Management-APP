package com.agniv.hisaab.feature.expenses.home

import com.agniv.hisaab.feature.expenses.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GreetingTest {
    @Test
    fun `morning from 4 am to before noon`() {
        assertThat(greetingRes(4)).isEqualTo(R.string.home_greeting_morning)
        assertThat(greetingRes(11)).isEqualTo(R.string.home_greeting_morning)
    }

    @Test
    fun `afternoon from noon to before 5 pm`() {
        assertThat(greetingRes(12)).isEqualTo(R.string.home_greeting_afternoon)
        assertThat(greetingRes(16)).isEqualTo(R.string.home_greeting_afternoon)
    }

    @Test
    fun `evening late in the day and after midnight`() {
        assertThat(greetingRes(17)).isEqualTo(R.string.home_greeting_evening)
        assertThat(greetingRes(23)).isEqualTo(R.string.home_greeting_evening)
        assertThat(greetingRes(2)).isEqualTo(R.string.home_greeting_evening)
    }
}
