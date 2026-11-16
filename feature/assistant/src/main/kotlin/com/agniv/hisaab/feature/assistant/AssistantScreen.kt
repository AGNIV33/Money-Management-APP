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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.ErrorCard
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.MoreButton
import com.agniv.hisaab.core.designsystem.motion.PulseRings
import com.agniv.hisaab.core.designsystem.motion.rememberReducedMotion
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Board "AI Assistant": privacy banner, welcome with two suggestion chips, the conversation as bubbles, a typing
 * indicator, offline and error states, and the input bar with a mic (voice) and send (typing). Asks for consent
 * once before anything leaves the phone; the microphone is asked for only when the mic is tapped. Nothing said,
 * typed or heard is stored. The conversation stops when the user leaves the app.
 */
@Composable
fun AssistantScreen(
    consentGiven: Boolean,
    onConsent: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val stopOnLeave by rememberUpdatedState(viewModel::stop)
    DisposableEffect(Unit) { onDispose { stopOnLeave() } }
    // The microphone is never left open behind the user's back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stop() }
    var menu by remember { mutableStateOf(false) }

    val askMic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.start() else viewModel.onPermissionDenied()
        }
    val onMic = {
        if (state.micOn) {
            viewModel.stop()
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.start()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        KinlyTopBar(title = stringResource(R.string.assistant_title)) {
            MoreButton(onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.assistant_clear)) }, onClick = {
                    menu = false
                    viewModel.clearChat()
                })
            }
        }
        if (!consentGiven) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) { Consent(onAgree = onConsent, onCancel = onDismiss) }
            return@Column
        }
        PrivacyBanner()
        Box(Modifier.weight(1f)) {
            Conversation(state, onRetry = viewModel::retry)
        }
        Suggestions(onAsk = viewModel::send, enabled = state.phase != AssistantPhase.CONNECTING)
        InputBar(state, onSend = viewModel::send, onMic = onMic)
    }
}

@Composable
private fun PrivacyBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.s)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(R.string.assistant_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Suppress("CyclomaticComplexMethod") // One branch per board state: welcome, offline, error, typing, chat.
@Composable
private fun Conversation(
    state: AssistantUiState,
    onRetry: () -> Unit,
) {
    val offline = state.phase == AssistantPhase.ERROR && state.message == AssistantMessage.OFFLINE
    val failed = state.phase == AssistantPhase.ERROR && !offline
    if (state.chat.isEmpty() && !failed && !offline) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = stringResource(R.string.assistant_welcome_title),
                body = stringResource(state.message?.let { messageText(it) } ?: R.string.assistant_welcome_body),
            )
        }
        return
    }
    if (offline && state.chat.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                icon = Icons.Outlined.WifiOff,
                title = stringResource(R.string.assistant_offline_title),
                body = stringResource(R.string.assistant_offline_body),
                action = stringResource(R.string.assistant_try_again),
                actionIcon = Icons.Filled.Refresh,
                onAction = onRetry,
            )
        }
        return
    }
    val list = rememberLazyListState()
    val thinking = state.phase == AssistantPhase.THINKING || state.phase == AssistantPhase.CONNECTING
    LaunchedEffect(state.chat.size, thinking) { if (state.chat.isNotEmpty()) list.animateScrollToItem(state.chat.size) }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(state.chat, key = { it.id }) { Bubble(it) }
        item(key = "tail") {
            when {
                thinking -> Typing()
                offline -> OfflineCard(onRetry)
                failed ->
                    ErrorCard(
                        title = stringResource(R.string.assistant_error_title),
                        body = stringResource(state.message?.let { messageText(it) } ?: R.string.assistant_err_failed),
                        retry = stringResource(R.string.assistant_try_again),
                        onRetry = onRetry,
                    )
                state.message == AssistantMessage.STOPPED_QUIET ->
                    Text(
                        stringResource(R.string.assistant_stopped_quiet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                else -> Box(Modifier.size(1.dp))
            }
        }
    }
}

@Composable
private fun OfflineCard(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = HisaabTheme.extendedColors.gold, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.assistant_offline_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.assistant_offline_body), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        HButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.assistant_try_again), modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

/** User on the right in green tint; Kinly on the left in a card with a sparkle. Time under each. */
@Composable
private fun Bubble(message: ChatMessage) {
    val locale = LocalConfiguration.current.locales[0]
    val time =
        Instant
            .ofEpochMilli(message.at)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    val mine = message.fromUser
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = HisaabTheme.extendedColors.gold,
                modifier =
                    Modifier
                        .padding(end = Spacing.s, top = Spacing.xs)
                        .size(20.dp),
            )
        }
        Column(
            Modifier
                .widthIn(max = BUBBLE_MAX.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    ),
                ).padding(Spacing.m)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            if (message.voiceOnly) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.assistant_voice_only), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private const val BUBBLE_MAX = 280

/** Board typing indicator: three dots fading in turn. Still dots when animations are off. */
@Composable
private fun Typing() {
    val label = stringResource(R.string.assistant_thinking)
    val reduced = rememberReducedMotion()
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val t =
            if (reduced) {
                0f
            } else {
                val transition = rememberInfiniteTransition(label = "typing")
                val v by transition.animateFloat(
                    0f,
                    DOTS.toFloat(),
                    infiniteRepeatable(tween(TYPING_MS), RepeatMode.Restart),
                    label = "dot",
                )
                v
            }
        repeat(DOTS) { i ->
            val on = !reduced && t.toInt() == i
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(if (on || reduced) 1f else DIM)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
        }
    }
}

private const val DOTS = 3
private const val TYPING_MS = 900
private const val DIM = 0.35f

@Composable
private fun Suggestions(
    onAsk: (String) -> Unit,
    enabled: Boolean,
) {
    val budget = stringResource(R.string.assistant_suggest_budget)
    val most = stringResource(R.string.assistant_suggest_most)
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        listOf(Icons.Outlined.Savings to budget, Icons.Outlined.BarChart to most).forEach { (icon, text) ->
            AssistChip(
                onClick = { onAsk(text) },
                enabled = enabled,
                label = { Text(text) },
                leadingIcon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    state: AssistantUiState,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        onSend(text)
        text = ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        val listening = state.micOn
        val micLabel = stringResource(if (listening) R.string.assistant_mic_stop else R.string.assistant_mic_start)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
            if (listening) PulseRings(color = MaterialTheme.colorScheme.primary, size = 52.dp)
            IconButton(onClick = onMic, modifier = Modifier.semantics { contentDescription = micLabel }) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.assistant_ask_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) send() }),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = { send() },
            enabled = text.isNotBlank(),
            modifier = Modifier.size(52.dp),
        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.assistant_send)) }
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
        HOutlinedButton(onClick = onCancel) { Text(stringResource(R.string.assistant_consent_no)) }
        HButton(onClick = onAgree) { Text(stringResource(R.string.assistant_consent_yes)) }
    }
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
