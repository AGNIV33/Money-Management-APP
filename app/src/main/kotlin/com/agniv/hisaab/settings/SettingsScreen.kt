package com.agniv.hisaab.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agniv.hisaab.AppSettings
import com.agniv.hisaab.BuildConfig
import com.agniv.hisaab.CrashReporting
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.family.profile.AvatarCircle
import com.agniv.hisaab.feature.family.profile.ExportDataDialog
import com.agniv.hisaab.feature.family.profile.ProfileViewModel
import com.agniv.hisaab.update.UpdateHost
import com.agniv.hisaab.update.UpdateViewModel
import com.agniv.hisaab.update.updateRowValue
import com.agniv.hisaab.feature.family.R as FamilyR

/** Where Settings rows lead; the nav host fills these in. */
class SettingsActions(
    val onProfile: () -> Unit,
    val onFamily: () -> Unit,
    val onSecurity: () -> Unit,
    val onNotifications: () -> Unit,
    val onPrivacy: () -> Unit,
    val onLanguage: (String) -> Unit,
    val onTheme: (String?) -> Unit,
)

/**
 * Board "Settings": profile tile, Family Members, Language, Theme, App Lock, Notifications, Send crash reports,
 * Export Data, About and Privacy, each a 56 dp row with its current value.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    avatar: String?,
    actions: SettingsActions,
    profile: ProfileViewModel = hiltViewModel(key = "settings"),
    updates: UpdateViewModel = viewModel(),
) {
    val state by profile.state.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    var showUpdate by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    var crashOn by remember { mutableStateOf(settings.crashReports) }
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    val language =
        settings.language ?: if (java.util.Locale
                .getDefault()
                .language == "hi"
        ) {
            "hi"
        } else {
            "en"
        }

    Column(Modifier.fillMaxSize()) {
        KinlyTopBar(title = stringResource(R.string.settings_title))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            HCard(Modifier.fillMaxWidth().clickable(onClick = actions.onProfile)) {
                Row(
                    Modifier
                        .padding(Spacing.l)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    AvatarCircle(state.me?.name, avatar, size = PROFILE_AVATAR)
                    Column(Modifier.weight(1f)) {
                        Text(state.me?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        state.email?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
            HCard(Modifier.fillMaxWidth()) {
                LinkRow(
                    Icons.Outlined.Groups,
                    stringResource(R.string.settings_family),
                    sub = stringResource(FamilyR.string.family_members, state.members.size),
                    onClick = actions.onFamily,
                )
                Divider()
                LinkRow(Icons.Outlined.Language, stringResource(R.string.settings_language), value = languageName(language)) {
                    picker = Picker.LANGUAGE
                }
                Divider()
                LinkRow(Icons.Outlined.Palette, stringResource(R.string.settings_theme), value = themeName(settings.theme)) {
                    picker = Picker.THEME
                }
                Divider()
                LinkRow(
                    Icons.Outlined.Lock,
                    stringResource(R.string.settings_lock),
                    value = stringResource(if (settings.appLock) R.string.settings_on else R.string.settings_off),
                    onClick = actions.onSecurity,
                )
                Divider()
                LinkRow(Icons.Outlined.NotificationsNone, stringResource(R.string.notify_title), onClick = actions.onNotifications)
                Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = crashOn, role = Role.Switch) { want ->
                            crashOn = want
                            settings.crashReports = want
                            if (want) CrashReporting.start(context, allowed = true) else CrashReporting.stop()
                        }.heightIn(min = 64.dp)
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    IconTile(Icons.Outlined.BugReport, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_crash), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.settings_crash_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = crashOn, onCheckedChange = null)
                }
                Divider()
                LinkRow(Icons.Outlined.FileUpload, stringResource(R.string.settings_export)) { showExport = true }
                Divider()
                LinkRow(Icons.Outlined.SystemUpdate, stringResource(R.string.update_title), value = updateRowValue(update)) {
                    updates.onRowTap()
                    showUpdate = true
                }
                Divider()
                LinkRow(Icons.Outlined.Info, stringResource(R.string.settings_about)) { picker = Picker.ABOUT }
                Divider()
                LinkRow(Icons.Outlined.Shield, stringResource(R.string.settings_privacy), onClick = actions.onPrivacy)
            }
        }
    }

    when (picker) {
        Picker.LANGUAGE ->
            ChoiceDialog(
                title = stringResource(R.string.settings_language),
                options = listOf("en", "hi"),
                selected = language,
                label = { languageName(it) },
                onPick = {
                    picker = null
                    if (it != language) actions.onLanguage(it)
                },
                onDismiss = { picker = null },
            )
        Picker.THEME ->
            ChoiceDialog(
                title = stringResource(R.string.settings_theme),
                options = listOf<String?>(null, "light", "dark"),
                selected = settings.theme,
                label = { themeName(it) },
                onPick = {
                    picker = null
                    actions.onTheme(it)
                },
                onDismiss = { picker = null },
            )
        Picker.ABOUT ->
            AlertDialog(
                onDismissRequest = { picker = null },
                title = { Text(stringResource(R.string.app_name)) },
                text = { Text(stringResource(R.string.settings_about_text, BuildConfig.VERSION_NAME)) },
                confirmButton = { TextButton(onClick = { picker = null }) { Text(stringResource(android.R.string.ok)) } },
            )
        null -> Unit
    }
    if (showExport) ExportDataDialog(onDismiss = { showExport = false })
    UpdateHost(updates, visible = showUpdate, onClose = { showUpdate = false })
}

private enum class Picker { LANGUAGE, THEME, ABOUT }

private const val PROFILE_AVATAR = 56

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = Spacing.l))

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    sub: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconTile(icon, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onPick(option) }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = Spacing.m))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
    )
}

@Composable
private fun languageName(code: String): String = if (code == "hi") "हिंदी" else "English"

@Composable
private fun themeName(theme: String?): String =
    stringResource(
        when (theme) {
            "light" -> R.string.settings_theme_light
            "dark" -> R.string.settings_theme_dark
            else -> R.string.settings_theme_system
        },
    )
