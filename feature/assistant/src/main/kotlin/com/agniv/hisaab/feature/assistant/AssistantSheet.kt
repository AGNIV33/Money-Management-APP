package com.agniv.hisaab.feature.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.theme.Spacing

private const val ORB_SIZE_DP = 140
private const val PULSE_SCALE = 1.12f
private const val PULSE_MS = 900

/**
 * Talk to Kinly (PRD F23). Asks for consent once, before anything leaves the phone, then asks for the
 * microphone when the user taps the orb. The conversation stops when the sheet closes or the app goes away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(
    consentGiven: Boolean,
    onConsent: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val stopOnDismiss by rememberUpdatedState(viewModel::stop)
    DisposableEffect(Unit) { onDispose { stopOnDismiss() } }
    // The microphone is never left open behind the user's back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }

    val askMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.start() else viewModel.onPermissionDenied()
        }
    val onOrb = {
        when (state.phase) {
            AssistantPhase.IDLE, AssistantPhase.ERROR ->
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.start()
                } else {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            else -> viewModel.stop()
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.assistant_title), style = MaterialTheme.typography.headlineSmall)
            if (consentGiven) {
                Talk(state, onOrb)
            } else {
                Consent(onAgree = onConsent, onCancel = onDismiss)
            }
        }
    }
}

/** The sparkle button in the Home header. */
@Composable
fun AssistantButton(onClick: () -> Unit) {
    val label = stringResource(R.string.assistant_open)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) { Text("✨") }
}

@Composable
private fun Talk(
    state: AssistantUiState,
    onOrb: () -> Unit,
) {
    val busy = state.phase != AssistantPhase.IDLE && state.phase != AssistantPhase.ERROR
    Orb(state.phase, onOrb)
    Text(
        stringResource(statusText(state.phase)),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    state.message?.let {
        Text(
            stringResource(messageText(it)),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (it ==
                    AssistantMessage.STOPPED_QUIET
                ) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            textAlign = TextAlign.Center,
        )
    }
    if (busy) {
        OutlinedButton(onClick = onOrb) { Text(stringResource(R.string.assistant_end)) }
    }
    Text(
        stringResource(R.string.assistant_tip),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = Spacing.l),
    )
}

@Composable
private fun Orb(
    phase: AssistantPhase,
    onClick: () -> Unit,
) {
    val color =
        when (phase) {
            AssistantPhase.ERROR -> MaterialTheme.colorScheme.error
            AssistantPhase.SPEAKING -> MaterialTheme.colorScheme.tertiary
            AssistantPhase.THINKING, AssistantPhase.CONNECTING -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        }
    val animate = phase == AssistantPhase.LISTENING || phase == AssistantPhase.SPEAKING || phase == AssistantPhase.THINKING
    val pulse =
        if (animate) {
            val transition = rememberInfiniteTransition(label = "orb")
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = PULSE_SCALE,
                animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
                label = "pulse",
            )
            value
        } else {
            1f
        }
    val description = stringResource(R.string.assistant_orb)
    Box(
        modifier =
            Modifier
                .size(ORB_SIZE_DP.dp)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ORB_SIZE_DP.dp).scale(pulse)) {
            drawCircle(color = color.copy(alpha = 0.25f))
            drawCircle(color = color, radius = size.minDimension / 3f)
        }
        TextButton(onClick = onClick, modifier = Modifier.size(ORB_SIZE_DP.dp)) {
            Text(
                if (phase == AssistantPhase.IDLE ||
                    phase == AssistantPhase.ERROR
                ) {
                    "🎙️"
                } else {
                    "✨"
                },
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
            )
        }
    }
}

@Composable
private fun Consent(
    onAgree: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(stringResource(R.string.assistant_consent_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.assistant_consent_voice), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.assistant_consent_totals), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.assistant_consent_nosave), style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(R.string.assistant_consent_free),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(R.string.assistant_consent_never), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.l)) {
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.assistant_consent_no)) }
        Button(onClick = onAgree) { Text(stringResource(R.string.assistant_consent_yes)) }
    }
}

private fun statusText(phase: AssistantPhase): Int =
    when (phase) {
        AssistantPhase.IDLE -> R.string.assistant_tap
        AssistantPhase.CONNECTING -> R.string.assistant_connecting
        AssistantPhase.LISTENING -> R.string.assistant_listening
        AssistantPhase.THINKING -> R.string.assistant_thinking
        AssistantPhase.SPEAKING -> R.string.assistant_speaking
        AssistantPhase.ERROR -> R.string.assistant_tap_again
    }

private fun messageText(message: AssistantMessage): Int =
    when (message) {
        AssistantMessage.NO_PERMISSION -> R.string.assistant_err_permission
        AssistantMessage.MIC_FAILED -> R.string.assistant_err_mic
        AssistantMessage.OFFLINE -> R.string.assistant_err_offline
        AssistantMessage.DAILY_LIMIT -> R.string.assistant_err_daily_limit
        AssistantMessage.BUSY_TODAY -> R.string.assistant_err_busy
        AssistantMessage.NOT_CONFIGURED -> R.string.assistant_err_not_configured
        AssistantMessage.NOT_SIGNED_IN -> R.string.assistant_err_signed_in
        AssistantMessage.FAILED -> R.string.assistant_err_failed
        AssistantMessage.SESSION_LOST -> R.string.assistant_err_lost
        AssistantMessage.STOPPED_QUIET -> R.string.assistant_stopped_quiet
    }
