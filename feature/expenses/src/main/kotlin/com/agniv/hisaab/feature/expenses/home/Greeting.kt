package com.agniv.hisaab.feature.expenses.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.agniv.hisaab.feature.expenses.R
import java.time.LocalTime

/** Board Home header: "Good morning, Agniv 👋" and "Your family's finances at a glance". */
@Composable
internal fun Greeting(
    firstName: String?,
    modifier: Modifier = Modifier,
) {
    val hour = remember { LocalTime.now().hour }
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(
            if (firstName.isNullOrBlank()) {
                stringResource(greetingRes(hour))
            } else {
                stringResource(greetingNameRes(hour), firstName)
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.home_dashboard_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun greetingRes(hour: Int): Int =
    when (partOfDay(hour)) {
        PartOfDay.MORNING -> R.string.home_greeting_morning
        PartOfDay.AFTERNOON -> R.string.home_greeting_afternoon
        PartOfDay.EVENING -> R.string.home_greeting_evening
    }

internal fun greetingNameRes(hour: Int): Int =
    when (partOfDay(hour)) {
        PartOfDay.MORNING -> R.string.home_greeting_morning_name
        PartOfDay.AFTERNOON -> R.string.home_greeting_afternoon_name
        PartOfDay.EVENING -> R.string.home_greeting_evening_name
    }

private enum class PartOfDay { MORNING, AFTERNOON, EVENING }

private fun partOfDay(hour: Int): PartOfDay =
    when (hour) {
        in MORNING_START until AFTERNOON_START -> PartOfDay.MORNING
        in AFTERNOON_START until EVENING_START -> PartOfDay.AFTERNOON
        else -> PartOfDay.EVENING
    }

private const val MORNING_START = 4
private const val AFTERNOON_START = 12
private const val EVENING_START = 17
