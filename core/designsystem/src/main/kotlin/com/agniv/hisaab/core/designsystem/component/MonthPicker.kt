package com.agniv.hisaab.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agniv.hisaab.core.designsystem.theme.Spacing
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Board month selector: "📅 Nov 2026 ⌄". Opens a list of the last [count] months, newest first. No future months:
 * nothing can be spent there yet.
 */
@Composable
fun MonthPickerChip(
    month: YearMonth,
    current: YearMonth,
    onPick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    count: Int = DEFAULT_MONTHS,
) {
    var open by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val short = remember(locale) { DateTimeFormatter.ofPattern("MMM yyyy", locale) }
    val long = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    Box(modifier) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(12.dp))
                .clickable { open = true }
                .semantics(mergeDescendants = true) { role = Role.DropdownList }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(month.format(short), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in List(count) { current.minusMonths(it.toLong()) }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            m.format(long),
                            color = if (m == month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onPick(m)
                    },
                )
            }
        }
    }
}

private const val DEFAULT_MONTHS = 12
