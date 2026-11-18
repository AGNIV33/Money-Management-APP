package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.component.HCard
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
private const val GST_HIGH = 18
private const val GST_MID = 12
private const val GST_LOW = 5
private val GST_RATES = listOf(GST_HIGH, GST_MID, GST_LOW)
private const val HISTORY_SIZE = 10
private const val HISTORY_SEPARATOR = "\n"

/** Calculator text plus history and chip state; kept across rotation. Logic lives here so the screen stays simple. */
private class CalcState(
    val text: MutableState<String>,
    // Joined with new lines so it saves across rotation without a custom saver.
    val history: MutableState<String>,
    val chip: MutableState<Int>,
    val discountOpen: MutableState<Boolean>,
) {
    val result get() = CalcEngine.evaluate(text.value)?.let(CalcEngine::format)

    /** The answer (or the text) with the calculator's own minus sign, to build GST / discount on. */
    private val base get() = (result ?: text.value).replace('-', MINUS)

    private val baseReady get() = base.lastOrNull()?.isDigit() == true

    val historyLines get() = history.value.lines().filter { it.isNotBlank() }

    fun press(key: String) {
        chip.value = 0
        discountOpen.value = false
        text.value =
            when (key) {
                CLEAR -> ""
                BACK -> text.value.dropLast(1)
                EQUALS -> equals()
                else -> key.fold(text.value) { acc, c -> CalcEngine.press(acc, c) }
            }
    }

    private fun equals(): String {
        val answer = result ?: return text.value
        if (answer != text.value) {
            history.value =
                (listOf("${shown(text.value)} = ${shownNumber(answer)}") + historyLines).take(HISTORY_SIZE).joinToString(HISTORY_SEPARATOR)
        }
        return answer.replace('-', MINUS)
    }

    fun gst(rate: Int) {
        if (!baseReady) return
        text.value = "$base$PLUS$rate$PERCENT"
        chip.value = rate
    }

    /** First tap adds "−", then the user types the %, second tap adds "%": 12,000 − 10% = 10,800. */
    fun discount() {
        chip.value = 0
        if (discountOpen.value && text.value.lastOrNull()?.isDigit() == true) {
            text.value += PERCENT
            discountOpen.value = false
        } else if (baseReady) {
            text.value = "$base$MINUS"
            discountOpen.value = true
        }
    }

    fun pick(line: String) {
        text.value = line.substringAfter("= ").filter { it != ',' }.replace('-', MINUS)
    }
}

@Composable
internal fun CalculatorTool(onBack: () -> Unit) {
    val calc =
        CalcState(
            rememberSaveable { mutableStateOf("") },
            rememberSaveable { mutableStateOf("") },
            rememberSaveable { mutableStateOf(0) },
            rememberSaveable { mutableStateOf(false) },
        )
    val haptics = LocalHapticFeedback.current
    val text = calc.text.value
    val result = calc.result
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.l)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        PageHeader(stringResource(R.string.tool_calculator), null, Icons.Outlined.Calculate, onBack)
        Display(
            expression = if (result != null && result != text) shown(text) else "",
            value = result?.let(::shownNumber) ?: shown(text).ifEmpty { "0" },
            history = calc.historyLines,
            onPick = calc::pick,
            modifier = Modifier.weight(1f),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            GST_RATES.forEach { rate ->
                val picked = calc.chip.value == rate
                FilterChip(
                    selected = picked,
                    onClick = { calc.gst(rate) },
                    label = { Text(stringResource(R.string.calc_gst, rate)) },
                    leadingIcon = if (picked) ({ Icon(Icons.Outlined.LocalOffer, contentDescription = null) }) else null,
                )
            }
            FilterChip(
                selected = calc.discountOpen.value,
                onClick = calc::discount,
                label = { Text(stringResource(R.string.calc_discount)) },
            )
        }
        KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { key ->
                    CalcKey(key, Modifier.weight(1f)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        calc.press(key)
                    }
                }
            }
        }
    }
}

/** Commas in every number, so 1200000 reads as 12,00,000. */
private fun shown(expression: String) = AmountInput.groupNumbers(expression)

private fun shownNumber(value: String) = if (value.startsWith("-")) "-" + shown(value.drop(1)) else shown(value)

@Composable
private fun Display(
    expression: String,
    value: String,
    history: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    HCard(modifier.fillMaxWidth().heightIn(min = DISPLAY_MIN)) {
        Row(Modifier.padding(start = Spacing.l, end = Spacing.xs, top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                expression,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menu = true }, enabled = history.isNotEmpty()) {
                    Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.calc_history))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    history.forEach { line ->
                        DropdownMenuItem(
                            text = { Text(line) },
                            onClick = {
                                onPick(line)
                                menu = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = DisplayAmount.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
        )
    }
}

private val DISPLAY_MIN = 140.dp

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
            CLEAR, BACK -> scheme.surfaceContainerHighest to scheme.onSurface
            else -> scheme.surfaceContainerHigh to scheme.onSurface
        }
    Button(
        onClick = onClick,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        elevation = null,
        modifier = modifier.heightIn(min = KEY_HEIGHT),
    ) {
        when (key) {
            BACK -> Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.calc_backspace))
            CLEAR -> {
                val clear = stringResource(R.string.calc_clear)
                Text(key, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = clear })
            }
            else -> Text(key, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private val KEY_HEIGHT = 60.dp
