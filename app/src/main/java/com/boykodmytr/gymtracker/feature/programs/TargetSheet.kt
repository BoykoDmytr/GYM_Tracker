package com.boykodmytr.gymtracker.feature.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.SetTarget
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.parseDecimal

/**
 * Bottom sheet (roomier than a dialog once the keyboard is up) for sets and reps as ranges
 * ("2–3 × 10–15"). For a fixed prescription enter the same number twice.
 * Weight and rest are optional: empty weight means "choose on the day", empty rest means the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSheet(
    title: String,
    initial: SetTarget?,
    initialRestSeconds: Int?,
    initialNotes: String,
    defaultRestSeconds: Int,
    weightUnit: WeightUnit,
    onConfirm: (SetTarget, Int?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var setsMin by rememberSaveable { mutableStateOf(initial?.setsMin?.toString().orEmpty()) }
    var setsMax by rememberSaveable { mutableStateOf(initial?.setsMax?.toString().orEmpty()) }
    var repsMin by rememberSaveable { mutableStateOf(initial?.repsMin?.toString().orEmpty()) }
    var repsMax by rememberSaveable { mutableStateOf(initial?.repsMax?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(initial?.weightKg?.let { Fmt.plain(weightUnit.fromKg(it)) }.orEmpty()) }
    var rest by rememberSaveable { mutableStateOf(initialRestSeconds?.toString().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }

    val sMin = setsMin.toIntOrNull()
    val sMax = setsMax.toIntOrNull() ?: sMin
    val rMin = repsMin.toIntOrNull()
    val rMax = repsMax.toIntOrNull() ?: rMin
    val weightValue = if (weight.isBlank()) null else parseDecimal(weight)
    val restValue = if (rest.isBlank()) null else rest.toIntOrNull()
    val target = if (sMin != null && sMax != null && rMin != null && rMax != null) {
        SetTarget(sMin, sMax, rMin, rMax, weightValue?.let(weightUnit::toKg))
    } else {
        null
    }
    val valid = target?.isValid == true && (weight.isBlank() || weightValue != null) && (rest.isBlank() || (restValue != null && restValue in 5..3600))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.target_sets), style = MaterialTheme.typography.labelLarge)
            RangeRow(setsMin, { setsMin = it.digits() }, setsMax, { setsMax = it.digits() })
            Text(stringResource(R.string.target_reps), style = MaterialTheme.typography.labelLarge)
            RangeRow(repsMin, { repsMin = it.digits() }, repsMax, { repsMax = it.digits() })
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(stringResource(R.string.target_weight, Fmt.weightUnitLabel(weightUnit))) },
                placeholder = { Text(stringResource(R.string.target_weight_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rest,
                onValueChange = { rest = it.digits() },
                label = { Text(stringResource(R.string.target_rest)) },
                placeholder = { Text(stringResource(R.string.target_rest_hint, defaultRestSeconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.target_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!valid && (setsMin.isNotEmpty() || repsMin.isNotEmpty())) {
                Text(stringResource(R.string.target_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            BigButton(
                text = stringResource(R.string.action_save),
                enabled = valid,
                onClick = { if (target != null) onConfirm(target, restValue, notes) },
            )
        }
    }
}

@Composable
private fun RangeRow(min: String, onMin: (String) -> Unit, max: String, onMax: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = min,
            onValueChange = onMin,
            label = { Text(stringResource(R.string.target_from)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = max,
            onValueChange = onMax,
            label = { Text(stringResource(R.string.target_to)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun String.digits() = filter(Char::isDigit).take(4)
