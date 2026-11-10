package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.tools.CalcEngine.DIVIDE
import com.agniv.hisaab.feature.tools.CalcEngine.MINUS
import com.agniv.hisaab.feature.tools.CalcEngine.PERCENT
import com.agniv.hisaab.feature.tools.CalcEngine.PLUS
import com.agniv.hisaab.feature.tools.CalcEngine.TIMES

private const val CLEAR = 'C'
private const val BACK = '⌫'
private const val EQUALS = '='

// Big keys, phone-calculator layout (PRD F9a).
private val KEYS =
    listOf(
        listOf(CLEAR, BACK, PERCENT, DIVIDE),
        listOf('7', '8', '9', TIMES),
        listOf('4', '5', '6', MINUS),
        listOf('1', '2', '3', PLUS),
        listOf('0', '.', EQUALS),
    )

@Composable
internal fun CalculatorTool(onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val result = CalcEngine.evaluate(text)?.let(CalcEngine::format)
    val backspace = stringResource(R.string.calc_backspace)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ToolHeader(Tool.CALCULATOR, onBack)
        Spacer(Modifier.weight(1f))
        Text(
            text.ifEmpty { "0" },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            result ?: "",
            style = DisplayAmount,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            text =
                                when (key) {
                                    CLEAR -> ""
                                    BACK -> text.dropLast(1)
                                    EQUALS -> result?.replace('-', MINUS) ?: text
                                    else -> CalcEngine.press(text, key)
                                }
                        },
                        modifier =
                            Modifier
                                .weight(if (key == '0') 2f else 1f)
                                .height(64.dp)
                                .semantics { if (key == BACK) contentDescription = backspace },
                    ) {
                        Text(key.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
