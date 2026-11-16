package com.agniv.hisaab.feature.expenses.statement

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HBusyButton
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.HProgressBar
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.ml.scan.PdfLockedException
import com.agniv.hisaab.core.ml.scan.PdfReader
import com.agniv.hisaab.core.ml.scan.TextScanner
import com.agniv.hisaab.core.ml.statement.MatchResult
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.ui.iconVector
import com.agniv.hisaab.feature.expenses.ui.label
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Board "Statement Import" (PRD F21): choose a bank statement or passbook (PDF or photos) → "Reading your file…" →
 * rows found, ticked for the user, possible duplicates marked → Import selected. Everything is read on the phone;
 * nothing is uploaded, and nothing is saved until "Import selected" is pressed.
 */
@Suppress("CyclomaticComplexMethod") // picture, PDF and camera launchers plus one branch per screen state
@Composable
fun StatementImportScreen(
    onBack: () -> Unit,
    viewModel: StatementImportViewModel = hiltViewModel(key = "statement-import"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var fileSize by rememberSaveable { mutableStateOf<String?>(null) }
    var lastUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    fun readImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        lastUris = uris.map(Uri::toString)
        fileName = context.resources.getQuantityString(R.plurals.stmt_pictures_count, uris.size, uris.size)
        fileSize = null
        viewModel.onReading()
        scope.launch {
            val text =
                try {
                    buildString { for (uri in uris) append(TextScanner.read(context, uri)).append(NEWLINE) }
                } catch (_: Exception) {
                    viewModel.onFailure(StatementMessage.READ_FAILED)
                    return@launch
                }
            viewModel.onText(text)
        }
    }

    fun readPdf(uri: Uri) {
        lastUris = listOf(uri.toString())
        val (name, size) = describe(context, uri)
        fileName = name
        fileSize = size
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

    fun tryAgain() {
        val uris = lastUris.map(Uri::parse)
        when {
            uris.isEmpty() -> viewModel.reset()
            fileSize != null -> readPdf(uris.first())
            else -> readImages(uris)
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
    val choosePdf = { pickPdf.launch(arrayOf("application/pdf")) }

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.stmt_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state.phase == StatementPhase.REVIEW) ReviewBar(state, onImport = viewModel::addSelected)
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = Spacing.screen),
        ) {
            val message = state.message
            when {
                state.phase == StatementPhase.READING -> Reading(state.progressFraction)
                state.phase == StatementPhase.REVIEW -> Review(state, fileName, fileSize, viewModel)
                state.phase == StatementPhase.DONE -> Done(state.addedCount, onBack)
                message == StatementMessage.NO_ROWS || message == StatementMessage.NO_TEXT ->
                    Centered {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.stmt_nothing_title),
                            body = stringResource(R.string.stmt_nothing_body),
                            action = stringResource(R.string.stmt_choose_another),
                            actionIcon = Icons.Outlined.FileUpload,
                            onAction = choosePdf,
                        )
                    }
                message == StatementMessage.READ_FAILED || message == StatementMessage.PDF_LOCKED ->
                    ReadFailed(
                        locked = message == StatementMessage.PDF_LOCKED,
                        onTryAgain = ::tryAgain,
                        onChooseAnother = choosePdf,
                    )
                else ->
                    NoFile(
                        onPdf = choosePdf,
                        onTake = {
                            val dir = File(context.cacheDir, "scans").apply { mkdirs() }
                            dir.listFiles()?.forEach { it.delete() }
                            val file = File(dir, "statement-${System.currentTimeMillis()}.jpg")
                            photoPath = file.absolutePath
                            takePhoto.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
                        },
                        onPictures = { pickPictures.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
            }
        }
    }
}

private const val MAX_PICTURES = 6
private const val NEWLINE = '\n'

/** The picked file's name and size ("2.4 MB"), for the file chip on the review screen. */
private fun describe(
    context: Context,
    uri: Uri,
): Pair<String?, String?> =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null to null
            val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
            val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let(c::getLong)
            name to size?.let { Formatter.formatShortFileSize(context, it) }
        } ?: (null to null)
    }.getOrDefault(null to null)

@Composable
private fun NoFile(
    onPdf: () -> Unit,
    onTake: () -> Unit,
    onPictures: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(
            Modifier
                .size(128.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Text(stringResource(R.string.stmt_import_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.stmt_import_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Bullet(Icons.Outlined.Description, stringResource(R.string.stmt_bullet_formats))
        Bullet(Icons.Outlined.FactCheck, stringResource(R.string.stmt_bullet_detect))
        Bullet(Icons.Outlined.PlaylistAddCheck, stringResource(R.string.stmt_bullet_review))
        HButton(onClick = onPdf, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.stmt_choose_file), modifier = Modifier.padding(start = Spacing.s))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            HOutlinedButton(onClick = onTake, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.scan_take), modifier = Modifier.padding(start = Spacing.s))
            }
            HOutlinedButton(onClick = onPictures, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.stmt_pictures), modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

@Composable
private fun Reading(fraction: Float?) {
    Centered {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(128.dp), strokeWidth = 6.dp)
                Icon(
                    Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
            Text(stringResource(R.string.stmt_reading_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.stmt_reading_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (fraction != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    HProgressBar(fraction, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                    Text("${(fraction * PERCENT).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.stmt_reading_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val PERCENT = 100

@Composable
private fun Review(
    state: StatementUiState,
    fileName: String?,
    fileSize: String?,
    viewModel: StatementImportViewModel,
) {
    val todo = state.rows.filter { it.actionable }
    val done = state.rows.filter { !it.actionable }
    val duplicates = todo.count { it.matched.result is MatchResult.Unsure }
    var showDone by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item(key = "file") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.s)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                IconTile(
                    Icons.Outlined.PictureAsPdf,
                    tint = MaterialTheme.colorScheme.error,
                    container = MaterialTheme.colorScheme.errorContainer,
                    size = 40.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        fileName ?: stringResource(R.string.stmt_title),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    fileSize?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item(key = "summary") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                    .padding(Spacing.m)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stmt_found, todo.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.stmt_selected_dupes, state.selectedCount, duplicates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (todo.isNotEmpty()) {
            item(key = "all") {
                val all = state.selectedCount == todo.size
                val some = state.selectedCount in 1 until todo.size
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Checkbox) { viewModel.selectAll(!all) }
                        .semantics(mergeDescendants = true) {
                            toggleableState =
                                when {
                                    all -> ToggleableState.On
                                    some -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = all, onCheckedChange = null)
                    Text(
                        stringResource(R.string.stmt_select_all, todo.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }
        items(todo, key = { "t${it.key}" }) { row ->
            TodoRow(row, state.categories, onToggle = { viewModel.toggle(row.key) }, onCategory = { viewModel.setCategory(row.key, it) })
        }
        if (done.isNotEmpty()) {
            item(key = "done-title") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showDone = !showDone },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.stmt_already_count, done.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showDone) {
                items(done, key = { "d${it.key}" }) { row ->
                    Row(Modifier.padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            lineWhat(row),
                            modifier = Modifier.weight(1f).padding(start = Spacing.s),
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
            item(key = "err") { InlineError(stringResource(R.string.error_save)) }
        }
    }
}

@Composable
private fun TodoRow(
    row: ReviewRow,
    categories: List<Category>,
    onToggle: () -> Unit,
    onCategory: (String) -> Unit,
) {
    val line = row.matched.line
    val result = row.matched.result
    val duplicate = result is MatchResult.Unsure
    val bg = if (duplicate) HisaabTheme.extendedColors.goldContainer else MaterialTheme.colorScheme.surface
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(14.dp))
            .padding(vertical = Spacing.s, horizontal = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    line.date.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(lineWhat(row), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(line.amount.formatted(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.s))
            if (result is MatchResult.EmiUnmarked) {
                Text(
                    stringResource(R.string.stmt_emi_chip),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier =
                        Modifier
                            .padding(end = Spacing.s)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = Spacing.s, vertical = 4.dp),
                )
            } else {
                CategoryPill(categories, row.categoryId, onCategory)
            }
        }
        if (duplicate) {
            Row(
                Modifier
                    .padding(start = 48.dp, bottom = Spacing.xs)
                    .background(HisaabTheme.extendedColors.chipWarn, CircleShape)
                    .padding(horizontal = Spacing.s, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = HisaabTheme.extendedColors.onChipWarn,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(R.string.stmt_possible_duplicate),
                    style = MaterialTheme.typography.labelSmall,
                    color = HisaabTheme.extendedColors.onChipWarn,
                )
            }
        }
        if (result is MatchResult.EmiUnmarked) {
            Text(
                stringResource(R.string.stmt_emi_mark, result.loan.lender),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, bottom = Spacing.xs),
            )
        }
    }
}

/** Small "🍴 Food ⌄" drop-down at the end of a row. */
@Composable
private fun CategoryPill(
    categories: List<Category>,
    selectedId: String?,
    onCategory: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val category = categories.firstOrNull { it.id == selectedId }
    Box {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            category?.let {
                Icon(it.iconVector(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Text(
                category?.label() ?: stringResource(R.string.stmt_pick_category),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label()) },
                    leadingIcon = { Icon(c.iconVector(), contentDescription = null) },
                    onClick = {
                        onCategory(c.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun lineWhat(row: ReviewRow): String =
    when (val r = row.matched.result) {
        is MatchResult.EmiMarked -> r.loan.lender + " EMI"
        else ->
            row.matched.line.description
                .take(DESC_SHOWN)
                .ifBlank { stringResource(R.string.stmt_payment) }
    }

private const val DESC_SHOWN = 40

/** Sticky bottom bar: "20 selected / Total: ₹28,430" and Import selected. */
@Composable
private fun ReviewBar(
    state: StatementUiState,
    onImport: () -> Unit,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider()
        Row(
            Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.stmt_n_selected, state.selectedCount), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.stmt_total, state.selectedTotal.formatted()), style = MaterialTheme.typography.bodyMedium)
            }
            HBusyButton(
                text = stringResource(R.string.stmt_import_selected),
                busyText = stringResource(R.string.add_saving),
                busy = state.saving,
                enabled = state.selectedCount > 0,
                onClick = onImport,
            )
        }
    }
}
