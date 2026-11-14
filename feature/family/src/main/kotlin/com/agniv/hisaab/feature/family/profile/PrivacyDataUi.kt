package com.agniv.hisaab.feature.family.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.export.ExportLabels
import com.agniv.hisaab.core.domain.export.ExportRange
import com.agniv.hisaab.core.domain.usecase.DeleteAccount
import com.agniv.hisaab.feature.family.R
import com.agniv.hisaab.feature.family.export.ExportFormat
import com.agniv.hisaab.feature.family.export.ExportTexts
import com.agniv.hisaab.feature.family.export.ExportViewModel
import com.agniv.hisaab.feature.family.export.PdfTitle
import java.io.File

/** "Privacy & data" in the Profile sheet (DESIGN.md 7.11): export and delete account. */
@Composable
internal fun PrivacySection(
    busy: Boolean,
    message: ProfileMessage?,
    onDelete: () -> Unit,
) {
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { showExport = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.export_button))
        }
        OutlinedButton(
            onClick = { showDelete = true },
            enabled = !busy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.delete_account_button))
        }
        val error =
            when (message) {
                ProfileMessage.DELETE_OFFLINE -> R.string.delete_err_offline
                ProfileMessage.DELETE_LAST_ADMIN -> R.string.delete_err_last_admin
                ProfileMessage.DELETE_FAILED -> R.string.delete_err_failed
                else -> null
            }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    if (showExport) ExportDialog(onDismiss = { showExport = false })
    if (showDelete) {
        DeleteAccountDialog(
            onConfirm = {
                showDelete = false
                onDelete()
            },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.delete_account_text))
                Text(stringResource(R.string.delete_account_type), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = typed, onValueChange = { typed = it.take(MAX_TYPED) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = DeleteAccount.isConfirmed(typed)) {
                Text(stringResource(R.string.delete_account_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(key = "export"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val texts = exportTexts()
    LaunchedEffect(state.file) {
        val file = state.file ?: return@LaunchedEffect
        share(context, file, state.mime ?: "*/*", context.getString(R.string.export_share_title))
        viewModel.shared()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ExportRange.entries.forEach { range ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = state.range == range, onClick = { viewModel.setRange(range) }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.range == range, onClick = null)
                        Text(stringResource(range.label()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    stringResource(R.string.export_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    state.busy -> Text(stringResource(R.string.export_working))
                    state.empty -> Text(stringResource(R.string.export_empty), color = MaterialTheme.colorScheme.error)
                    state.failed -> Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Button(onClick = { viewModel.export(ExportFormat.CSV, texts) }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_csv))
                    }
                    Button(onClick = { viewModel.export(ExportFormat.PDF, texts) }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.export_pdf))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) } },
    )
}

@Composable
private fun exportTexts(): ExportTexts {
    val labels =
        ExportLabels(
            date = stringResource(R.string.export_col_date),
            amount = stringResource(R.string.export_col_amount),
            category = stringResource(R.string.export_col_category),
            paidBy = stringResource(R.string.export_col_paid_by),
            mode = stringResource(R.string.export_col_mode),
            note = stringResource(R.string.export_col_note),
            visibility = stringResource(R.string.export_col_visibility),
            family = stringResource(R.string.export_family),
            onlyMe = stringResource(R.string.export_only_me),
        )
    val modes =
        mapOf(
            "CASH" to stringResource(R.string.export_mode_cash),
            "UPI" to stringResource(R.string.export_mode_upi),
            "CARD" to stringResource(R.string.export_mode_card),
            "OTHER" to stringResource(R.string.export_mode_other),
        )
    val title = stringResource(R.string.export_pdf_title)
    val rangeFmt = stringResource(R.string.export_pdf_range)
    val totalFmt = stringResource(R.string.export_pdf_total)
    val footer = stringResource(R.string.export_pdf_footer)
    val former = stringResource(R.string.export_former_member)
    val hindi = LocalConfiguration.current.locales[0].language == "hi"
    return ExportTexts(labels, former, modes, hindi) { from, to, count, total ->
        PdfTitle(title, rangeFmt.format(from, to), totalFmt.format(count, total), footer)
    }
}

private fun ExportRange.label(): Int =
    when (this) {
        ExportRange.THIS_MONTH -> R.string.export_range_this_month
        ExportRange.LAST_MONTH -> R.string.export_range_last_month
        ExportRange.LAST_3_MONTHS -> R.string.export_range_3_months
        ExportRange.THIS_YEAR -> R.string.export_range_this_year
        ExportRange.ALL -> R.string.export_range_all
    }

private fun share(
    context: Context,
    file: File,
    mime: String,
    title: String,
) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app can take the file; the user simply sees nothing happen, the file stays in the app's cache.
    }
}

private const val MAX_TYPED = 20
