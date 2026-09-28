package com.boykodmytr.gymtracker.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.core.timer.StandaloneTimerState
import com.boykodmytr.gymtracker.core.timer.TimerMode
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.rememberNow
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.theme.TimerTextStyle

private val COUNTDOWN_PRESETS = listOf(30, 60, 90, 120, 180, 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onBack: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TimerMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, TimerMode.entries.size),
                    ) {
                        Text(stringResource(if (mode == TimerMode.COUNTDOWN) R.string.timer_countdown else R.string.timer_stopwatch))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            when (state.mode) {
                TimerMode.COUNTDOWN -> Countdown(state, viewModel)
                TimerMode.STOPWATCH -> Stopwatch(state, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Countdown(state: StandaloneTimerState, viewModel: TimerViewModel) {
    val now by rememberNow(200)
    val remaining = state.countdownRemainingMillis(now)
    Text(
        Fmt.clockSeconds((remaining + 999) / 1000),
        style = TimerTextStyle.copy(fontSize = 88.sp, lineHeight = 92.sp),
        textAlign = TextAlign.Center,
    )
    val idle = !state.countdownRunning && !state.countdownPaused
    if (idle) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            COUNTDOWN_PRESETS.forEach { seconds ->
                FilterChip(
                    selected = state.countdownSeconds == seconds,
                    onClick = { viewModel.setCountdownSeconds(seconds) },
                    label = { Text(Fmt.restLabel(seconds)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("−15 с", { viewModel.setCountdownSeconds(state.countdownSeconds - 15) }, Modifier.weight(1f))
            SecondaryButton("+15 с", { viewModel.setCountdownSeconds(state.countdownSeconds + 15) }, Modifier.weight(1f))
        }
    }
    if (state.countdownRunning) {
        BigButton(stringResource(R.string.timer_pause), viewModel::pauseCountdown, icon = Icons.Filled.Pause)
    } else {
        BigButton(
            stringResource(if (state.countdownPaused) R.string.timer_resume else R.string.timer_start),
            viewModel::startCountdown,
            icon = Icons.Filled.PlayArrow,
        )
    }
    if (!idle) {
        SecondaryButton(stringResource(R.string.timer_reset), viewModel::resetCountdown, Modifier.fillMaxWidth(), icon = Icons.Filled.Refresh)
    }
    Text(
        stringResource(R.string.timer_background_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Stopwatch(state: StandaloneTimerState, viewModel: TimerViewModel) {
    val now by rememberNow(100)
    val elapsed = state.stopwatchElapsedMillis(now)
    Text(
        Fmt.clockSeconds(elapsed / 1000) + ".%d".format((elapsed % 1000) / 100),
        style = TimerTextStyle.copy(fontSize = 80.sp, lineHeight = 84.sp),
        textAlign = TextAlign.Center,
    )
    if (state.stopwatchRunning) {
        BigButton(stringResource(R.string.timer_pause), viewModel::pauseStopwatch, icon = Icons.Filled.Pause)
    } else {
        BigButton(
            stringResource(if (elapsed > 0) R.string.timer_resume else R.string.timer_start),
            viewModel::startStopwatch,
            icon = Icons.Filled.PlayArrow,
        )
    }
    if (elapsed > 0) {
        SecondaryButton(stringResource(R.string.timer_reset), viewModel::resetStopwatch, Modifier.fillMaxWidth(), icon = Icons.Filled.Refresh)
    }
}
