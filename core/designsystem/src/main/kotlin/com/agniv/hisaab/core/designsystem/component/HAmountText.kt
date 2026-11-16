package com.agniv.hisaab.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.MoneyFormatter
import com.agniv.hisaab.core.designsystem.R
import com.agniv.hisaab.core.designsystem.motion.Motion
import com.agniv.hisaab.core.designsystem.motion.rememberReducedMotion
import com.agniv.hisaab.core.designsystem.theme.DisplayAmount
import com.agniv.hisaab.core.designsystem.theme.HisaabTheme

@Composable
fun HAmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    status: AmountStatus = AmountStatus.NEUTRAL,
    paise: MoneyFormatter.Paise = MoneyFormatter.Paise.WHEN_NEEDED,
    // Counts up from ₹0 the first time it is shown (big totals only). TalkBack always hears the final amount.
    countUp: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val shown = remember { Animatable(if (countUp && !reduced) 0f else amount.paise.toFloat()) }
    LaunchedEffect(amount) {
        if (countUp && !reduced) {
            shown.animateTo(amount.paise.toFloat(), tween(Motion.FILL, easing = FastOutSlowInEasing))
        } else {
            shown.snapTo(amount.paise.toFloat())
        }
    }
    // Float loses paise above ~₹1.6 lakh, so the exact amount is shown as soon as the animation stops.
    val counting = shown.isRunning || (countUp && !reduced && shown.value == 0f && !amount.isZero)
    val finalText = remember(amount, paise) { MoneyFormatter.format(amount, paise) }
    val text =
        if (counting) {
            // Whole rupees while counting so the digits do not flicker through paise.
            MoneyFormatter.format(Money(shown.value.toLong() / PAISE_PER_RUPEE * PAISE_PER_RUPEE), MoneyFormatter.Paise.NEVER)
        } else {
            finalText
        }
    val words =
        MoneyFormatter.SpokenWords(
            rupees = stringResource(R.string.ds_spoken_rupees),
            paise = stringResource(R.string.ds_spoken_paise),
            minus = stringResource(R.string.ds_spoken_minus),
        )
    val spoken = remember(amount, words) { MoneyFormatter.spoken(amount, words) }
    Text(
        text = text,
        // TalkBack says "250 rupees" instead of reading the ₹ sign and commas (DESIGN.md §10).
        modifier = modifier.semantics { contentDescription = spoken },
        color = amountColor(status),
        // Tabular digits keep columns of amounts aligned.
        style = style.merge(TextStyle(fontFeatureSettings = "tnum")),
        // No maxLines: at 200% font a big amount wraps instead of being cut off; money must never be hidden.
    )
}

private const val PAISE_PER_RUPEE = 100L

@Composable
private fun amountColor(status: AmountStatus): Color =
    when (status) {
        AmountStatus.NEUTRAL -> LocalContentColor.current
        AmountStatus.SUCCESS -> HisaabTheme.extendedColors.success
        AmountStatus.WARNING -> HisaabTheme.extendedColors.warning
        AmountStatus.ALERT -> MaterialTheme.colorScheme.error
    }

@Preview
@Composable
private fun HAmountTextPreview() {
    HisaabTheme {
        HAmountText(amount = Money(12_500_050), style = DisplayAmount)
    }
}
