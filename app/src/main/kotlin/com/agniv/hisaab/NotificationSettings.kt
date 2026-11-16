package com.agniv.hisaab

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * One switch per kind of notification, plus the Android 13+ permission.
 * Turning a switch on when the phone has notifications blocked asks for the permission; after one refusal
 * (Android may not show the prompt again) the button opens Kinly's page in the phone's settings instead.
 */
@Composable
fun NotificationSettings(settings: AppSettings) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(Notifications.permitted(context)) }
    var refusedOnce by remember { mutableStateOf(false) }
    val switches =
        remember { mutableStateMapOf<NotifyType, Boolean>().apply { NotifyType.entries.forEach { put(it, settings.notifyOn(it)) } } }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permitted = granted
            if (!granted) refusedOnce = true
        }
    // Back from the phone's settings page: show the real state.
    LifecycleResumeEffect(Unit) {
        permitted = Notifications.permitted(context)
        onPauseOrDispose {}
    }
    val openPhoneSettings = {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
    val askPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !refusedOnce) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openPhoneSettings()
        }
    }

    Text(stringResource(R.string.notify_title), style = MaterialTheme.typography.titleMedium)
    if (!permitted) {
        Text(
            stringResource(R.string.notify_blocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { askPermission() }) { Text(stringResource(R.string.notify_allow)) }
    }
    NotifyType.entries.forEach { type ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(type.label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(type.hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = switches[type] == true,
                onCheckedChange = { on ->
                    switches[type] = on
                    settings.setNotify(type, on)
                    if (on && !permitted) askPermission()
                },
            )
        }
    }
}
