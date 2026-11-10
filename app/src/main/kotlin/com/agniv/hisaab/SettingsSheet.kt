package com.agniv.hisaab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.agniv.hisaab.core.designsystem.theme.Spacing

/** Small settings sheet: app lock and language. The full Settings screen (export, delete account) comes in Phase 5. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    lock: AppLock,
    onLanguage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var lockOn by remember { mutableStateOf(settings.appLock) }
    var lockMissing by remember { mutableStateOf(false) }
    val language =
        settings.language ?: if (java.util.Locale
                .getDefault()
                .language == "hi"
        ) {
            "hi"
        } else {
            "en"
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_lock), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_lock_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = lockOn,
                    onCheckedChange = { want ->
                        lockMissing = false
                        lock.setEnabled(want) { result ->
                            lockOn = result
                            lockMissing = want && !result && !lock.available()
                        }
                    },
                )
            }
            if (lockMissing) {
                Text(
                    stringResource(R.string.settings_lock_missing),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                FilterChip(selected = language == "en", onClick = { if (language != "en") onLanguage("en") }, label = { Text("English") })
                FilterChip(selected = language == "hi", onClick = { if (language != "hi") onLanguage("hi") }, label = { Text("हिंदी") })
            }
        }
    }
}

/** Shown instead of the app while it is locked. Amounts and names are not visible here. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
    }
}
