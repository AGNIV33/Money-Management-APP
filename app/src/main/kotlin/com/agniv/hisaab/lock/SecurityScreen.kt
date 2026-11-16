package com.agniv.hisaab.lock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.AppLock
import com.agniv.hisaab.AppSettings
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.component.GoldCard
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.family.profile.ProfileViewModel
import com.agniv.hisaab.feature.family.R as FamilyR

/** Board "Security": App Lock switch, Change PIN, Use Fingerprint, Forgot PIN, and what the lock is for. */
@Composable
fun SecurityScreen(
    lock: AppLock,
    settings: AppSettings,
    onBack: () -> Unit,
    onCreatePin: () -> Unit,
    onChangePin: () -> Unit,
    onForgot: () -> Unit,
) {
    // Re-read when coming back from the PIN flow.
    var lockOn by remember { mutableStateOf(settings.appLock) }
    var finger by remember { mutableStateOf(lock.pins.useFingerprint) }
    LaunchedEffect(Unit) { lockOn = settings.appLock }
    val hasPin = lock.usesPin
    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.security_title), onBack = onBack) },
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
            HCard(Modifier.fillMaxWidth()) {
                ToggleRow(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.settings_lock),
                    hint = stringResource(R.string.security_lock_hint),
                    on = lockOn,
                    onChange = { want ->
                        if (want) {
                            onCreatePin()
                        } else {
                            lock.disable()
                            lockOn = false
                        }
                    },
                )
                if (lockOn && hasPin) {
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                    LinkRow(Icons.Outlined.LockReset, stringResource(R.string.pin_change), onChangePin)
                    if (lock.fingerprintAvailable()) {
                        HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                        ToggleRow(
                            icon = Icons.Filled.Fingerprint,
                            title = stringResource(R.string.security_fingerprint),
                            hint = null,
                            on = finger,
                            onChange = {
                                lock.pins.useFingerprint = it
                                finger = it
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = Spacing.l))
                    LinkRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.pin_forgot), onForgot)
                }
            }
            val gold = HisaabTheme.extendedColors
            GoldCard {
                Row(
                    Modifier.padding(Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = gold.gold)
                    Text(stringResource(R.string.security_info), style = MaterialTheme.typography.bodyMedium, color = gold.onGoldContainer)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    hint: String?,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(icon, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(icon, size = 40.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/**
 * Forgot PIN → sign in again: removes the PIN, then the normal sign-out, which warns first when changes are not yet
 * synced (signing out then would lose them). After sign-out the app shows the login screen.
 */
@Composable
fun ResetBySignIn(
    lock: AppLock,
    localFamilyName: String,
    localMemberName: String,
    onCancel: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(key = "pin-reset"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.signOut(false, localFamilyName, localMemberName) { lock.disable() }
    }
    state.pendingOnSignOut?.let { count ->
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissPending()
                onCancel()
            },
            title = { Text(stringResource(FamilyR.string.profile_pending_title)) },
            text = { Text(stringResource(FamilyR.string.profile_pending_text, count)) },
            confirmButton = {
                TextButton(onClick = { viewModel.signOut(true, localFamilyName, localMemberName) { lock.disable() } }) {
                    Text(stringResource(FamilyR.string.profile_sign_out_anyway), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissPending()
                    onCancel()
                }) { Text(stringResource(FamilyR.string.profile_stay)) }
            },
        )
    }
}
