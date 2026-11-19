package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.theme.Spacing
import kotlin.math.roundToLong

/** Slider limits for one field. Typing can go past them; the slider just stops at the end. */
internal data class SliderRange(
    val min: Float,
    val max: Float,
    val step: Float,
    val minLabel: String,
    val maxLabel: String,
)

/**
 * One input row of the boards: icon tile, label + helper, the value box on the right, and an optional slider under it.
 * [extra] sits between the label and the value box (the Years / Months switch).
 */
@Composable
internal fun InputRow(
    icon: ImageVector,
    label: Int,
    helper: Int,
    value: @Composable () -> Unit,
    slider: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            IconTile(icon, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (extra == null) value()
        }
        if (extra != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Box(Modifier.weight(1f)) { extra() }
                value()
            }
        }
        slider?.invoke()
    }
}

/**
 * Small outlined number box: "₹ 30,00,000", "8.50 %", "20 Years". The state keeps plain digits; [grouped] adds
 * Indian commas on screen only. [label] is read by TalkBack, since the box has no visible label of its own.
 */
@Composable
internal fun ValueBox(
    state: MutableState<String>,
    label: String,
    prefix: String = "",
    suffix: String = "",
    grouped: Boolean = false,
    decimal: Boolean = true,
    width: Dp = VALUE_WIDTH,
) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = state.value,
        onValueChange = { new ->
            state.value = if (decimal) AmountInput.cleanAmount(new) else new.filter(Char::isDigit).take(MAX_COUNT_DIGITS)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurface, textAlign = TextAlign.End),
        cursorBrush = SolidColor(scheme.primary),
        visualTransformation = if (grouped) IndianGrouping else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier =
            Modifier
                .width(width)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = label },
        decorationBox = { field ->
            Row(
                Modifier
                    .border(1.dp, scheme.outline, MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.m, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (prefix.isNotEmpty()) Text(prefix, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { field() }
                if (suffix.isNotEmpty()) Text(suffix, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            }
        },
    )
}

private val VALUE_WIDTH = 150.dp
private const val MAX_COUNT_DIGITS = 3

/** Slider tied to a text state: moving it writes a clean number, typing moves the thumb. */
@Composable
internal fun ValueSlider(
    state: MutableState<String>,
    range: SliderRange,
    decimals: Boolean = false,
) {
    val now = state.value.toFloatOrNull()?.coerceIn(range.min, range.max) ?: range.min
    Column {
        Slider(
            value = now,
            onValueChange = { v ->
                val snapped = (v / range.step).roundToLong() * range.step
                state.value = if (decimals) AmountInput.rateText(snapped) else snapped.roundToLong().toString()
            },
            valueRange = range.min..range.max,
            colors =
                SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.height(SLIDER_HEIGHT),
        )
        Row {
            Text(range.minLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                range.maxLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val SLIDER_HEIGHT = 32.dp

/** − [number] + box for "EMIs already paid". */
@Composable
internal fun StepperBox(
    state: MutableState<String>,
    label: String,
) {
    val now = state.value.toIntOrNull() ?: 0
    Row(
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { state.value = (now - 1).coerceAtLeast(0).toString() }) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.tools_less))
        }
        ValueBox(state, label, decimal = false, width = STEPPER_FIELD)
        IconButton(onClick = { state.value = (now + 1).toString() }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tools_more))
        }
    }
}

private val STEPPER_FIELD = 64.dp

/** One segmented control for two choices (Years / Months, One-time / Every month). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Segmented(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

/** Adds Indian commas on screen only; the field's text stays plain digits. */
internal object IndianGrouping : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = AmountInput.groupIndian(raw)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    var seen = 0
                    shown.forEachIndexed { index, c ->
                        if (seen == offset) return index
                        if (c != ',') seen++
                    }
                    return shown.length
                }

                override fun transformedToOriginal(offset: Int): Int = shown.take(offset).count { it != ',' }.coerceAtMost(raw.length)
            }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
