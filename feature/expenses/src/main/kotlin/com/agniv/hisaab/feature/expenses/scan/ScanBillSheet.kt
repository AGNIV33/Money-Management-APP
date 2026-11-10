package com.agniv.hisaab.feature.expenses.scan

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.ml.receipt.ReceiptParser
import com.agniv.hisaab.core.ml.scan.TextScanner
import com.agniv.hisaab.core.ml.voice.VoiceExpense
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.ExpensePrefill
import com.agniv.hisaab.feature.expenses.voice.MessageText
import com.agniv.hisaab.feature.expenses.voice.ParsedPreview
import com.agniv.hisaab.feature.expenses.voice.VoiceAddViewModel
import com.agniv.hisaab.feature.expenses.voice.VoiceMessage
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Bill scan (PRD F14): take a photo or pick a picture, read it on the phone, see what was found, then Save or Edit.
 * No camera permission is needed: the phone's own camera app takes the photo and hands it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBillSheet(
    onDismiss: () -> Unit,
    onEdit: (ExpensePrefill) -> Unit,
    today: LocalDate = LocalDate.now(),
    viewModel: VoiceAddViewModel = hiltViewModel(key = "scan-bill"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by rememberSaveable { mutableStateOf(false) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onDismiss() } }

    fun read(uri: Uri) {
        reading = true
        scope.launch {
            val text = runCatching { TextScanner.read(context, uri) }.getOrNull()
            reading = false
            if (text.isNullOrBlank()) {
                viewModel.onFailure(VoiceMessage.NO_TEXT)
            } else {
                val scan = ReceiptParser.parse(text, today)
                viewModel.onParsed(
                    VoiceExpense(scan.amount, scan.date ?: today, scan.categoryName, null, scan.merchant, scan.confidence),
                    heard = text.lines().firstOrNull { it.isNotBlank() }.orEmpty(),
                    source = Source.SCAN,
                )
            }
        }
    }

    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val path = photoPath
            if (ok && path != null) read(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path)))
        }
    val pickPicture =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::read) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        val file = newPhotoFile(context)
                        photoPath = file.absolutePath
                        takePhoto.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
                    },
                    enabled = !reading,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(64.dp),
                ) { Text("📷 " + stringResource(R.string.scan_take)) }
                FilledTonalButton(
                    onClick = { pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !reading,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(64.dp),
                ) { Text("🖼️ " + stringResource(R.string.scan_pick)) }
            }
            Text(
                stringResource(if (reading) R.string.scan_reading else R.string.scan_hint),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            MessageText(state.message)
            ParsedPreview(state, viewModel, onEdit)
        }
    }
}

/** A fresh file in the app's cache for the camera to write into; old ones are cleared so the cache stays small. */
private fun newPhotoFile(context: Context): File {
    val dir = File(context.cacheDir, "scans").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "bill-${System.currentTimeMillis()}.jpg")
}
