package com.agniv.hisaab.core.designsystem.component

/** A key on [HNumberPad]. */
sealed interface PadKey {
    data class Digit(
        val value: Int,
    ) : PadKey

    data object Dot : PadKey

    data object Backspace : PadKey
}
