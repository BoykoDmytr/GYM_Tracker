package com.boykodmytr.gymtracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.SetInput
import com.boykodmytr.gymtracker.domain.model.SetLog
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.parseDecimal

private val RPE_VALUES = listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

/**
 * Result entry after a set. Weight and reps are front and centre; RPE, failure and a note are folded
 * away because most sets never need them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SetInputSheet(
    title: String,
    initial: SetInput,
    weightUnit: WeightUnit,
    onSave: (SetInput) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightText by rememberSaveable { mutableStateOf(Fmt.plain(weightUnit.fromKg(initial.weightKg))) }
    var repsText by rememberSaveable { mutableStateOf(if (initial.reps > 0) initial.reps.toString() else "") }
    var rpe by rememberSaveable { mutableStateOf(initial.rpe) }
    var failure by rememberSaveable { mutableStateOf(initial.isFailure) }
    var note by rememberSaveable { mutableStateOf(initial.note.orEmpty()) }
    var showMore by rememberSaveable { mutableStateOf(initial.rpe != null || initial.isFailure || !initial.note.isNullOrBlank()) }
    var showError by rememberSaveable { mutableStateOf(false) }

    val weight = parseDecimal(weightText)
    val reps = repsText.trim().toIntOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            NumberStepper(
                label = stringResource(R.string.set_weight, Fmt.weightUnitLabel(weightUnit)),
                value = weightText,
                onValueChange = { weightText = it; showError = false },
                onDecrement = { weightText = Fmt.plain(((weight ?: 0.0) - weightUnit.step).coerceAtLeast(0.0)) },
                onIncrement = { weightText = Fmt.plain((weight ?: 0.0) + weightUnit.step) },
                decrementLabel = "−${Fmt.number(weightUnit.step)}",
                incrementLabel = "+${Fmt.number(weightUnit.step)}",
                decimal = true,
                isError = showError && weight == null,
            )
            NumberStepper(
                label = stringResource(R.string.set_reps),
                value = repsText,
                onValueChange = { repsText = it.filter(Char::isDigit).take(3); showError = false },
                onDecrement = { repsText = ((reps ?: 1) - 1).coerceAtLeast(1).toString() },
                onIncrement = { repsText = ((reps ?: 0) + 1).toString() },
                decrementLabel = "−1",
                incrementLabel = "+1",
                isError = showError && reps == null,
            )
            if (showError) {
                Text(stringResource(R.string.set_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            TextButton(onClick = { showMore = !showMore }) {
                Text(stringResource(R.string.set_more_options))
                Icon(if (showMore) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (showMore) {
                Text(stringResource(R.string.set_rpe), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RPE_VALUES.forEach { value ->
                        FilterChip(
                            selected = rpe == value,
                            onClick = { rpe = if (rpe == value) null else value },
                            label = { Text(Fmt.rpe(value)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = failure, onCheckedChange = { failure = it })
                    Text(stringResource(R.string.set_failure))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.set_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BigButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    if (weight == null || reps == null) {
                        showError = true
                    } else {
                        onSave(SetInput(weightUnit.toKg(weight), reps, rpe, failure, note.takeIf { it.isNotBlank() }))
                    }
                },
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun SetLog.toInput() = SetInput(weightKg, reps, rpe, isFailure, note)
