package com.agniv.hisaab.feature.expenses.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlySelectField
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.motion.PulseRings
import com.agniv.hisaab.core.designsystem.motion.popIn
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.ExpensePrefill
import com.agniv.hisaab.feature.expenses.ui.iconVector
import com.agniv.hisaab.feature.expenses.ui.label

/**
 * Board "Voice Entry" bottom sheet: idle mic → listening with rings → live words → "Here's what I understood" with
 * amount, category and note the user can fix → Confirm fills the Add Expense form. Nothing is saved here
 * (AI suggests, user confirms). The microphone is asked for only when the mic is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod") // One branch per screen state.
@Composable
fun VoiceAddSheet(
    onDismiss: () -> Unit,
    onConfirm: (ExpensePrefill) -> Unit,
    viewModel: VoiceAddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val controller =
        remember {
            SpeechController(
                context,
                onPartial = viewModel::onPartial,
                onFinal = viewModel::onSentence,
                onFailure = {
                    viewModel.onFailure(
                        when (it) {
                            SpeechFailure.NO_SPEECH -> VoiceMessage.NO_SPEECH
                            SpeechFailure.NETWORK -> VoiceMessage.NETWORK
                            SpeechFailure.BUSY -> VoiceMessage.BUSY
                            SpeechFailure.OTHER -> VoiceMessage.OTHER
                        },
                    )
                },
            )
        }
    DisposableEffect(Unit) {
        onDispose {
            controller.stop()
            // Next time the sheet opens it starts fresh (also after the user allowed the mic in Settings).
            viewModel.reset()
        }
    }

    fun listen() {
        if (!controller.isAvailable()) {
            viewModel.onFailure(VoiceMessage.UNAVAILABLE)
            return
        }
        viewModel.onListening()
        controller.start(state.language.tag)
    }

    val askMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) listen() else viewModel.onFailure(VoiceMessage.NO_PERMISSION)
        }
    val onMic = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Header(onClose = onDismiss)
            when {
                state.message == VoiceMessage.NO_PERMISSION ->
                    NoPermission(onAllow = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    })
                state.result != null && !state.listening -> Parsed(state, viewModel, onRetry = viewModel::reset, onConfirm = onConfirm)
                state.listening ->
                    Listening(state.heard, onStop = {
                        controller.stop()
                        // Stopping early still reads what was heard so far (board: "Tap stop → show parsed result").
                        if (state.heard.isNotBlank()) viewModel.onSentence(state.heard) else viewModel.reset()
                    })
                else -> Idle(state, viewModel, onMic = onMic)
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.voice_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.s),
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.voice_close))
        }
    }
}

@Composable
private fun Idle(
    state: VoiceUiState,
    viewModel: VoiceAddViewModel,
    onMic: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            stringResource(R.string.voice_idle_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilterChip(
                selected = state.language == VoiceLanguage.ENGLISH,
                onClick = { viewModel.setLanguage(VoiceLanguage.ENGLISH) },
                label = { Text("English") },
            )
            FilterChip(
                selected = state.language == VoiceLanguage.HINDI,
                onClick = { viewModel.setLanguage(VoiceLanguage.HINDI) },
                label = { Text("हिंदी") },
            )
        }
        MicButton(listening = false, onClick = onMic)
        ExampleChip(stringResource(R.string.voice_example))
        state.message?.let { InlineError(stringResource(it.textRes())) }
        // For phones without a speech service (and the emulator): type the sentence and it is read the same way.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            KinlyTextField(
                value = typed,
                onValueChange = { typed = it },
                placeholder = stringResource(R.string.voice_type_label),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (typed.isNotBlank()) viewModel.onSentence(typed) }) { Text(stringResource(R.string.voice_read)) }
        }
    }
}

@Composable
private fun Listening(
    heard: String,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (heard.isEmpty()) {
            Text(
                stringResource(R.string.voice_listening),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            MicButton(listening = true, onClick = onStop)
            ExampleChip(stringResource(R.string.voice_example))
        } else {
            // Live transcript, then a big stop button.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(Spacing.m)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("\"$heard\"", style = MaterialTheme.typography.titleMedium)
            }
            val stop = stringResource(R.string.voice_stop)
            Box(
                Modifier
                    .padding(top = Spacing.xl)
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(role = Role.Button, onClick = onStop)
                    .semantics { contentDescription = stop },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(stop, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Big green mic; soft rings spread from it while listening. */
@Composable
private fun MicButton(
    listening: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(if (listening) R.string.voice_stop else R.string.voice_start)
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (listening) PulseRings(color = MaterialTheme.colorScheme.primary)
        Box(
            Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun ExampleChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}

@Composable
private fun Parsed(
    state: VoiceUiState,
    viewModel: VoiceAddViewModel,
    onRetry: () -> Unit,
    onConfirm: (ExpensePrefill) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                .padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.popIn())
            Text(
                stringResource(R.string.voice_understood),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        KinlyTextField(
            value = state.amountText,
            onValueChange = viewModel::editAmount,
            label = stringResource(R.string.add_amount),
            leadingIcon = Icons.Filled.CurrencyRupee,
            trailingIcon = Icons.Filled.Edit,
            keyboardType = KeyboardType.Decimal,
            isError = state.amount == null,
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
        KinlyTextField(
            value = state.noteText,
            onValueChange = viewModel::editNote,
            label = stringResource(R.string.voice_note),
            leadingIcon = Icons.AutoMirrored.Outlined.Notes,
            trailingIcon = Icons.Filled.Edit,
        )
        state.message?.let { InlineError(stringResource(it.textRes())) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            HOutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.voice_retry), modifier = Modifier.padding(start = Spacing.s))
            }
            HButton(
                onClick = { viewModel.prefill()?.let(onConfirm) },
                enabled = state.canSave,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.voice_confirm)) }
        }
    }
}

@Composable
private fun NoPermission(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
        }
        Text(stringResource(R.string.voice_perm_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.voice_perm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        HButton(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.voice_perm_allow), modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

internal fun VoiceMessage.textRes(): Int =
    when (this) {
        VoiceMessage.NO_TEXT -> R.string.scan_err_no_text
        VoiceMessage.NO_SPEECH -> R.string.voice_err_no_speech
        VoiceMessage.NETWORK -> R.string.voice_err_network
        VoiceMessage.BUSY -> R.string.voice_err_busy
        VoiceMessage.OTHER -> R.string.voice_err_other
        VoiceMessage.UNAVAILABLE -> R.string.voice_err_unavailable
        VoiceMessage.NO_PERMISSION -> R.string.voice_err_permission
        VoiceMessage.NO_AMOUNT -> R.string.voice_err_no_amount
        VoiceMessage.SAVE_FAILED -> R.string.error_save
    }
