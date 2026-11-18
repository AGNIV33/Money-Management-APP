package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.ButtonShape
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.tools.CalcEngine.DIVIDE
import com.agniv.hisaab.feature.tools.CalcEngine.MINUS
import com.agniv.hisaab.feature.tools.CalcEngine.PERCENT
import com.agniv.hisaab.feature.tools.CalcEngine.PLUS
import com.agniv.hisaab.feature.tools.CalcEngine.TIMES

private const val CLEAR = "C"
private const val BACK = "⌫"
private const val EQUALS = "="
private const val DOUBLE_ZERO = "00"

// Phone-calculator layout (PRD F9a); every key the same size, so the grid is even.
private val KEYS =
    listOf(
        listOf(CLEAR, BACK, "$PERCENT", "$DIVIDE"),
        listOf("7", "8", "9", "$TIMES"),
        listOf("4", "5", "6", "$MINUS"),
        listOf("1", "2", "3", "$PLUS"),
        listOf(DOUBLE_ZERO, "0", ".", EQUALS),
    )

private val OPERATORS = setOf("$PERCENT", "$DIVIDE", "$TIMES", "$MINUS", "$PLUS")

@Composable
internal fun CalculatorTool(onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val result = CalcEngine.evaluate(text)?.let(CalcEngine::format)
    val haptics = LocalHapticFeedback.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        KinlyTopBar(title = stringResource(R.string.tool_calculator), onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.l)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            HCard(Modifier.fillMaxWidth().weight(1f)) {
                Spacer(Modifier.weight(1f))
                // Expression above, answer below, both right-aligned like a phone calculator.
                Text(
                    if (result != null && result != text) text else "",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
                )
                Text(
                    result ?: text.ifEmpty { "0" },
                    style = DisplayAmount.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l),
                )
            }
            KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    row.forEach { key ->
                        CalcKey(key, Modifier.weight(1f)) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            text =
                                when (key) {
                                    CLEAR -> ""
                                    BACK -> text.dropLast(1)
                                    EQUALS -> result?.replace('-', MINUS) ?: text
                                    else -> key.fold(text) { acc, c -> CalcEngine.press(acc, c) }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    key: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (key) {
            EQUALS -> scheme.primary to scheme.onPrimary
            in OPERATORS -> scheme.primaryContainer to scheme.onPrimaryContainer
            CLEAR, BACK -> scheme.surfaceContainerHighest to scheme.error
            else -> scheme.surfaceContainerHigh to scheme.onSurface
        }
    val spoken =
        when (key) {
            BACK -> stringResource(R.string.calc_backspace)
            CLEAR -> stringResource(R.string.calc_clear)
            else -> null
        }
    Button(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        elevation = null,
        modifier =
            modifier
                .heightIn(min = KEY_HEIGHT)
                .semantics { if (spoken != null) contentDescription = spoken },
    ) {
        Text(key, style = MaterialTheme.typography.titleLarge, color = content)
    }
}

private val KEY_HEIGHT = 64.dp
