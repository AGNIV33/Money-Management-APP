package com.agniv.hisaab.feature.goals

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.MoreButton
import com.agniv.hisaab.core.designsystem.component.SavingsJar
import com.agniv.hisaab.core.designsystem.motion.popIn
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.Visibility
import com.agniv.hisaab.core.domain.usecase.GoalItem
import java.time.format.DateTimeFormatter

/** Board "Goals" tab: one card per goal with its savings jar, what is saved, the date and the monthly amount needed. */
@Composable
fun GoalsScreen(viewModel: GoalsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var addMoneyFor by rememberSaveable { mutableStateOf<String?>(null) }
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }
    val newGoal = {
        sheetCount++
        sheetFor = ""
    }
    val overview = state.overview

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.goals_title)) },
        floatingActionButton = {
            if (overview.items.isNotEmpty()) {
                FloatingActionButton(
                    onClick = newGoal,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.goals_create)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (overview.items.isNotEmpty()) {
                items(overview.items, key = { it.goal.id }) { item ->
                    GoalCard(
                        item,
                        onClick = { openId = item.goal.id },
                        onAddMoney = { addMoneyFor = item.goal.id },
                        onEdit = {
                            sheetCount++
                            sheetFor = item.goal.id
                        },
                    )
                }
            } else if (!state.loading) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Savings,
                        title = stringResource(R.string.goals_empty_title),
                        body = stringResource(R.string.goals_empty_body),
                        action = stringResource(R.string.goals_create),
                        onAction = newGoal,
                    )
                }
            }
        }
    }

    overview.items.firstOrNull { it.goal.id == openId }?.let { open ->
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
    overview.items.firstOrNull { it.goal.id == addMoneyFor }?.let { goal ->
        AddMoneySheet(
            item = goal,
            onAdd = {
                viewModel.onEvent(GoalsEvent.Add(goal.goal.id, it))
                addMoneyFor = null
            },
            onDismiss = { addMoneyFor = null },
        )
    }
    // Android 13+ needs the user's OK before goal reminders can show; asked right after a goal is saved.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    sheetFor?.let { id ->
        AddGoalSheet(
            onDismiss = { sheetFor = null },
            editId = id.ifEmpty { null },
            instanceKey = "$sheetCount",
            onSaved = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
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
    onAddMoney: () -> Unit,
    onEdit: () -> Unit,
) {
    val g = item.goal
    val locale = LocalConfiguration.current.locales[0]
    var menu by remember { mutableStateOf(false) }
    HCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        containerColor = if (item.reached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(start = Spacing.l, top = Spacing.l, bottom = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SavingsJar(item.fraction)
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    g.icon?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Text(g.name, style = MaterialTheme.typography.titleSmall)
                    if (g.visibility == Visibility.PRIVATE) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = stringResource(R.string.goals_private),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.goals_saved_slash, item.saved.formatted(), g.target.formatted()),
                    style = MaterialTheme.typography.titleMedium,
                )
                g.deadline?.let {
                    Text(
                        stringResource(R.string.goals_due, it.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val perMonth = item.perMonthNeeded
                if (item.reached) {
                    Pill(
                        text = stringResource(R.string.goals_completed),
                        icon = Icons.Filled.CheckCircle,
                        bg = MaterialTheme.colorScheme.surface,
                        fg = MaterialTheme.colorScheme.primary,
                    )
                } else if (perMonth != null) {
                    Pill(
                        text = stringResource(R.string.goals_save_month, perMonth.formatted()),
                        icon = null,
                        bg = HisaabTheme.extendedColors.chipWarn,
                        fg = HisaabTheme.extendedColors.onChipWarn,
                    )
                }
            }
            Box {
                MoreButton(onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!item.reached) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.goals_add_money)) }, onClick = {
                            menu = false
                            onAddMoney()
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.goals_edit)) }, onClick = {
                        menu = false
                        onEdit()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.goals_details)) }, onClick = {
                        menu = false
                        onClick()
                    })
                }
            }
        }
    }
}

@Composable
private fun Pill(
    text: String,
    icon: ImageVector?,
    bg: Color,
    fg: Color,
) {
    Row(
        Modifier
            .padding(top = Spacing.xs)
            .background(bg, CircleShape)
            .padding(horizontal = Spacing.s, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp).popIn()) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}
