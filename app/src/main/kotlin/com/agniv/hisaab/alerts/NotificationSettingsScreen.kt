package com.agniv.hisaab.alerts

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.agniv.hisaab.AppSettings
import com.agniv.hisaab.Notifications
import com.agniv.hisaab.NotifyType
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.SparkIllustration
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing

/** Board order of the alert types, each with its icon. */
private val TYPES: List<Pair<NotifyType, ImageVector>> =
    listOf(
        NotifyType.SMART to Icons.Outlined.NotificationsNone,
        NotifyType.BUDGET to Icons.Outlined.AccountBalanceWallet,
        NotifyType.EMI to Icons.Outlined.CalendarMonth,
        NotifyType.UDHAAR to Icons.Outlined.Handshake,
        NotifyType.GOALS to Icons.Outlined.TrackChanges,
        NotifyType.REPORT to Icons.Outlined.BarChart,
    )

/**
 * Board "Notification Settings": the Android permission card (asks once, then opens Kinly's page in phone settings,
 * since Android may not ask again) and one switch per alert type. A type switched off is neither notified nor kept
 * in the Alerts list.
 */
@Composable
fun NotificationSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
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
    val askPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !refusedOnce) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.notify_settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (permitted) PermissionCard(onAllow = { askPermission() }, permitted = true) else PermissionOff(onAllow = { askPermission() })
            Text(stringResource(R.string.notify_types), style = MaterialTheme.typography.titleMedium)
            HCard(Modifier.fillMaxWidth()) {
                TYPES.forEachIndexed { i, (type, icon) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                    val on = switches[type] == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = on, role = Role.Switch) { want ->
                                switches[type] = want
                                settings.setNotify(type, want)
                                if (want && !permitted) askPermission()
                            }.heightIn(min = 72.dp)
                            .padding(horizontal = Spacing.l, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        IconTile(icon, size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(type.label), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(type.hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = on, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onAllow: () -> Unit,
    permitted: Boolean,
) {
    val colors = HisaabTheme.extendedColors
    HCard(Modifier.fillMaxWidth(), containerColor = colors.goldContainer) {
        Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = colors.gold, modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.notify_allow), style = MaterialTheme.typography.titleSmall, color = colors.onGoldContainer)
                Text(
                    stringResource(if (permitted) R.string.notify_on else R.string.notify_card_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onGoldContainer,
                )
                if (!permitted) AllowButton(onAllow)
            }
        }
    }
}

/** Board "Permission Off": crossed bell, "Notifications are turned off.", Allow notifications. */
@Composable
private fun PermissionOff(onAllow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SparkIllustration(Icons.Outlined.NotificationsOff, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.notify_off_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.notify_off_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AllowButton(onAllow)
    }
}

@Composable
private fun AllowButton(onAllow: () -> Unit) {
    HButton(onClick = onAllow) {
        Text(stringResource(R.string.notify_allow))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier =
                Modifier
                    .padding(start = Spacing.s)
                    .size(18.dp),
        )
    }
}
