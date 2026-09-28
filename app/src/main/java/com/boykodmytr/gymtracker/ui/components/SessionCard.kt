package com.boykodmytr.gymtracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.SessionSummary
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.format.Fmt

/** History row: name, when, how long, how much. */
@Composable
fun SessionCard(
    session: SessionSummary,
    weightUnit: WeightUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = LocalClock.current.zone
    SectionCard(modifier = modifier, onClick = onClick) {
        Text(session.name, style = MaterialTheme.typography.titleMedium)
        val start = session.startedAt.atZone(zone).toLocalTime()
        Text(
            "${Fmt.fullDate(session.date).replaceFirstChar { it.titlecase() }} · ${Fmt.time(start)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val parts = buildList {
            session.duration?.let { add(Fmt.duration(it)) }
            add(pluralStringResource(R.plurals.exercises_count, session.exerciseCount, session.exerciseCount))
            add(pluralStringResource(R.plurals.sets_count, session.setCount, session.setCount))
            if (session.volumeKg > 0) add(Fmt.volume(session.volumeKg, weightUnit))
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
