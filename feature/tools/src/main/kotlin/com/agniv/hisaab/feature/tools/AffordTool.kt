package com.agniv.hisaab.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.designsystem.component.IconTile
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme
import com.agniv.hisaab.core.designsystem.theme.Spacing
import com.agniv.hisaab.core.domain.loan.BurdenLevel
import com.agniv.hisaab.core.domain.loan.LoanForm
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val INCOME_MIN = 25_000f
private const val INCOME_MAX = 500_000f
private const val INCOME_STEP = 1_000f
private const val EMIS_MAX = 200_000f
private const val EMIS_STEP = 500f
private const val NEW_LOAN_MIN = 50_000f
private const val NEW_LOAN_MAX = 20_000_000f
private const val NEW_LOAN_STEP = 10_000f

/** PRD F9d, "Can I afford it?" board: EMI-to-income gauge, the level in words, money left, and advice. */
@Composable
internal fun AffordTool(
    state: ToolsUiState,
    onResult: (RecentResult?) -> Unit,
) {
    val income = rememberSaveable { mutableStateOf("") }
    val existing = rememberSaveable { mutableStateOf("") }
    // The EMIs already in Kinly are a good starting point; the user can change it.
    LaunchedEffect(state.monthlyEmi) {
        if (existing.value.isEmpty() && !state.monthlyEmi.isZero) {
            existing.value = AmountInput.cleanAmount(LoanForm.amountText(state.monthlyEmi))
        }
    }
    val inputs = rememberLoanInputs()
    InputCard {
        AmountRow(
            income,
            R.string.afford_income,
            R.string.afford_income_help,
            Icons.Outlined.AccountBalanceWallet,
            INCOME_MIN,
            INCOME_MAX,
            INCOME_STEP,
        )
        RowDivider()
        AmountRow(existing, R.string.afford_existing, R.string.afford_existing_help, Icons.Outlined.Payments, 0f, EMIS_MAX, EMIS_STEP)
        RowDivider()
        AmountRow(
            inputs.amount,
            R.string.afford_new_loan,
            R.string.afford_new_loan_help,
            Icons.Outlined.RequestQuote,
            NEW_LOAN_MIN,
            NEW_LOAN_MAX,
            NEW_LOAN_STEP,
        )
        RowDivider()
        RateRow(inputs.rate)
        RowDivider()
        TenureRow(inputs)
    }
    val incomeMoney = LoanForm.parseAmount(income.value)
    val existingMoney = if (existing.value.isBlank()) Money.ZERO else Money.parse(existing.value)?.takeIf { !it.isNegative }
    val check = ToolsLogic.afford(incomeMoney, existingMoney, inputs.terms)
    LaunchedEffect(check) {
        onResult(check?.let { recent(Tool.AFFORD, it.result.newEmi.paise, Math.round(it.result.burdenPercent * TENTHS)) })
    }
    if (check == null || incomeMoney == null) {
        EmptyResult(stringResource(R.string.afford_need_inputs), Icons.Outlined.Balance)
    } else {
        AffordResult(check, incomeMoney)
    }
}

private const val TENTHS = 10

private data class LevelLook(
    val label: Int,
    val sentence: Int,
    val advice: Int,
    val icon: ImageVector,
    val chip: Color,
    val onChip: Color,
)

@Composable
private fun lookFor(level: BurdenLevel): LevelLook {
    val x = HisaabTheme.extendedColors
    return when (level) {
        BurdenLevel.COMFORTABLE ->
            LevelLook(
                R.string.afford_ok,
                R.string.afford_ok_sentence,
                R.string.afford_ok_advice,
                Icons.Outlined.CheckCircle,
                x.chipGood,
                x.onChipGood,
            )
        BurdenLevel.CAUTION ->
            LevelLook(
                R.string.afford_caution,
                R.string.afford_caution_sentence,
                R.string.afford_caution_advice,
                Icons.Outlined.WarningAmber,
                x.chipWarn,
                x.onChipWarn,
            )
        BurdenLevel.RISKY ->
            LevelLook(
                R.string.afford_risky,
                R.string.afford_risky_sentence,
                R.string.afford_risky_advice,
                Icons.Outlined.ErrorOutline,
                x.chipOver,
                x.onChipOver,
            )
    }
}

@Composable
private fun AffordResult(
    check: AffordCheck,
    income: Money,
) {
    val result = check.result
    val look = lookFor(result.level)
    val percent = String.format(Locale.US, "%.0f", result.burdenPercent)
    val left = income - result.totalEmiAfter
    ResultCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BurdenGauge(result.burdenPercent, stringResource(R.string.afford_burden, percent), "$percent%")
        }
        Row(
            Modifier
                .clip(CircleShape)
                .background(look.chip)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(look.icon, contentDescription = null, tint = look.onChip)
            Text(stringResource(look.label), style = MaterialTheme.typography.titleMedium, color = look.onChip)
        }
        Text(stringResource(look.sentence, percent), style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = DIVIDER_ALPHA))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            MiniStat(Icons.Outlined.Payments, stringResource(R.string.afford_new_emi), result.newEmi.formatted(), Modifier.weight(1f))
            MiniStat(Icons.Outlined.AccountBalanceWallet, stringResource(R.string.afford_left), left.formatted(), Modifier.weight(1f))
        }
        TipBox(Icons.Outlined.Lightbulb) {
            Text(stringResource(look.advice, result.totalEmiAfter.formatted()), style = MaterialTheme.typography.bodyMedium)
            val max = check.maxLoan
            Text(
                if (max == null) stringResource(R.string.afford_no_room) else stringResource(R.string.afford_max, max.formatted()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Text(stringResource(R.string.afford_note), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MiniStat(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        IconTile(icon, size = 36.dp)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Half-circle gauge in three bands that match [BurdenLevel]: green under 30%, amber 30–40%, red over 40%.
 * A dot marks the user's %. TalkBack reads [description] instead of the drawing.
 */
@Composable
private fun BurdenGauge(
    percent: Double,
    description: String,
    centre: String,
) {
    val x = HisaabTheme.extendedColors
    val bands =
        listOf(
            0f to CAUTION_FROM to x.success,
            CAUTION_FROM to RISKY_FROM to x.warning,
            RISKY_FROM to FULL to MaterialTheme.colorScheme.error,
        )
    val dot = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .width(GAUGE_WIDTH)
            .height(GAUGE_WIDTH / 2 + GAUGE_STROKE)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.size(GAUGE_WIDTH)) {
            val stroke = GAUGE_STROKE.toPx()
            val arc = Size(size.width - stroke, size.width - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            bands.forEach { (range, color) ->
                val (from, to) = range
                drawArc(
                    color,
                    HALF_TURN + from / FULL * HALF_TURN,
                    (to - from) / FULL * HALF_TURN,
                    false,
                    topLeft,
                    arc,
                    style = Stroke(stroke),
                )
            }
            val angle = Math.toRadians((HALF_TURN + percent.coerceIn(0.0, FULL.toDouble()) / FULL * HALF_TURN).toDouble())
            val radius = arc.width / 2
            val centrePoint = Offset(size.width / 2, size.width / 2)
            val at = Offset(centrePoint.x + radius * cos(angle).toFloat(), centrePoint.y + radius * sin(angle).toFloat())
            drawCircle(dot, radius = stroke * DOT_SHARE, center = at)
            drawLine(dot, centrePoint, at, strokeWidth = stroke / NEEDLE_THIN, cap = StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centre, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.afford_of_income), style = MaterialTheme.typography.labelMedium)
        }
    }
    Row(Modifier.width(GAUGE_WIDTH)) {
        Text("0%", style = MaterialTheme.typography.labelSmall)
        Text(
            stringResource(R.string.afford_bands),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Text("100%", style = MaterialTheme.typography.labelSmall)
    }
}

private val GAUGE_WIDTH = 220.dp
private val GAUGE_STROKE = 18.dp
private const val HALF_TURN = 180f
private const val FULL = 100f
private const val CAUTION_FROM = 30f
private const val RISKY_FROM = 40f
private const val DOT_SHARE = 0.6f
private const val NEEDLE_THIN = 4f
