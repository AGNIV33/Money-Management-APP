package com.agniv.hisaab.feature.udhaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.UdhaarItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Udhaar tab: what you will get, what you will give, and every entry (PRD F22). */
@Composable
fun UdhaarScreen(viewModel: UdhaarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    sheetCount++
                    sheetFor = ""
                },
                text = { Text(stringResource(R.string.udhaar_add_fab)) },
                icon = { Text("+", style = MaterialTheme.typography.headlineSmall) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "title") {
                Text(
                    stringResource(R.string.udhaar_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item(key = "totals") { Totals(state) }
            item(key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    UdhaarFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.onEvent(UdhaarEvent.SetFilter(f)) },
                            label = { Text(stringResource(f.label())) },
                        )
                    }
                }
            }
            if (!state.loading && state.shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.udhaar_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
            }
            items(state.shown, key = { it.udhaar.id }) { item -> UdhaarCard(item, state.today) { openId = item.udhaar.id } }
        }
    }

    val open = state.overview.items.firstOrNull { it.udhaar.id == openId }
    if (open != null) {
        UdhaarDetailSheet(
            item = open,
            canDelete = state.canDelete(open.udhaar),
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
        AddUdhaarSheet(onDismiss = {
            sheetFor = null
        }, editId = id.ifEmpty { null }, instanceKey = "$sheetCount", direction = state.filter.direction)
    }
}

private fun UdhaarFilter.label(): Int =
    when (this) {
        UdhaarFilter.ALL -> R.string.udhaar_filter_all
        UdhaarFilter.GET -> R.string.udhaar_filter_get
        UdhaarFilter.GIVE -> R.string.udhaar_filter_give
    }

@Composable
private fun Totals(state: UdhaarUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        TotalCard(R.string.udhaar_you_get, state.overview.youWillGet.formatted(), HisaabTheme.extendedColors.success, Modifier.weight(1f))
        TotalCard(R.string.udhaar_you_give, state.overview.youWillGive.formatted(), HisaabTheme.extendedColors.warning, Modifier.weight(1f))
    }
}

@Composable
private fun TotalCard(
    label: Int,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Text(amount, style = MaterialTheme.typography.headlineSmall, color = color)
        }
    }
}

@Composable
internal fun UdhaarCard(
    item: UdhaarItem,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val e = item.udhaar
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(Spacing.l), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    e.person + if (e.visibility == Visibility.PRIVATE) "  " + stringResource(R.string.udhaar_private) else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(if (e.direction == UdhaarDirection.THEY_OWE_ME) R.string.udhaar_dir_get else R.string.udhaar_dir_give),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DueLine(item, today)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                if (item.settled) {
                    Text(stringResource(R.string.udhaar_settled), color = HisaabTheme.extendedColors.success)
                    Text(e.amount.formatted(), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(item.remaining.formatted(), style = MaterialTheme.typography.titleMedium, color = directionColor(e.direction))
                    if (item.paid.paise > 0) {
                        Text(stringResource(R.string.udhaar_of, e.amount.formatted()), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DueLine(
    item: UdhaarItem,
    today: LocalDate,
) {
    val due = item.udhaar.dueDate
    if (item.settled || due == null) return
    val locale = LocalConfiguration.current.locales[0]
    val text = due.format(DateTimeFormatter.ofPattern("d MMM", locale))
    val days =
        java.time.temporal.ChronoUnit.DAYS
            .between(today, due)
    Text(
        if (item.overdue) stringResource(R.string.udhaar_overdue, -days.toInt()) else stringResource(R.string.udhaar_due_on, text),
        style = MaterialTheme.typography.bodySmall,
        color = if (item.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun directionColor(direction: UdhaarDirection) =
    if (direction == UdhaarDirection.THEY_OWE_ME) HisaabTheme.extendedColors.success else HisaabTheme.extendedColors.warning
