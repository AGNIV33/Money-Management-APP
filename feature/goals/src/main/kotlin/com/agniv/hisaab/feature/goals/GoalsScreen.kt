package com.agniv.hisaab.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.GoalItem
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Goals tab (PRD F19): every goal with a progress jar, what is saved and what to put aside each month. */
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
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
                text = { Text(stringResource(R.string.goals_add_fab)) },
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
                    stringResource(R.string.goals_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val overview = state.overview
            if (overview.items.isNotEmpty()) {
                item(key = "total") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.goals_saved_total), style = MaterialTheme.typography.labelLarge)
                            Text(
                                overview.totalSaved.formatted(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = HisaabTheme.extendedColors.success,
                            )
                            Text(
                                stringResource(R.string.goals_of_target, overview.totalTarget.formatted()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(overview.items, key = { it.goal.id }) { item -> GoalCard(item) { openId = item.goal.id } }
            } else if (!state.loading) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.goals_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.xl),
                    )
                }
            }
        }
    }

    val open = state.overview.items.firstOrNull { it.goal.id == openId }
    if (open != null) {
        GoalDetailSheet(
            item = open,
            canDelete = state.canDelete(open.goal),
            onEvent = viewModel::onEvent,
            onEdit = {
                sheetCount++
                sheetFor = open.goal.id
                openId = null
            },
            onDismiss = { openId = null },
        )
    }
    sheetFor?.let { id -> AddGoalSheet(onDismiss = { sheetFor = null }, editId = id.ifEmpty { null }, instanceKey = "$sheetCount") }
}

/** The jar: fills from the bottom up in green. Words as well as the bar (DESIGN.md: never colour alone). */
@Composable
internal fun GoalJar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(12.dp)
                .background(HisaabTheme.extendedColors.success, shape),
        )
    }
}

@Composable
private fun GoalCard(
    item: GoalItem,
    onClick: () -> Unit,
) {
    val g = item.goal
    val locale = LocalConfiguration.current.locales[0]
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.reached) "🎉" else "🫙", style = MaterialTheme.typography.headlineSmall)
                Text(
                    g.name + if (g.visibility == Visibility.PRIVATE) "  " + stringResource(R.string.goals_private) else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = Spacing.m),
                )
                Text(
                    stringResource(R.string.goals_percent, (item.fraction * PERCENT).roundToInt()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            GoalJar(item.fraction)
            Row {
                Text(
                    stringResource(R.string.goals_saved_of, item.saved.formatted(), g.target.formatted()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (item.reached) {
                    Text(
                        stringResource(R.string.goals_reached),
                        color = HisaabTheme.extendedColors.success,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    g.deadline?.let {
                        Text(
                            stringResource(R.string.goals_by, it.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item.perMonthNeeded?.let {
                Text(
                    stringResource(R.string.goals_per_month, it.formatted()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private const val PERCENT = 100
