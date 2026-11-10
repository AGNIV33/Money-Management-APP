package com.agniv.hisaab.feature.expenses.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.ml.voice.Confidence
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.add.ExpensePrefill
import com.agniv.hisaab.feature.expenses.add.labelRes
import com.agniv.hisaab.feature.expenses.ui.emoji
import com.agniv.hisaab.feature.expenses.ui.label
import java.time.format.DateTimeFormatter

/**
 * Voice add (PRD F13): speak, see what was understood, then Save or Edit. AI suggests, the user confirms
 * (CLAUDE.md rule 4): nothing is saved until a button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAddSheet(
    onDismiss: () -> Unit,
    onEdit: (ExpensePrefill) -> Unit,
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
    DisposableEffect(Unit) { onDispose { controller.stop() } }
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onDismiss() } }

    fun listen() {
        if (!controller.isAvailable()) {
            viewModel.onFailure(VoiceMessage.UNAVAILABLE)
            return
        }
        viewModel.onListening()
        controller.start(state.language.tag)
    }

    // The microphone permission is asked only now, when the user taps the mic (CLAUDE.md: at use time).
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.headlineSmall)
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
            MicArea(state, onMic = onMic, onStop = { controller.stop().also { viewModel.reset() } })
            TypeBox(onRead = viewModel::onSentence)
            MessageText(state.message)
            ParsedPreview(state, viewModel, onEdit)
        }
    }
}

@Composable
private fun MicArea(
    state: VoiceUiState,
    onMic: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        FilledTonalButton(onClick = if (state.listening) onStop else onMic, modifier = Modifier.height(72.dp)) {
            Text(if (state.listening) "⏹" else "🎤", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            when {
                state.listening && state.heard.isNotEmpty() -> state.heard
                state.listening -> stringResource(R.string.voice_listening)
                state.result != null -> stringResource(R.string.voice_heard, state.heard)
                else -> stringResource(R.string.voice_hint)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

/** For phones without a microphone service (and the emulator): type the sentence and it is read the same way. */
@Composable
private fun TypeBox(onRead: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.voice_type_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = { if (text.isNotBlank()) onRead(text) }) { Text(stringResource(R.string.voice_read)) }
    }
}

@Composable
internal fun MessageText(message: VoiceMessage?) {
    val res =
        when (message) {
            null -> return
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
    Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ResultCard(
    state: VoiceUiState,
    viewModel: VoiceAddViewModel,
) {
    val r = state.result ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(r.amount?.formatted() ?: "—", style = DisplayAmount, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            val date = r.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", LocalConfiguration.current.locales[0]))
            Text(date + (r.mode?.let { " • " + stringResource(it.labelRes()) } ?: ""), style = MaterialTheme.typography.bodyLarge)
            r.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (r.confidence != Confidence.HIGH) {
                Text(
                    stringResource(R.string.voice_check),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(state.categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = c.id == state.categoryId,
                        onClick = { viewModel.pickCategory(c.id) },
                        label = { Text("${c.emoji()} ${c.label()}") },
                    )
                }
            }
        }
    }
}

/** What was understood, with Save and Edit. Shared by voice and bill scan. */
@Composable
internal fun ParsedPreview(
    state: VoiceUiState,
    viewModel: VoiceAddViewModel,
    onEdit: (ExpensePrefill) -> Unit,
) {
    state.result?.takeIf { state.message != VoiceMessage.NO_AMOUNT || it.amount != null } ?: return
    ResultCard(state, viewModel)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = viewModel::save,
            enabled = state.canSave && !state.saving,
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) { Text(stringResource(R.string.voice_save)) }
        OutlinedButton(
            onClick = { viewModel.prefill()?.let(onEdit) },
            modifier =
                Modifier
                    .weight(1f)
                    .height(56.dp),
        ) { Text(stringResource(R.string.voice_edit)) }
    }
}
