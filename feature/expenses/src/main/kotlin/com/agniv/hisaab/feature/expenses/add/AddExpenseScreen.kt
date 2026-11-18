package com.agniv.hisaab.feature.expenses.add

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.designsystem.component.FieldBox
import com.agniv.hisaab.core.designsystem.component.HBusyButton
import com.agniv.hisaab.core.designsystem.component.IconChoiceChip
import com.agniv.hisaab.core.designsystem.component.InlineError
import com.agniv.hisaab.core.designsystem.component.KinlySelectField
import com.agniv.hisaab.core.designsystem.component.KinlyTextField
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.theme.ButtonShape
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.InterFamily
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.feature.expenses.R
import com.agniv.hisaab.feature.expenses.scan.ScanBillScreen
import com.agniv.hisaab.feature.expenses.ui.iconVector
import com.agniv.hisaab.feature.expenses.ui.label
import com.agniv.hisaab.feature.expenses.voice.VoiceAddSheet

/**
 * Add / edit expense (board "Add Expense"). Voice opens a bottom sheet over this screen; Scan bill opens its own
 * full screen. Both only fill the form or save after the user confirms (AI suggests, user confirms).
 */
@Composable
fun AddExpenseScreen(
    onDone: () -> Unit,
    editId: String? = null,
    viewModel: AddExpenseViewModel = hiltViewModel(),
) {
    var prefill by rememberSaveable(stateSaver = PrefillSaver) { mutableStateOf<ExpensePrefill?>(null) }
    LaunchedEffect(editId, prefill) { viewModel.start(editId, prefill) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.savedEvents.collect { onDone() } }
    var showVoice by rememberSaveable { mutableStateOf(false) }
    var showScan by rememberSaveable { mutableStateOf(false) }

    if (showScan) {
        BackHandler { showScan = false }
        ScanBillScreen(onBack = { showScan = false }, onSaved = onDone)
        return
    }
    Scaffold(
        topBar = {
            KinlyTopBar(
                title = stringResource(if (state.editingId == null) R.string.add_expense_title else R.string.edit_expense_title),
                onBack = onDone,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AddExpenseContent(
            state = state,
            onEvent = viewModel::onEvent,
            onVoice = { showVoice = true },
            onScan = { showScan = true },
            modifier = Modifier.padding(padding),
        )
    }
    if (showVoice) {
        VoiceAddSheet(
            onDismiss = { showVoice = false },
            onConfirm = {
                prefill = it
                showVoice = false
            },
        )
    }
}

// Many optional parts (credit, repeat, goal, delete) each add a branch; split further it would read worse.
@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddExpenseContent(
    state: AddExpenseUiState,
    onEvent: (AddExpenseEvent) -> Unit,
    onVoice: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(state.onCredit || state.repeat != null || state.onlyMe) }
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        AmountBox(state.amountText, onChange = { onEvent(AddExpenseEvent.EditAmount(it)) })
        ErrorBanner(state.error)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.add_expense_category),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.add_choose_category),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                state.categories.forEach { category ->
                    IconChoiceChip(
                        label = category.label(),
                        icon = category.iconVector(),
                        selected = category.id == state.categoryId,
                        onClick = { onEvent(AddExpenseEvent.PickCategory(category.id)) },
                    )
                }
            }
            if (state.categoryAuto) {
                Text(
                    stringResource(R.string.category_auto),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Labeled(stringResource(R.string.add_expense_note)) {
            KinlyTextField(
                value = state.note,
                onValueChange = { onEvent(AddExpenseEvent.EditNote(it)) },
                placeholder = stringResource(R.string.add_note_hint),
                leadingIcon = Icons.Outlined.Description,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Labeled(stringResource(R.string.add_date), Modifier.weight(1f)) {
                FieldBox(
                    value = dateText(state.date, state.today, locale),
                    leadingIcon = Icons.Outlined.CalendarToday,
                    trailingIcon = Icons.Filled.KeyboardArrowDown,
                    onClick = { pickingDate = true },
                )
            }
            Labeled(stringResource(R.string.add_payment_mode), Modifier.weight(1f)) {
                KinlySelectField(
                    value = stringResource(state.mode.labelRes()),
                    options = PayMode.entries,
                    optionLabel = { stringResource(it.labelRes()) },
                    onSelect = { onEvent(AddExpenseEvent.PickMode(it)) },
                    leadingIcon = Icons.Outlined.CreditCard,
                )
            }
        }

        if (state.editingId == null && state.goals.isNotEmpty()) {
            Labeled(stringResource(R.string.goal_pick_title_optional)) {
                val none = stringResource(R.string.goal_pick_select)
                KinlySelectField(
                    value = state.goals.firstOrNull { it.id == state.goalId }?.name ?: none,
                    options = listOf<GoalChoice?>(null) + state.goals,
                    optionLabel = { it?.name ?: stringResource(R.string.goal_pick_none) },
                    onSelect = { onEvent(AddExpenseEvent.PickGoal(it?.id)) },
                    leadingIcon = Icons.Outlined.TrackChanges,
                )
            }
        }

        if (state.editingId == null) {
            MoreOptions(state, onEvent, open = moreOpen, onToggle = { moreOpen = !moreOpen })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                QuickButton(
                    text = stringResource(R.string.add_voice),
                    icon = Icons.Outlined.Mic,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onVoice,
                    modifier = Modifier.weight(1f),
                )
                QuickButton(
                    text = stringResource(R.string.add_scan),
                    icon = Icons.Outlined.CameraAlt,
                    container = HisaabTheme.extendedColors.goldContainer,
                    content = HisaabTheme.extendedColors.onGoldContainer,
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            OnlyMeRow(state.onlyMe) { onEvent(AddExpenseEvent.SetOnlyMe(it)) }
        }

        HBusyButton(
            text = stringResource(R.string.add_expense_save),
            busyText = stringResource(R.string.add_saving),
            busy = state.saving,
            enabled = state.amount != null,
            onClick = { onEvent(AddExpenseEvent.Save) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.canDelete) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete_expense), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_expense_title)) },
            text = { Text(stringResource(R.string.delete_expense_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onEvent(AddExpenseEvent.Delete)
                }) { Text(stringResource(R.string.delete_expense), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickingDate) {
        DateDialog(
            initial = state.date,
            today = state.today,
            onPick = {
                onEvent(AddExpenseEvent.PickDate(it))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** Big "₹ 0" on the green tint; the phone's number keyboard types into it. */
@Composable
private fun AmountBox(
    text: String,
    onChange: (String) -> Unit,
) {
    val label = stringResource(R.string.add_amount)
    val color = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
            .padding(Spacing.l),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = SolidColor(color),
            textStyle = AmountStyle.copy(color = color, textAlign = TextAlign.Center),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("₹ ", style = AmountStyle, color = color)
                    Box {
                        if (text.isEmpty()) Text("0", style = AmountStyle, color = color.copy(alpha = HINT_ALPHA))
                        inner()
                    }
                }
            },
        )
    }
}

private val AmountStyle =
    TextStyle(fontFamily = InterFamily, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
private const val HINT_ALPHA = 0.6f

@Composable
private fun Labeled(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun QuickButton(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = 56.dp)
            .background(container, ButtonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(text, style = MaterialTheme.typography.titleMedium, color = content, modifier = Modifier.padding(start = Spacing.s))
    }
}

/** Extras the board leaves out but the app already supports: bought on credit, repeat, only me. */
@Composable
private fun MoreOptions(
    state: AddExpenseUiState,
    onEvent: (AddExpenseEvent) -> Unit,
    open: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.add_more_options),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                SwitchRow(stringResource(R.string.credit_title), stringResource(R.string.credit_hint), state.onCredit) {
                    onEvent(AddExpenseEvent.SetOnCredit(it))
                }
                if (state.onCredit) {
                    KinlyTextField(
                        value = state.creditFrom,
                        onValueChange = { onEvent(AddExpenseEvent.EditCreditFrom(it)) },
                        label = stringResource(R.string.credit_from),
                        isError = state.error == AddError.CREDIT_FROM,
                    )
                }
                Text(stringResource(R.string.repeat_title), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    listOf(null, RecurRule.DAILY, RecurRule.WEEKLY, RecurRule.MONTHLY).forEach { rule ->
                        FilterChip(
                            selected = state.repeat == rule,
                            onClick = { onEvent(AddExpenseEvent.PickRepeat(rule)) },
                            label = { Text(stringResource(rule.labelRes())) },
                        )
                    }
                }
                OnlyMeRow(state.onlyMe) { onEvent(AddExpenseEvent.SetOnlyMe(it)) }
            }
        }
    }
}

@Composable
private fun OnlyMeRow(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SwitchRow(stringResource(R.string.add_expense_only_me), stringResource(R.string.add_expense_only_me_hint), on, onChange)
}

@Composable
private fun SwitchRow(
    title: String,
    hint: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun ErrorBanner(error: AddError?) {
    if (error == null) return
    val text =
        when (error) {
            AddError.AMOUNT -> R.string.error_amount
            AddError.CATEGORY -> R.string.error_category
            AddError.NOTE -> R.string.error_note
            AddError.CREDIT_FROM -> R.string.error_credit_from
            AddError.SAVE_FAILED -> R.string.error_save
        }
    InlineError(stringResource(text))
}
