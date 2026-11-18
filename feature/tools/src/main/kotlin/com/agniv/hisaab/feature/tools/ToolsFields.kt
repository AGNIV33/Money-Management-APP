package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** ₹ field that shows 12,00,000 while the state keeps plain digits for parsing. */
@Composable
internal fun MoneyField(
    state: MutableState<String>,
    label: Int,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = AmountInput.cleanAmount(it) },
        label = { Text(stringResource(label)) },
        prefix = { Text("₹") },
        singleLine = true,
        visualTransformation = IndianGrouping,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun PercentField(
    state: MutableState<String>,
    label: Int,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = AmountInput.cleanAmount(it) },
        label = { Text(stringResource(label)) },
        suffix = { Text("%") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Plain whole-number field (tenure, EMIs paid). */
@Composable
internal fun CountField(
    state: MutableState<String>,
    label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { new -> state.value = new.filter(Char::isDigit).take(MAX_COUNT_DIGITS) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private const val MAX_COUNT_DIGITS = 3

/** One segmented control for two or more choices (Years / Months, Finish sooner / Smaller EMI). */
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

/** Tenure number with a Years / Months switch under it. */
@Composable
internal fun TenureInput(inputs: LoanInputs) {
    CountField(inputs.tenure, R.string.field_tenure)
    Segmented(
        options = listOf(R.string.unit_years, R.string.unit_months),
        selected = if (inputs.inYears.value) 0 else 1,
        onSelect = { inputs.inYears.value = it == 0 },
    )
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
