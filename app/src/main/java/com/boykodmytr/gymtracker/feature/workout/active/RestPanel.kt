package com.boykodmytr.gymtracker.feature.workout.active

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.AppSettings
import com.boykodmytr.gymtracker.domain.model.RestState
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.rememberNow
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.theme.TimerTextStyle
import java.time.Duration

/** Countdown while resting; a highlighted "time's up" banner once it is over. */
@Composable
fun RestPanel(
    rest: RestState,
    onAdjust: (Long) -> Unit,
    onPreset: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow(250)
    val remaining = Duration.between(now, rest.endsAt)
    if (remaining.isNegative || remaining.isZero) {
        SectionCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.primary) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Text(
                    stringResource(R.string.workout_rest_over),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = onSkip) { Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        return
    }
    val total = rest.totalSeconds.coerceAtLeast(1)
    // Clamp: the ticker may lag a frame behind a freshly started rest.
    val remainingMillis = remaining.toMillis().coerceAtMost(total * 1000)
    val remainingSeconds = (remainingMillis + 999) / 1000
    SectionCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_rest), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text(stringResource(R.string.workout_rest_skip)) }
        }
        Text(
            Fmt.clockSeconds(remainingSeconds),
            style = TimerTextStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LinearProgressIndicator(
            progress = { (remainingMillis.toFloat() / (total * 1000f)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(stringResource(R.string.workout_rest_minus), { onAdjust(-15) }, Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.workout_rest_plus), { onAdjust(15) }, Modifier.weight(1f))
        }
        RestPresets(selected = total.toInt(), onSelect = onPreset)
    }
}

/** Shown instead of the panel when rest does not start automatically. */
@Composable
fun RestStarter(onStart: (Int) -> Unit, defaultSeconds: Int, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, title = stringResource(R.string.workout_start_rest)) {
        RestPresets(selected = defaultSeconds, onSelect = onStart, highlightSelected = false)
    }
}

@Composable
private fun RestPresets(selected: Int, onSelect: (Int) -> Unit, highlightSelected: Boolean = true) {
    Column {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppSettings.REST_PRESETS_SECONDS.forEach { seconds ->
                FilterChip(
                    selected = highlightSelected && seconds == selected,
                    onClick = { onSelect(seconds) },
                    label = { Text(Fmt.restLabel(seconds)) },
                )
            }
        }
    }
}
