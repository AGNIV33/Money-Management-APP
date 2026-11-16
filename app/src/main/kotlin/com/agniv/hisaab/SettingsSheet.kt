package com.agniv.hisaab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.agniv.hisaab.core.designsystem.theme.Spacing

/** App lock and language, shown inside the profile. The full Settings screen (export, delete account) comes in Phase 5. */
@Composable
fun SettingsSection(
    settings: AppSettings,
    lock: AppLock,
    onLanguage: (String) -> Unit,
) {
    var lockOn by remember { mutableStateOf(settings.appLock) }
    var lockMissing by remember { mutableStateOf(false) }
    var crashOn by remember { mutableStateOf(settings.crashReports) }
    val context = LocalContext.current.applicationContext
    val language =
        settings.language ?: if (java.util.Locale
                .getDefault()
                .language == "hi"
        ) {
            "hi"
        } else {
            "en"
        }

    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_crash), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_crash_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = crashOn,
            onCheckedChange = { want ->
                crashOn = want
                settings.crashReports = want
                if (want) CrashReporting.start(context, allowed = true) else CrashReporting.stop()
            },
        )
    }
    NotificationSettings(settings)
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        FilterChip(selected = language == "en", onClick = { if (language != "en") onLanguage("en") }, label = { Text("English") })
        FilterChip(selected = language == "hi", onClick = { if (language != "hi") onLanguage("hi") }, label = { Text("हिंदी") })
    }
}
