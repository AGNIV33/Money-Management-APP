package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.agniv.hisaab.feature.expenses.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Board FAB: round green "+", 56 dp. */
@Composable
fun AddFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_expense_fab))
    }
}

/** "Today", "Yesterday" or "12 Nov". Expenses keep a date only, so no clock time is shown. */
@Composable
@ReadOnlyComposable
internal fun dayLabel(
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
): String =
    when (date) {
        today -> stringResource(R.string.home_today)
        today.minusDays(1) -> stringResource(R.string.home_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0]))
    }
