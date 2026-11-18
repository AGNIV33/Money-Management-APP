package com.agniv.hisaab.feature.insights.report

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.InsertChartOutlined
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.EmptyState
import com.agniv.hisaab.core.designsystem.component.HAmountText
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HCard
import com.agniv.hisaab.core.designsystem.component.KinlyTopBar
import com.agniv.hisaab.core.designsystem.component.MonthPickerChip
import com.agniv.hisaab.core.designsystem.component.TintSummaryCard
import com.agniv.hisaab.core.designsystem.theme.CardAmount
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.feature.insights.R
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Board "Monthly Report": month picker, total with the change against last month, category donut, three metric
 * cards (biggest expense, EMI share, savings), plain tips and Share. EMI share and savings need the private income;
 * without it the savings card says "Set income" instead of guessing.
 */
@Composable
fun MonthlyReportScreen(
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val monthName = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    val lastName = state.month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMM", locale))
    val shareText = shareSummary(state, monthName)

    Scaffold(
        topBar = { KinlyTopBar(title = stringResource(R.string.report_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "month") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    MonthPickerChip(month = state.month, current = state.current, onPick = viewModel::setMonth)
                }
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.InsertChartOutlined,
                        title = stringResource(R.string.report_empty_title),
                        body = stringResource(R.string.report_empty_body),
                        action = stringResource(R.string.report_add_expense),
                        onAction = onAddExpense,
                    )
                }
                return@LazyColumn
            }
            item(key = "total") {
                TintSummaryCard(icon = Icons.Outlined.AccountBalanceWallet) {
                    Text(
                        stringResource(R.string.report_total_spent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    HAmountText(state.spent, style = CardAmount, countUp = true)
                    state.change?.let { change ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (change <= 0) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowDropUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                stringResource(
                                    if (change <= 0) R.string.report_less_vs else R.string.report_more_vs,
                                    abs(change),
                                    lastName,
                                    state.lastMonth.formatted(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            item(key = "donut") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.report_by_category), style = MaterialTheme.typography.titleMedium)
                    HCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.l)) { CategoryDonut(state.slices, state.categories, state.spent) }
                    }
                }
            }
            item(key = "metrics") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val biggest = state.biggest
                    MetricTile(
                        Icons.Outlined.StarOutline,
                        stringResource(R.string.report_biggest),
                        biggest?.amount?.formatted() ?: "—",
                        biggest?.let { state.categories[it.categoryId]?.name },
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        Icons.Outlined.CalendarMonth,
                        stringResource(R.string.report_emi_share),
                        state.emiPercent?.let { "$it%" } ?: "—",
                        if (state.emiPercent != null) "(${state.monthlyEmi.formatted()})" else stringResource(R.string.report_set_income),
                        Modifier.weight(1f),
                    )
                    MetricTile(
                        Icons.Outlined.Savings,
                        stringResource(R.string.report_savings),
                        state.savingsPercent?.let { "$it%" } ?: "—",
                        state.savings?.let { "(${it.formatted()})" } ?: stringResource(R.string.report_set_income),
                        Modifier.weight(1f),
                    )
                }
            }
            item(key = "tips") { TipsCard(state.tips, state.categories) }
            item(key = "share") {
                HButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, null))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.report_share), modifier = Modifier.padding(start = Spacing.s))
                }
            }
        }
    }
}

/** Plain text the user chooses to share: totals and categories only, no notes or names. */
@Composable
private fun shareSummary(
    state: ReportState,
    monthName: String,
): String {
    val lines = mutableListOf(stringResource(R.string.report_share_head, monthName, state.spent.formatted()))
    state.slices.forEach { lines += "• ${it.name(state.categories)}: ${it.total.formatted()} (${it.percent}%)" }
    return lines.joinToString("\n")
}
