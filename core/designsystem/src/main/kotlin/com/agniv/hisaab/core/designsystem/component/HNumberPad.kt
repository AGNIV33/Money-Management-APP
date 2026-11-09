package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing

@Suppress("MagicNumber") // the digits themselves
private val rows =
    listOf(
        listOf(PadKey.Digit(1), PadKey.Digit(2), PadKey.Digit(3)),
        listOf(PadKey.Digit(4), PadKey.Digit(5), PadKey.Digit(6)),
        listOf(PadKey.Digit(7), PadKey.Digit(8), PadKey.Digit(9)),
        listOf(PadKey.Dot, PadKey.Digit(0), PadKey.Backspace),
    )

/** Big 3×4 keypad: faster than the system keyboard and never covers the Save button (DESIGN.md §6). */
@Composable
fun HNumberPad(
    onKey: (PadKey) -> Unit,
    backspaceDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = { onKey(key) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .then(
                                    if (key == PadKey.Backspace) {
                                        Modifier.semantics { contentDescription = backspaceDescription }
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        Text(
                            text =
                                when (key) {
                                    is PadKey.Digit -> key.value.toString()
                                    PadKey.Dot -> "."
                                    PadKey.Backspace -> "⌫"
                                },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun HNumberPadPreview() {
    HisaabTheme { HNumberPad(onKey = {}, backspaceDescription = "Delete", modifier = Modifier.fillMaxWidth()) }
}
