package com.agniv.hisaab.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.NotifyType
import com.agniv.hisaab.R
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.MoreButton
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Board filter chips. */
enum class AlertFilter(
    val label: Int,
) {
    ALL(R.string.alerts_all),
    SMART(R.string.alerts_smart),
    BUDGET(R.string.alerts_budget),
    OTHER(R.string.alerts_other),
    ;

    fun keeps(kind: AlertKind): Boolean =
        when (this) {
            ALL -> true
            SMART -> kind.type == NotifyType.SMART
            BUDGET -> kind.type == NotifyType.BUDGET
            OTHER -> kind.type != NotifyType.SMART && kind.type != NotifyType.BUDGET
        }
}

/**
 * Board "Alerts" (new): every alert Kinly raised, newest first, in Today / Earlier, with All / Smart / Budget /
 * Other chips. Swipe left to dismiss (Undo in the snackbar; TalkBack gets a "Dismiss" action instead of the swipe).
 * Tapping an alert opens the screen it is about.
 */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onOpen: (AlertKind) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val entries by AlertLog.observe(context).collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(AlertFilter.ALL) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissed = stringResource(R.string.alerts_dismissed)
    val undo = stringResource(R.string.alerts_undo)
    val today = LocalDate.now()
    val shown = entries.orEmpty().filter { filter.keeps(it.kind) }
    val (todays, earlier) = shown.partition { it.day() == today }

    val onDismiss: (AlertEntry) -> Unit = { entry ->
        AlertLog.dismiss(context, entry.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(dismissed, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                AlertLog.restore(context, entry)
            }
        }
    }

    Scaffold(
        topBar = {
            KinlyTopBar(title = stringResource(R.string.alerts_title), onBack = onBack) {
                MoreButton(onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notify_settings_title)) },
                        onClick = {
                            menu = false
                            onSettings()
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                AlertFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(stringResource(f.label)) },
                        shape = CircleShape,
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                    )
                }
            }
            if (entries != null && shown.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = stringResource(R.string.alerts_empty_title),
                    body = stringResource(R.string.alerts_empty_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    section(R.string.alerts_today, todays, onOpen, onDismiss)
                    section(R.string.alerts_earlier, earlier, onOpen, onDismiss)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    list: List<AlertEntry>,
    onOpen: (AlertKind) -> Unit,
    onDismiss: (AlertEntry) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h$title") {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.m))
    }
    items(list, key = { it.id }) { entry -> SwipeAlert(entry, onOpen, onDismiss) }
}

@Composable
private fun SwipeAlert(
    entry: AlertEntry,
    onOpen: (AlertKind) -> Unit,
    onDismiss: (AlertEntry) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val label = stringResource(R.string.alerts_dismiss)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDismiss(entry) },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp))
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            }
        },
    ) {
        AlertCard(
            entry,
            Modifier
                .clickable { onOpen(entry.kind) }
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(label) {
                                onDismiss(entry)
                                true
                            },
                        )
                },
        )
    }
}

@Composable
private fun AlertCard(
    entry: AlertEntry,
    modifier: Modifier = Modifier,
) {
    val (icon, tint, container) = entry.kind.look()
    val danger = entry.kind == AlertKind.UNUSUAL || entry.kind == AlertKind.BUDGET_OVER
    // Soft grey text is under 4.5:1 on the pink card, so red alerts use the card's own dark red for everything.
    val soft = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    HCard(
        modifier.fillMaxWidth(),
        containerColor = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier
                .padding(Spacing.l)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            IconTile(icon, tint = tint, container = container, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        entry.timeLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = soft,
                    )
                }
                Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = soft)
            }
        }
    }
}

@Composable
private fun AlertKind.look(): Triple<ImageVector, Color, Color> {
    val gold = HisaabTheme.extendedColors
    val red = MaterialTheme.colorScheme.error
    val green = MaterialTheme.colorScheme.primary
    val blue = MaterialTheme.colorScheme.tertiary
    return when (this) {
        AlertKind.UNUSUAL -> Triple(Icons.Outlined.WarningAmber, red, red.copy(alpha = TILE_ALPHA))
        AlertKind.CATEGORY_JUMP -> Triple(Icons.Outlined.TrendingUp, gold.onChipWarn, gold.chipWarn)
        AlertKind.BUDGET_PACE -> Triple(Icons.Outlined.Schedule, gold.onChipWarn, gold.chipWarn)
        AlertKind.BUDGET_WARNING -> Triple(Icons.Outlined.AccountBalanceWallet, gold.onChipWarn, gold.chipWarn)
        AlertKind.BUDGET_OVER -> Triple(Icons.Outlined.AccountBalanceWallet, red, red.copy(alpha = TILE_ALPHA))
        AlertKind.REPORT -> Triple(Icons.Outlined.BarChart, blue, blue.copy(alpha = TILE_ALPHA))
        AlertKind.EMI -> Triple(Icons.Outlined.CalendarMonth, green, MaterialTheme.colorScheme.primaryContainer)
        AlertKind.UDHAAR -> Triple(Icons.Outlined.Handshake, green, MaterialTheme.colorScheme.primaryContainer)
        AlertKind.GOAL -> Triple(Icons.Outlined.TrackChanges, green, MaterialTheme.colorScheme.primaryContainer)
    }
}

private const val TILE_ALPHA = 0.14f

private fun AlertEntry.day(): LocalDate = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()

/** "10:24 AM" today, "Yesterday", otherwise the date. */
@Composable
private fun AlertEntry.timeLabel(): String {
    val locale = LocalConfiguration.current.locales[0]
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (time.toLocalDate()) {
        today -> time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        today.minusDays(1) -> stringResource(R.string.alerts_yesterday)
        else -> time.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
}
