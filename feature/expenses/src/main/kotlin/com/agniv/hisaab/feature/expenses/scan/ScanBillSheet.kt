package com.agniv.hisaab.feature.expenses.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.FieldBox
import com.agniv.hisaab.core.designsystem.component.HBusyButton
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlySelectField
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.motion.popIn
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Source
import com.agniv.hisaab.core.ml.receipt.ReceiptParser
import com.agniv.hisaab.core.ml.scan.TextScanner
import com.agniv.hisaab.core.ml.voice.VoiceExpense
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.DateDialog
import com.agniv.hisaab.feature.expenses.ui.iconVector
import com.agniv.hisaab.feature.expenses.ui.label
import com.agniv.hisaab.feature.expenses.voice.VoiceAddViewModel
import com.agniv.hisaab.feature.expenses.voice.VoiceMessage
import com.agniv.hisaab.feature.expenses.voice.textRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ScanPhase { CAMERA, CAPTURED, PROCESSING, DETECTED, NOT_READABLE }

/**
 * Board "Bill Scan": frame guide → photo preview (Retake / Process) → "Reading your bill…" → editable amount, date
 * and shop name → Save expense. The phone's own camera app takes the photo (no camera permission needed) and the
 * text is read on this phone; the picture is never sent anywhere.
 */
@Suppress("CyclomaticComplexMethod") // One branch per screen state.
@Composable
fun ScanBillScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    today: LocalDate = LocalDate.now(),
    viewModel: VoiceAddViewModel = hiltViewModel(key = "scan-bill"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by rememberSaveable { mutableStateOf(ScanPhase.CAMERA) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onSaved() } }

    fun process() {
        val uri = photo?.let(Uri::parse) ?: return
        phase = ScanPhase.PROCESSING
        scope.launch {
            val text = runCatching { TextScanner.read(context, uri) }.getOrNull()
            if (text.isNullOrBlank()) {
                viewModel.onFailure(VoiceMessage.NO_TEXT)
                phase = ScanPhase.NOT_READABLE
            } else {
                val scan = ReceiptParser.parse(text, today)
                viewModel.onParsed(
                    VoiceExpense(scan.amount, scan.date ?: today, scan.categoryName, null, scan.merchant, scan.confidence),
                    heard = text.lines().firstOrNull { it.isNotBlank() }.orEmpty(),
                    source = Source.SCAN,
                )
                phase = if (scan.amount == null) ScanPhase.NOT_READABLE else ScanPhase.DETECTED
            }
        }
    }

    val takePhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val path = photoPath
            if (ok && path != null) {
                photo = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path)).toString()
                phase = ScanPhase.CAPTURED
            }
        }
    val pickPicture =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                photo = uri.toString()
                phase = ScanPhase.CAPTURED
            }
        }
    val capture = {
        val file = newPhotoFile(context)
        photoPath = file.absolutePath
        takePhoto.launch(FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
    }
    val retake = {
        viewModel.reset()
        photo = null
        phase = ScanPhase.CAMERA
    }

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.scan_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (phase) {
                ScanPhase.CAMERA ->
                    Camera(onCapture = capture, onGallery = {
                        pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                ScanPhase.CAPTURED -> Captured(photo, onRetake = retake, onProcess = ::process)
                ScanPhase.PROCESSING -> Processing()
                ScanPhase.DETECTED -> Detected(photo, viewModel, today, onRetake = retake)
                ScanPhase.NOT_READABLE ->
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = stringResource(R.string.scan_unreadable_title),
                        body = stringResource(R.string.scan_unreadable_body),
                        action = stringResource(R.string.scan_retake),
                        actionIcon = Icons.Filled.CameraAlt,
                        onAction = retake,
                    )
            }
            state.message?.takeIf { phase == ScanPhase.DETECTED }?.let { InlineError(stringResource(it.textRes())) }
        }
    }
}

/** Dark frame with corner brackets and a shutter, like a camera view; the shutter opens the phone's camera. */
@Composable
private fun Camera(
    onCapture: () -> Unit,
    onGallery: () -> Unit,
) {
    val frame = Color.White
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(CAMERA_BG))
            .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(FRAME_RATIO),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val l = size.minDimension * BRACKET
                val w = 4.dp.toPx()
                val corners =
                    listOf(
                        Offset(0f, 0f) to Offset(1f, 1f),
                        Offset(size.width, 0f) to Offset(-1f, 1f),
                        Offset(0f, size.height) to Offset(1f, -1f),
                        Offset(size.width, size.height) to Offset(-1f, -1f),
                    )
                corners.forEach { (p, d) ->
                    drawLine(frame, p, Offset(p.x + d.x * l, p.y), w, StrokeCap.Round)
                    drawLine(frame, p, Offset(p.x, p.y + d.y * l), w, StrokeCap.Round)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(
                    Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = frame.copy(alpha = HINT_ALPHA),
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    stringResource(R.string.scan_frame_hint),
                    color = frame,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            Spacer(Modifier.weight(1f))
            val shutter = stringResource(R.string.scan_capture)
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, frame, CircleShape)
                    .padding(6.dp)
                    .background(frame, CircleShape)
                    .clickable(role = Role.Button, onClick = onCapture)
                    .semantics { contentDescription = shutter },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onGallery) {
                Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.scan_pick), tint = frame)
            }
        }
    }
    Text(
        stringResource(R.string.scan_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val CAMERA_BG = 0xFF1E2622
private const val FRAME_RATIO = 0.8f
private const val BRACKET = 0.14f
private const val HINT_ALPHA = 0.7f

@Composable
private fun Captured(
    photo: String?,
    onRetake: () -> Unit,
    onProcess: () -> Unit,
) {
    val image = rememberPreview(photo)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(FRAME_RATIO)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                image,
                contentDescription = stringResource(R.string.scan_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HOutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_retake)) }
        HButton(onClick = onProcess, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_process)) }
    }
}

@Composable
private fun Processing() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(
            Modifier
                .size(140.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        }
        Text(stringResource(R.string.scan_reading_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.scan_reading_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CircularProgressIndicator()
    }
}

@Composable
private fun Detected(
    photo: String?,
    viewModel: VoiceAddViewModel,
    today: LocalDate,
    onRetake: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val image = rememberPreview(photo)
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(
            stringResource(R.string.scan_detected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.popIn(),
        )
    }
    KinlyTextField(
        value = state.amountText,
        onValueChange = viewModel::editAmount,
        label = stringResource(R.string.add_amount),
        leadingIcon = Icons.Outlined.CurrencyRupee,
        trailingIcon = Icons.Filled.Edit,
        keyboardType = KeyboardType.Decimal,
        isError = state.amount == null,
    )
    val date = state.date ?: today
    FieldBox(
        value = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)),
        label = stringResource(R.string.add_date),
        leadingIcon = Icons.Outlined.CalendarToday,
        onClick = { pickingDate = true },
    )
    KinlyTextField(
        value = state.noteText,
        onValueChange = viewModel::editNote,
        label = stringResource(R.string.scan_shop),
        leadingIcon = Icons.Outlined.Storefront,
        trailingIcon = Icons.Filled.Edit,
    )
    KinlySelectField(
        value =
            state.categories
                .firstOrNull { it.id == state.categoryId }
                ?.label()
                .orEmpty(),
        options = state.categories,
        optionLabel = { it.label() },
        optionIcon = { it.iconVector() },
        onSelect = { viewModel.pickCategory(it.id) },
        label = stringResource(R.string.add_expense_category),
        leadingIcon = state.categories.firstOrNull { it.id == state.categoryId }?.iconVector(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HOutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_retake)) }
        HBusyButton(
            text = stringResource(R.string.scan_save),
            busyText = stringResource(R.string.add_saving),
            busy = state.saving,
            enabled = state.canSave,
            onClick = viewModel::save,
            modifier = Modifier.weight(1f),
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = date,
            today = today,
            onPick = {
                viewModel.pickDate(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The photo, shrunk to about screen width before decoding so a 12 MP picture does not fill a 2 GB phone's memory. */
@Composable
private fun rememberPreview(photo: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { withContext(Dispatchers.IO) { decodeSmall(context, Uri.parse(it)) } }
    }
    return image
}

private fun decodeSmall(
    context: Context,
    uri: Uri,
): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PREVIEW_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver
            .openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.asImageBitmap()
    }.getOrNull()

private const val PREVIEW_PX = 720

/** A fresh file in the app's cache for the camera to write into; old ones are cleared so the cache stays small. */
private fun newPhotoFile(context: Context): File {
    val dir = File(context.cacheDir, "scans").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    return File(dir, "bill-${System.currentTimeMillis()}.jpg")
}
