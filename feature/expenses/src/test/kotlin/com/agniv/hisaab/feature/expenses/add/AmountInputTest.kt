package com.agniv.hisaab.feature.expenses.add

import com.agniv.hisaab.core.designsystem.component.PadKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountInputTest {
    private fun type(vararg keys: PadKey): String = keys.fold("") { text, key -> AmountInput.apply(text, key) }

    private fun d(n: Int) = PadKey.Digit(n)

    @Test
    fun `digits build an amount`() {
        assertThat(type(d(2), d(5), d(0))).isEqualTo("250")
    }

    @Test
    fun `leading zero is replaced`() {
        assertThat(type(d(0), d(7))).isEqualTo("7")
    }

    @Test
    fun `dot first gives zero point`() {
        assertThat(type(PadKey.Dot, d(5))).isEqualTo("0.5")
    }

    @Test
    fun `only one dot and two paise digits`() {
        assertThat(type(d(9), PadKey.Dot, d(9), PadKey.Dot, d(9), d(9))).isEqualTo("9.99")
    }

    @Test
    fun `rupee part is capped at nine digits`() {
        val keys = Array<PadKey>(12) { d(9) }
        assertThat(type(*keys)).isEqualTo("999999999")
    }

    @Test
    fun `backspace removes last char and is safe when empty`() {
        assertThat(type(d(1), d(2), PadKey.Backspace)).isEqualTo("1")
        assertThat(type(PadKey.Backspace)).isEqualTo("")
    }
}
