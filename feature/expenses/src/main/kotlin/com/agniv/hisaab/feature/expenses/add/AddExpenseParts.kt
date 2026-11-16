package com.agniv.hisaab.feature.expenses.add

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.agniv.hisaab.core.domain.model.PayMode
import com.agniv.hisaab.core.domain.model.RecurRule
import com.agniv.hisaab.feature.expenses.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun dateText(
    date: LocalDate,
    today: LocalDate,
    locale: java.util.Locale,
): String {
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    return when (date) {
        today -> stringResource(R.string.date_today_full, formatted)
        today.minusDays(1) -> stringResource(R.string.date_yesterday_full, formatted)
        else -> formatted
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(
    initial: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC millis.
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() <= today
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.toLocalDate()) } ?: onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal fun PayMode.labelRes(): Int =
    when (this) {
        PayMode.CASH -> R.string.mode_cash
        PayMode.UPI -> R.string.mode_upi
        PayMode.CARD -> R.string.mode_card
        PayMode.OTHER -> R.string.mode_other
    }

internal fun RecurRule?.labelRes(): Int =
    when (this) {
        null -> R.string.repeat_never
        RecurRule.DAILY -> R.string.repeat_daily
        RecurRule.WEEKLY -> R.string.repeat_weekly
        RecurRule.MONTHLY -> R.string.repeat_monthly
    }

/** Keeps a confirmed voice result across rotation, so the form is not emptied. */
internal val PrefillSaver =
    androidx.compose.runtime.saveable.Saver<ExpensePrefill?, List<String>>(
        save = { p ->
            p?.let { listOf(it.amountText, it.categoryId.orEmpty(), it.date.toString(), it.note, it.mode?.name.orEmpty()) } ?: emptyList()
        },
        restore = { l ->
            if (l.size < PREFILL_PARTS) {
                null
            } else {
                ExpensePrefill(
                    amountText = l[0],
                    categoryId = l[1].ifEmpty { null },
                    date = LocalDate.parse(l[2]),
                    note = l[3],
                    mode = l[4].ifEmpty { null }?.let(PayMode::valueOf),
                )
            }
        },
    )

private const val PREFILL_PARTS = 5
