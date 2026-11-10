package com.agniv.hisaab.feature.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalcEngineTest {
    private fun eval(text: String) = CalcEngine.evaluate(text)?.let(CalcEngine::format)

    private fun type(keys: String) = keys.fold("") { acc, c -> CalcEngine.press(acc, c) }

    @Test
    fun `decimal maths is exact`() {
        assertThat(eval("0.1+0.2")).isEqualTo("0.3")
        assertThat(eval("1÷3")).isEqualTo("0.33333333")
    }

    @Test
    fun `times and divide go first`() {
        assertThat(eval("2+3×4")).isEqualTo("14")
        assertThat(eval("100−20÷4")).isEqualTo("95")
        assertThat(eval("−5+2")).isEqualTo("-3")
    }

    @Test
    fun `percent like a shop calculator`() {
        assertThat(eval("500+18%")).isEqualTo("590")
        assertThat(eval("1000−10%")).isEqualTo("900")
        assertThat(eval("18%")).isEqualTo("0.18")
        assertThat(eval("200×15%")).isEqualTo("30")
    }

    @Test
    fun `incomplete or impossible gives null`() {
        assertThat(eval("")).isNull()
        assertThat(eval("5÷0")).isNull()
        // A trailing operator is ignored so the live result keeps showing.
        assertThat(eval("12+")).isEqualTo("12")
    }

    @Test
    fun `keys never make a broken expression`() {
        assertThat(type("1++2")).isEqualTo("1+2")
        assertThat(type("1+×2")).isEqualTo("1×2")
        assertThat(type("1.2.3")).isEqualTo("1.23")
        assertThat(type(".5")).isEqualTo("0.5")
        assertThat(type("+5")).isEqualTo("5")
        assertThat(type("%5")).isEqualTo("5")
    }
}
