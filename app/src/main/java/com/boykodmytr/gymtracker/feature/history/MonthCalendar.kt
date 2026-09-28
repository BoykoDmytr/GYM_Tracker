package com.boykodmytr.gymtracker.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.ui.format.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Month grid, Monday first. Status is shown by shape as well as colour: filled circle = done,
 * ring = planned, small dot = missed, so it reads without relying on colour alone.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    statuses: Map<LocalDate, DayStatus>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    Fmt.dayOfWeekShort(day),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val cells = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                status = statuses[date] ?: DayStatus.NONE,
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, status: DayStatus, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val statusLabel = when (status) {
        DayStatus.COMPLETED -> stringResource(R.string.history_legend_done)
        DayStatus.MISSED -> stringResource(R.string.history_legend_missed)
        DayStatus.PLANNED -> stringResource(R.string.history_legend_planned)
        DayStatus.NONE -> ""
    }
    val description = listOf(Fmt.fullDate(date), statusLabel).filter { it.isNotEmpty() }.joinToString(", ")
    Box(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) colors.secondaryContainer else colors.surface.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        val circle = Modifier.size(36.dp)
        when (status) {
            DayStatus.COMPLETED -> Box(circle.clip(CircleShape).background(colors.primary))
            DayStatus.PLANNED -> Box(circle.border(1.5.dp, colors.primary, CircleShape))
            else -> Unit
        }
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when (status) {
                DayStatus.COMPLETED -> colors.onPrimary
                DayStatus.MISSED -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
        )
        if (status == DayStatus.MISSED) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.error),
            )
        }
        if (isToday && status != DayStatus.COMPLETED) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .background(colors.onSurface),
            )
        }
    }
}

@Composable
fun CalendarLegend(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(stringResource(R.string.history_legend_done)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(colors.primary))
        }
        LegendItem(stringResource(R.string.history_legend_planned)) {
            Box(Modifier.size(12.dp).border(1.5.dp, colors.primary, CircleShape))
        }
        LegendItem(stringResource(R.string.history_legend_missed)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(colors.error))
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) { swatch() }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
