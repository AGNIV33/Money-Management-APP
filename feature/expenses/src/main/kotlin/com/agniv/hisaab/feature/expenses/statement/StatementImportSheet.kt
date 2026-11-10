package com.agniv.hisaab.feature.expenses.statement

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.ml.scan.PdfLockedException
import com.agniv.hisaab.core.ml.scan.PdfReader
import com.agniv.hisaab.core.ml.scan.TextScanner
import com.agniv.hisaab.core.ml.statement.MatchResult
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Import a passbook or bank statement from photos or a PDF (PRD F21). It shows which payments are already in
 * Kinly and which are missing; you tick what to add. Read on the phone: nothing is uploaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod") // picture, PDF and camera launchers plus four steps in one sheet
@Composable
fun StatementImportSheet(
    onDismiss: () -> Unit,
    viewModel: StatementImportViewModel = hiltViewModel(key = "statement-import"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun readImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModel.onReading()
        scope.launch {
            val text =
                try {
                    buildString {
                        for (uri in uris) append(TextScanner.read(context, uri)).append(NEWLINE)
                    }
                } catch (_: Exception) {
                    viewModel.onFailure(StatementMessage.READ_FAILED)
                    return@launch
                }
            viewModel.onText(text)
        }
    }

    fun readPdf(uri: Uri) {
        viewModel.onReading()
        scope.launch {
            val text =
                try {
                    PdfReader.read(context, uri) { page, total -> viewModel.onReading("$page / $total") }
                } catch (_: PdfLockedException) {
                    viewModel.onFailure(StatementMessage.PDF_LOCKED)
                    return@launch
                } catch (_: Exception) {
                    viewModel.onFailure(StatementMessage.READ_FAILED)
                    return@launch
                }
            viewModel.onText(text)
        }
    }

    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val path = photoPath
            if (ok &&
                path != null
            ) {
                readImages(listOf(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))))
            }
        }
    val pickPictures = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICTURES)) { readImages(it) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::readPdf) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.stmt_title), style = MaterialTheme.typography.headlineSmall)
            when (state.phase) {
                StatementPhase.PICK ->
                    PickStep(
                        state,
                        onTake = {
                            val dir = File(context.cacheDir, "scans").apply { mkdirs() }
                            dir.listFiles()?.forEach { it.delete() }
                            val file = File(dir, "statement-${System.currentTimeMillis()}.jpg")
                            photoPath = file.absolutePath
                            takePhoto.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
                        },
                        onPictures = { pickPictures.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                    )
                StatementPhase.READING ->
                    Text(
                        stringResource(R.string.stmt_reading) + (state.progress?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                StatementPhase.REVIEW -> ReviewStep(state, viewModel)
                StatementPhase.DONE -> {
                    Text(stringResource(R.string.stmt_done, state.addedCount), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

private const val MAX_PICTURES = 6
private const val NEWLINE = '\n'

@Composable
private fun PickStep(
    state: StatementUiState,
    onTake: () -> Unit,
    onPictures: () -> Unit,
    onPdf: () -> Unit,
) {
    Text(stringResource(R.string.stmt_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = onTake,
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text("📷 " + stringResource(R.string.scan_take)) }
        FilledTonalButton(onClick = onPictures, modifier = Modifier.weight(1f).height(56.dp)) {
            Text(
                "🖼️ " + stringResource(R.string.stmt_pictures),
            )
        }
    }
    FilledTonalButton(onClick = onPdf, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("📄 " + stringResource(R.string.stmt_pdf)) }
    state.message?.let { m ->
        val res =
            when (m) {
                StatementMessage.NO_TEXT -> R.string.scan_err_no_text
                StatementMessage.NO_ROWS -> R.string.stmt_err_no_rows
                StatementMessage.PDF_LOCKED -> R.string.stmt_err_locked
                StatementMessage.READ_FAILED -> R.string.stmt_err_read
                StatementMessage.SAVE_FAILED -> R.string.error_save
            }
        Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewStep(
    state: StatementUiState,
    viewModel: StatementImportViewModel,
) {
    val s = state.summary
    if (s != null) {
        Text(
            stringResource(R.string.stmt_summary, s.added, s.missing + s.emiToMark, s.unsure),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    val todo = state.rows.filter { it.actionable }
    val done = state.rows.filter { !it.actionable }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        if (todo.isNotEmpty()) {
            item(key = "todo-title") { Text(stringResource(R.string.stmt_to_add), style = MaterialTheme.typography.titleMedium) }
            items(todo, key = { "t${it.key}" }) { row ->
                TodoRow(
                    row,
                    state.categories,
                    onToggle = { viewModel.toggle(row.key) },
                    onCategory = { viewModel.setCategory(row.key, it) },
                )
                HorizontalDivider()
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-title") {
                Text(
                    stringResource(R.string.stmt_already),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
            items(done, key = { "d${it.key}" }) { row ->
                Row(Modifier.padding(vertical = Spacing.xs)) {
                    Text("✅ " + lineTitle(row), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        row.matched.line.amount
                            .formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    if (state.message == StatementMessage.SAVE_FAILED) {
        Text(stringResource(R.string.error_save), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = viewModel::reset,
            modifier = Modifier.weight(1f).height(52.dp),
        ) { Text(stringResource(R.string.stmt_again)) }
        Button(
            onClick = viewModel::addSelected,
            enabled = state.selectedCount > 0 && !state.saving,
            modifier = Modifier.weight(1f).height(52.dp),
        ) { Text(stringResource(R.string.stmt_add_selected, state.selectedCount)) }
    }
}

@Composable
private fun lineTitle(row: ReviewRow): String {
    val line = row.matched.line
    val date = line.date.format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]))
    val what =
        when (val r = row.matched.result) {
            is MatchResult.EmiMarked -> r.loan.lender + " EMI"
            else -> line.description.take(DESC_SHOWN).ifBlank { stringResource(R.string.stmt_payment) }
        }
    return "$date • $what"
}

private const val DESC_SHOWN = 40

@Composable
private fun TodoRow(
    row: ReviewRow,
    categories: List<Category>,
    onToggle: () -> Unit,
    onCategory: (String) -> Unit,
) {
    val line = row.matched.line
    var menu by remember { mutableStateOf(false) }
    val result = row.matched.result
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(lineTitle(row), style = MaterialTheme.typography.bodyLarge)
            when (result) {
                is MatchResult.EmiUnmarked ->
                    Text(
                        stringResource(R.string.stmt_emi_mark, result.loan.lender),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HisaabTheme.extendedColors.warning,
                    )
                is MatchResult.Unsure ->
                    Text(
                        stringResource(R.string.stmt_unsure),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HisaabTheme.extendedColors.warning,
                    )
                else -> Unit
            }
            if (result !is MatchResult.EmiUnmarked) {
                val category = categories.firstOrNull { it.id == row.categoryId }
                TextButton(onClick = { menu = true }) {
                    Text(
                        category?.let { "${it.emoji()} ${it.label()}" } ?: stringResource(R.string.stmt_pick_category),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.emoji()} ${c.label()}") },
                            onClick = {
                                onCategory(c.id)
                                menu = false
                            },
                        )
                    }
                }
            }
        }
        Text(line.amount.formatted(), style = MaterialTheme.typography.titleMedium)
    }
}
