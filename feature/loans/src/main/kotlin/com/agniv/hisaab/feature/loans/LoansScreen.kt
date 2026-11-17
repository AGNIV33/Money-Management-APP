package com.agniv.hisaab.feature.loans

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.TintSummaryCard
import com.agniv.hisaab.core.designsystem.theme.CardAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing

/** Board "EMI": total monthly EMI, one card per EMI with its status, and a loan's detail in place of the list. */
@Composable
fun LoansScreen(
    onBack: () -> Unit,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    // null = closed, "" = adding, otherwise the id being edited.
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetCount by rememberSaveable { mutableIntStateOf(0) }
    val openSheet: (String) -> Unit = {
        sheetCount++
        sheetFor = it
    }

    val open = state.overview?.items?.firstOrNull { it.loan.id == openId }
    if (openId != null && open != null) {
        BackHandler { openId = null }
        LoanDetail(
            item = open,
            today = state.today,
            onBack = { openId = null },
            onEdit = { openSheet(open.loan.id) },
            canDelete = state.canDelete(open.loan),
            onDelete = {
                viewModel.onEvent(LoansEvent.Delete(open.loan.id))
                openId = null
            },
            onSetPaid = { month, paid -> viewModel.onEvent(LoansEvent.SetPaid(open.loan.id, month, paid)) },
            // Reminders only ever go to the loan's owner, so only they get the switch.
            reminderOn = (open.loan.id !in state.mutedReminders).takeIf { open.loan.ownerUserId == state.viewerUserId },
            onReminderChange = { on -> viewModel.onEvent(LoansEvent.SetReminder(open.loan.id, on)) },
        )
    } else {
        LoansList(state, onBack = onBack, onOpen = { openId = it }, onAdd = { openSheet("") })
    }

    // Android 13+ needs the user's OK before EMI reminders can show; asked right after a loan is saved.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    sheetFor?.let { id ->
        AddLoanSheet(
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

@Composable
private fun LoansList(
    state: LoansUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val overview = state.overview
    val hasLoans = overview != null && overview.items.isNotEmpty()
    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.emi_title), onBack = onBack) },
        floatingActionButton = {
            if (hasLoans) {
                FloatingActionButton(
                    onClick = onAdd,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.emi_add)) }
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
            if (overview != null && hasLoans) {
                item(key = "summary") {
                    TintSummaryCard(icon = Icons.Outlined.Payments) {
                        Text(
                            stringResource(R.string.emi_total_monthly),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        HAmountText(overview.monthlyEmi, style = CardAmount, countUp = true)
                        Text(
                            stringResource(R.string.loans_outstanding, overview.outstanding.formatted()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                items(overview.items, key = { it.loan.id }) { item ->
                    LoanCard(item, onClick = { onOpen(item.loan.id) })
                }
            } else if (!state.loading) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.EventRepeat,
                        title = stringResource(R.string.emi_empty_title),
                        body = stringResource(R.string.emi_empty_body),
                        action = stringResource(R.string.emi_add),
                        onAction = onAdd,
                    )
                }
            }
        }
    }
}
