package com.agniv.hisaab.feature.udhaar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.InitialAvatar
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.PillTabs
import com.agniv.hisaab.core.designsystem.component.TintSummaryCard
import com.agniv.hisaab.core.designsystem.theme.CardAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.UdhaarItem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Board "Udhaar": I gave / I owe tabs, the open total, one row per person, + Add entry. Tap a row to settle or record
 * a part payment; swipe left to delete (asks first; creator or admin only).
 */
@Composable
fun UdhaarScreen(
    onBack: () -> Unit,
    viewModel: UdhaarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    // The boards have two tabs; "All" stays only in the old state for old saved screens.
    val tab = if (state.filter == UdhaarFilter.GIVE) 1 else 0
    val gave = tab == 0

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.udhaar_title), onBack = onBack) },
        bottomBar = {
            HButton(
                onClick = {
                    sheetCount++
                    sheetFor = ""
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(Spacing.screen),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.udhaar_add_entry), modifier = Modifier.padding(start = Spacing.s))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "tabs") {
                PillTabs(
                    options = listOf(stringResource(R.string.udhaar_tab_gave), stringResource(R.string.udhaar_tab_owe)),
                    selected = tab,
                    onSelect = { viewModel.onEvent(UdhaarEvent.SetFilter(if (it == 0) UdhaarFilter.GET else UdhaarFilter.GIVE)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "total") {
                val gold = HisaabTheme.extendedColors
                TintSummaryCard(
                    icon = if (gave) Icons.Outlined.Payments else Icons.Outlined.VolunteerActivism,
                    container = if (gave) MaterialTheme.colorScheme.primaryContainer else gold.goldContainer,
                    iconTint = if (gave) MaterialTheme.colorScheme.primary else gold.gold,
                ) {
                    val onCard = if (gave) MaterialTheme.colorScheme.onPrimaryContainer else gold.onGoldContainer
                    Text(
                        stringResource(if (gave) R.string.udhaar_total_receive else R.string.udhaar_total_pay),
                        style = MaterialTheme.typography.titleSmall,
                        color = onCard,
                    )
                    HAmountText(
                        if (gave) state.overview.youWillGet else state.overview.youWillGive,
                        style = CardAmount,
                        countUp = true,
                    )
                }
            }
            val shown = state.shown
            if (!state.loading && shown.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.VolunteerActivism,
                        title = stringResource(R.string.udhaar_empty_title),
                        body = stringResource(R.string.udhaar_empty_body),
                    )
                }
            }
            items(shown, key = { it.udhaar.id }) { item ->
                SwipeRow(
                    item = item,
                    today = state.today,
                    canDelete = state.canDelete(item.udhaar),
                    onOpen = { openId = item.udhaar.id },
                    onDelete = { deleteId = item.udhaar.id },
                )
            }
        }
    }

    state.overview.items.firstOrNull { it.udhaar.id == openId }?.let { open ->
        SettleFlow(
            item = open,
            onEvent = viewModel::onEvent,
            onEdit = {
                sheetCount++
                sheetFor = open.udhaar.id
                openId = null
            },
            onDismiss = { openId = null },
        )
    }
    sheetFor?.let { id ->
        AddUdhaarSheet(
            onDismiss = { sheetFor = null },
            editId = id.ifEmpty { null },
            instanceKey = "$sheetCount",
            direction = state.filter.direction,
        )
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.udhaar_delete_title)) },
            text = { Text(stringResource(R.string.udhaar_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(UdhaarEvent.Delete(id))
                    deleteId = null
                }) { Text(stringResource(R.string.udhaar_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SwipeRow(
    item: UdhaarItem,
    today: LocalDate,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    if (!canDelete) {
        UdhaarRow(item, today, Modifier.clickable(onClick = onOpen))
        return
    }
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.udhaar_delete)
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            // Snap back: the row only leaves once the user confirms.
            scope.launch { swipe.reset() }
        },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp))
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onError) }
        },
    ) {
        UdhaarRow(
            item,
            today,
            Modifier
                .clickable(onClick = onOpen)
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(label) {
                                onDelete()
                                true
                            },
                        )
                },
        )
    }
}

/** Avatar, name, note; amount and date on the right. Settled rows are dimmed with "✓ Settled • 14 Nov". */
@Composable
private fun UdhaarRow(
    item: UdhaarItem,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val e = item.udhaar
    val locale = LocalConfiguration.current.locales[0]
    val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    HCard(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .heightIn(min = 72.dp)
                .padding(Spacing.l)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            InitialAvatar(e.person, Modifier.alpha(if (item.settled) SETTLED_ALPHA else 1f))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(e.person, style = MaterialTheme.typography.titleSmall)
                    if (e.visibility == Visibility.PRIVATE) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.udhaar_private),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.settled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(
                                R.string.udhaar_settled_on,
                                (item.payments.lastOrNull()?.paidOn ?: e.date).format(DateTimeFormatter.ofPattern("d MMM", locale)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    e.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    DueLine(item, today)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                HAmountText(if (item.settled) e.amount else item.remaining, style = MaterialTheme.typography.titleSmall)
                Text(e.date.format(dateFmt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!item.settled && item.paid.paise > 0) {
                    Text(stringResource(R.string.udhaar_of, e.amount.formatted()), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private const val SETTLED_ALPHA = 0.6f

@Composable
private fun DueLine(
    item: UdhaarItem,
    today: LocalDate,
) {
    val due = item.udhaar.dueDate ?: return
    if (item.settled) return
    val locale = LocalConfiguration.current.locales[0]
    val days =
        java.time.temporal.ChronoUnit.DAYS
            .between(today, due)
    Text(
        if (item.overdue) {
            stringResource(R.string.udhaar_overdue, -days.toInt())
        } else {
            stringResource(R.string.udhaar_due_on, due.format(DateTimeFormatter.ofPattern("d MMM", locale)))
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (item.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
