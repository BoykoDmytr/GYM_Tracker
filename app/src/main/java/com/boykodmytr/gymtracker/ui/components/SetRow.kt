package com.boykodmytr.gymtracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle

/** One logged set: "2   60 кг × 8   RPE 8 · відмова". */
@Composable
fun SetRow(
    set: SetLog,
    weightUnit: WeightUnit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            set.setNumber.toString(),
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        val weight = if (set.weightKg > 0) Fmt.weightWithUnit(set.weightKg, weightUnit) else stringResource(R.string.set_bodyweight)
        Text(
            stringResource(R.string.set_row, weight, set.reps),
            style = MaterialTheme.typography.titleMedium.merge(NumberTextStyle),
            modifier = Modifier.weight(1f),
        )
        val extras = buildList {
            set.rpe?.let { add(stringResource(R.string.set_rpe_short, Fmt.rpe(it))) }
            if (set.isFailure) add(stringResource(R.string.set_failure_short))
        }
        if (extras.isNotEmpty()) {
            Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    set.note?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 36.dp, bottom = 4.dp),
        )
    }
}
