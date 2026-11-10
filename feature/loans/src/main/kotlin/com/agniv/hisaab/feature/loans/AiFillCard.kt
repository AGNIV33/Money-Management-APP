package com.agniv.hisaab.feature.loans

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.ml.scan.PdfLockedException
import com.agniv.hisaab.core.ml.scan.PdfReader
import com.agniv.hisaab.core.ml.scan.TextScanner
import kotlinx.coroutines.launch

private const val MIN_LINES = 4

/**
 * "Fill with AI" for a new loan (PRD F9 / plan 4.8). The person pastes or types the loan details, or picks a
 * picture or PDF of the letter; the text is read ON THE PHONE and shown in the box, so they see exactly what
 * would be sent. Account, phone and ID numbers are hidden before sending. The result only fills the form below.
 */
@Composable
internal fun AiFillCard(
    state: AddLoanUiState,
    onEvent: (AddLoanEvent) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun scan(read: suspend () -> String) {
        scope.launch {
            runCatching { read() }
                .onSuccess { onEvent(AddLoanEvent.AiAppend(it)) }
                .onFailure { onEvent(AddLoanEvent.AiScanProblem(it is PdfLockedException)) }
        }
    }
    val pickPicture =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { scan { TextScanner.read(context, it) } }
        }
    val pickPdf =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { scan { PdfReader.read(context, it) { _, _ -> } } }
        }

    if (!state.aiOpen) {
        OutlinedButton(onClick = { onEvent(AddLoanEvent.AiToggle) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ai_fill_button))
        }
        if (state.aiFilled) {
            Text(
                stringResource(R.string.ai_fill_check),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.ai_fill_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.ai_fill_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.aiText,
                onValueChange = { onEvent(AddLoanEvent.AiText(it)) },
                label = { Text(stringResource(R.string.ai_fill_hint)) },
                minLines = MIN_LINES,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(
                    onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) { Text(stringResource(R.string.ai_fill_picture)) }
                OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text(stringResource(R.string.ai_fill_pdf)) }
            }
            state.aiMessage?.let {
                Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(onClick = { onEvent(AddLoanEvent.AiRun) }, enabled = !state.aiBusy) {
                    if (state.aiBusy) CircularProgressIndicator(Modifier.padding(end = Spacing.s).heightIn(max = 20.dp))
                    Text(stringResource(if (state.aiBusy) R.string.ai_fill_reading else R.string.ai_fill_run))
                }
                OutlinedButton(onClick = { onEvent(AddLoanEvent.AiToggle) }, enabled = !state.aiBusy) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

private fun LoanAiMessage.textRes(): Int =
    when (this) {
        LoanAiMessage.TOO_SHORT -> R.string.ai_fill_err_short
        LoanAiMessage.OFFLINE -> R.string.ai_fill_err_offline
        LoanAiMessage.DAILY_LIMIT -> R.string.ai_fill_err_limit
        LoanAiMessage.NOTHING_FOUND -> R.string.ai_fill_err_nothing
        LoanAiMessage.NOT_AVAILABLE -> R.string.ai_fill_err_unavailable
        LoanAiMessage.FAILED -> R.string.ai_fill_err_failed
        LoanAiMessage.PDF_LOCKED -> R.string.ai_fill_err_locked
        LoanAiMessage.READ_FAILED -> R.string.ai_fill_err_read
    }
