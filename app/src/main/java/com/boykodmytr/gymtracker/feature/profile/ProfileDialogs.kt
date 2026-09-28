package com.boykodmytr.gymtracker.feature.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.MeasurementUnit
import com.boykodmytr.gymtracker.domain.model.UserProfile
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.components.AppDatePickerDialog
import com.boykodmytr.gymtracker.ui.components.BigButton
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.MeasurementFormat
import com.boykodmytr.gymtracker.ui.format.parseDecimal
import java.time.LocalDate

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    today: LocalDate,
    onSave: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var height by rememberSaveable { mutableStateOf(profile.heightCm?.let(Fmt::plain).orEmpty()) }
    var birthDate by rememberSaveable { mutableStateOf(profile.birthDate) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val heightValue = if (height.isBlank()) null else parseDecimal(height)
    val valid = height.isBlank() || (heightValue != null && heightValue in 50.0..260.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Text(
                        birthDate?.let { stringResource(R.string.profile_birth_date_value, Fmt.dayMonthYear(it)) }
                            ?: stringResource(R.string.profile_birth_date),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedTextField(
                    height,
                    { height = it },
                    label = { Text(stringResource(R.string.profile_height)) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(UserProfile(name, birthDate, heightValue)) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingDate) {
        AppDatePickerDialog(
            initial = birthDate ?: today.minusYears(30),
            maxDate = today,
            onConfirm = {
                birthDate = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/**
 * One "measuring day": pick the date once, fill in only what was measured. Empty fields are skipped,
 * so each parameter keeps its own history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementsSheet(
    types: List<MeasurementType>,
    today: LocalDate,
    weightUnit: WeightUnit,
    onSave: (LocalDate, Map<String, Double>) -> Unit,
    onDismiss: () -> Unit,
    onlyTypeId: String? = null,
) {
    val values = remember { mutableStateMapOf<String, String>() }
    var date by rememberSaveable { mutableStateOf(today) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val visible = types.filter { onlyTypeId == null || it.id == onlyTypeId }
    val parsed = visible.mapNotNull { type ->
        val raw = values[type.id].orEmpty()
        if (raw.isBlank()) null else type to parseDecimal(raw)
    }
    val valid = parsed.isNotEmpty() && parsed.all { (_, v) -> v != null && v > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.measurements_add), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Text(Fmt.dayMonthYear(date), modifier = Modifier.padding(start = 8.dp))
            }
            visible.forEach { type ->
                OutlinedTextField(
                    value = values[type.id].orEmpty(),
                    onValueChange = { values[type.id] = it },
                    label = { Text("${type.name}, ${MeasurementFormat.unitLabel(type.unit, weightUnit)}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BigButton(
                text = stringResource(R.string.action_save),
                enabled = valid,
                onClick = {
                    onSave(
                        date,
                        parsed.associate { (type, v) -> type.id to MeasurementFormat.fromDisplay(v!!, type.unit, weightUnit) },
                    )
                },
            )
        }
    }
    if (pickingDate) {
        AppDatePickerDialog(
            initial = date,
            maxDate = today,
            onConfirm = {
                date = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
fun AddMeasurementTypeDialog(onSave: (String, MeasurementUnit) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(MeasurementUnit.CM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measurements_new_type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.exercise_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasurementUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(MeasurementFormat.unitLabel(u, WeightUnit.KG)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), unit) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
