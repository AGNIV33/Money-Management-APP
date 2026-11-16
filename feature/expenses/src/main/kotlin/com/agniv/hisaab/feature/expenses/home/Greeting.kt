package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.agniv.hisaab.feature.expenses.R
import java.time.LocalTime

/** Kinly spec home header: a time-of-day hello and "Your family dashboard". */
@Composable
internal fun Greeting() {
    val hour = remember { LocalTime.now().hour }
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(stringResource(greetingRes(hour)), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.home_dashboard_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun greetingRes(hour: Int): Int =
    when (hour) {
        in MORNING_START until AFTERNOON_START -> R.string.home_greeting_morning
        in AFTERNOON_START until EVENING_START -> R.string.home_greeting_afternoon
        else -> R.string.home_greeting_evening
    }

private const val MORNING_START = 4
private const val AFTERNOON_START = 12
private const val EVENING_START = 17
