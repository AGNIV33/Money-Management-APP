package com.agniv.hisaab.feature.tools

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.designsystem.component.DonutSlice
import com.agniv.hisaab.core.designsystem.component.HButton
import com.agniv.hisaab.core.designsystem.component.HDonut
import com.agniv.hisaab.core.designsystem.component.HOutlinedButton
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanForm
import com.agniv.hisaab.core.domain.loan.LoanTerms

/** PRD F9b, EMI board: sliders, EMI with donut, year-by-year table, Save and Share. */
@Composable
internal fun EmiTool(
    state: ToolsUiState,
    onResult: (RecentResult?) -> Unit,
    onSave: (RecentResult) -> Unit,
) {
    val inputs = rememberLoanInputs()
    FillFromLoans(state.openLoans, inputs::fillFrom)
    InputCard { LoanRows(inputs) }
    val terms = inputs.terms
    val result = terms?.let { recent(Tool.EMI, EmiMath.emi(it).paise, it.tenureMonths.toLong(), it.principal.paise) }
    LaunchedEffect(terms) { onResult(result) }
    if (terms == null || result == null) {
        EmptyResult(stringResource(R.string.tools_need_inputs))
        return
    }
    val schedule = remember(terms) { EmiMath.schedule(terms) }
    val interest = remember(schedule) { schedule.map { it.interest }.sum() }
    val years = remember(schedule) { ToolsLogic.yearly(schedule) }
    EmiResult(terms, interest)
    YearTable(terms.principal, years)
    EmiActions(terms, interest, onSave = { onSave(result) })
}

@Composable
private fun EmiResult(
    terms: LoanTerms,
    interest: Money,
) {
    val emi = EmiMath.emi(terms)
    val total = terms.principal + interest
    val principalColor = MaterialTheme.colorScheme.primary
    val interestColor = HisaabTheme.extendedColors.gold
    ResultCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.emi_result), style = MaterialTheme.typography.titleSmall)
                Text(emi.formatted(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = principalColor)
                Text(stringResource(R.string.per_month), style = MaterialTheme.typography.titleSmall, color = principalColor)
            }
            Box(contentAlignment = Alignment.Center) {
                HDonut(
                    slices = listOf(DonutSlice(terms.principal.paise, principalColor), DonutSlice(interest.paise, interestColor)),
                    description = stringResource(R.string.emi_pie_desc, terms.principal.formatted(), interest.formatted()),
                    size = DONUT,
                    thickness = 14.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(AmountInput.compact(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.emi_total), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = DIVIDER_ALPHA))
        Stat(stringResource(R.string.emi_total_interest), interest.formatted(), icon = Icons.Outlined.Toll)
        Stat(stringResource(R.string.emi_total_payable), total.formatted(), icon = Icons.Outlined.AccountBalanceWallet)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Legend(principalColor, stringResource(R.string.emi_principal), terms.principal, total, Modifier.weight(1f))
            Legend(interestColor, stringResource(R.string.emi_interest), interest, total, Modifier.weight(1f))
        }
    }
}

private val DONUT = 112.dp
internal const val DIVIDER_ALPHA = 0.2f

@Composable
private fun Legend(
    color: Color,
    label: String,
    amount: Money,
    total: Money,
    modifier: Modifier,
) {
    Column(modifier) {
        Stat(label, "", dot = color)
        Text(amount.formatted(), style = MaterialTheme.typography.titleSmall)
        Text("(" + ToolsLogic.share(amount, total) + "%)", style = MaterialTheme.typography.labelMedium)
    }
}

/** Shows 3 years first: a 30-year loan is 30 rows, which would bury the answer. */
@Composable
private fun YearTable(
    principal: Money,
    years: List<YearRow>,
) {
    var open by rememberSaveable { mutableStateOf(true) }
    var all by rememberSaveable { mutableStateOf(false) }
    DetailCard {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            IconTile(Icons.Outlined.BarChart, size = 36.dp)
            Text(stringResource(R.string.emi_year_table), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = stringResource(if (open) R.string.tools_hide else R.string.tools_show),
            )
        }
        if (open) {
            YearRows(principal, if (all) years else years.take(FIRST_YEARS))
            if (years.size > FIRST_YEARS) ViewAll(all, years.size) { all = !all }
        }
    }
}

@Composable
private fun YearRows(
    principal: Money,
    shown: List<YearRow>,
) {
    // Fixed column widths in a side-scroll, so full rupee amounts never squeeze on small phones.
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        TableRow(
            listOf(
                stringResource(R.string.col_year),
                stringResource(R.string.col_opening),
                stringResource(R.string.col_principal),
                stringResource(R.string.col_interest),
                stringResource(R.string.col_balance),
            ),
            header = true,
        )
        shown.forEachIndexed { index, row ->
            HorizontalDivider(Modifier.width(TABLE_WIDTH), color = MaterialTheme.colorScheme.outlineVariant)
            val opening = if (index == 0) principal else shown[index - 1].balance
            TableRow(
                listOf(
                    row.year.toString(),
                    opening.formatted(),
                    row.principal.formatted(),
                    row.interest.formatted(),
                    row.balance.formatted(),
                ),
            )
        }
    }
}

@Composable
private fun ViewAll(
    all: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (all) stringResource(R.string.tools_show_less) else stringResource(R.string.emi_view_all, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            if (all) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private const val FIRST_YEARS = 3
private val YEAR_COL = 44.dp
private val AMOUNT_COL = 104.dp
private const val AMOUNT_COLS = 4
private val TABLE_WIDTH: Dp = YEAR_COL + AMOUNT_COL * AMOUNT_COLS

@Composable
private fun TableRow(
    cells: List<String>,
    header: Boolean = false,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.padding(vertical = Spacing.s)) {
        cells.forEachIndexed { index, text ->
            Text(
                text,
                style = style,
                color = color,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                maxLines = 2,
                modifier = Modifier.width(if (index == 0) YEAR_COL else AMOUNT_COL),
            )
        }
    }
}

@Composable
private fun EmiActions(
    terms: LoanTerms,
    interest: Money,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var saved by rememberSaveable(terms.toString()) { mutableStateOf(false) }
    val shareText =
        stringResource(
            R.string.emi_share_text,
            terms.principal.formatted(),
            LoanForm.rateText(terms.annualRateBps),
            terms.tenureMonths,
            EmiMath.emi(terms).formatted(),
            interest.formatted(),
            (terms.principal + interest).formatted(),
        )
    val shareTitle = stringResource(R.string.emi_share)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HOutlinedButton(
            onClick = {
                onSave()
                saved = true
            },
            enabled = !saved,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.BookmarkBorder, contentDescription = null)
            Text(stringResource(if (saved) R.string.emi_saved else R.string.emi_save), modifier = Modifier.padding(start = Spacing.s))
        }
        HButton(onClick = { share(context, shareText, shareTitle) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text(shareTitle, modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

/** Plain-text share sheet: numbers only, no names (privacy rule). */
internal fun share(
    context: Context,
    text: String,
    title: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(send, title))
}
