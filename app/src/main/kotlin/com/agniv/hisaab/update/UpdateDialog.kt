package com.agniv.hisaab.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.BuildConfig
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.theme.Spacing

/** The short value shown on the Settings row. */
@Composable
fun updateRowValue(state: UpdateState): String =
    when (state) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        is UpdateState.Available -> stringResource(R.string.update_new_version, state.info.versionName)
        is UpdateState.Downloading ->
            if (state.percent >= 0) "${state.percent}%" else stringResource(R.string.update_downloading_short)
        else -> "v${BuildConfig.VERSION_NAME}"
    }

/**
 * Checks quietly when Settings opens, carries on with the install after the user comes back from Android's
 * "Allow from this source" screen, and shows the dialog while [visible] (or while that permission is needed).
 */
@Composable
fun UpdateHost(
    updates: UpdateViewModel,
    visible: Boolean,
    onClose: () -> Unit,
) {
    val state by updates.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updates.checkQuietly() }
    LifecycleResumeEffect(Unit) {
        updates.retryInstall()
        onPauseOrDispose {}
    }
    if (visible || state is UpdateState.NeedsPermission) {
        UpdateDialog(
            state = state,
            onDownload = updates::download,
            onAllow = { updates.openPermission() },
            onRetry = updates::check,
            onDismiss = {
                onClose()
                updates.dismiss()
            },
        )
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    onDownload: (UpdateInfo) -> Unit,
    onAllow: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val close = @Composable { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } }
    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_title)) },
                text = { Progress(stringResource(R.string.update_checking), percent = -1) },
                confirmButton = close,
            )
        UpdateState.UpToDate ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_title)) },
                text = { Text(stringResource(R.string.update_latest, BuildConfig.VERSION_NAME)) },
                confirmButton = close,
            )
        UpdateState.Failed ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_title)) },
                text = { Text(stringResource(R.string.update_failed)) },
                confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.update_try_again)) } },
                dismissButton = close,
            )
        is UpdateState.Available ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_available, state.info.versionName)) },
                text = {
                    Column(
                        Modifier.heightIn(max = NOTES_HEIGHT.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(stringResource(R.string.update_explain, BuildConfig.VERSION_NAME))
                        if (state.info.notes.isNotBlank()) {
                            Text(
                                state.info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onDownload(state.info) }) { Text(stringResource(R.string.update_now)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
            )
        is UpdateState.Downloading ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_available, state.info.versionName)) },
                text = { Progress(stringResource(R.string.update_downloading), state.percent) },
                confirmButton = {},
            )
        is UpdateState.NeedsPermission ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.update_permission_title)) },
                text = { Text(stringResource(R.string.update_permission_text)) },
                confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.update_permission_open)) } },
                dismissButton = close,
            )
    }
}

@Composable
private fun Progress(
    label: String,
    percent: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(label)
        if (percent in 0..FULL) {
            LinearProgressIndicator(progress = { percent / FULL.toFloat() }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

private const val FULL = 100
private const val NOTES_HEIGHT = 280
