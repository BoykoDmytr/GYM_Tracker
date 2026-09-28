package com.boykodmytr.gymtracker.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.Measurement
import com.boykodmytr.gymtracker.domain.model.MeasurementType
import com.boykodmytr.gymtracker.domain.model.WeightUnit
import com.boykodmytr.gymtracker.ui.charts.ChartPoint
import com.boykodmytr.gymtracker.ui.charts.LineChart
import com.boykodmytr.gymtracker.ui.components.AppDatePickerDialog
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.EmptyState
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.MeasurementFormat
import com.boykodmytr.gymtracker.ui.format.parseDecimal
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    onBack: () -> Unit,
    viewModel: MeasurementDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.typeDeleted.collect { onBack() } }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteType by rememberSaveable { mutableStateOf(false) }
    val type = state.type
    val unit = state.weightUnit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (type != null && !type.isBuiltIn) {
                        IconButton(onClick = { confirmDeleteType = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add)) },
            )
        },
    ) { padding ->
        if (type == null) return@Scaffold
        val chronological = state.measurements.sortedBy { it.date }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chronological.isEmpty()) {
                item { EmptyState(stringResource(R.string.measurements_empty)) }
                return@LazyColumn
            }
            item(key = "chart") {
                SectionCard {
                    LineChart(
                        points = chronological.map {
                            ChartPoint(it.date.toEpochDay().toDouble(), MeasurementFormat.toDisplay(it.value, type.unit, unit), Fmt.shortDate(it.date))
                        },
                        valueFormatter = { "${Fmt.oneDecimal(it)} ${MeasurementFormat.unitLabel(type.unit, unit)}" },
                        description = type.name,
                    )
                    if (chronological.size > 1) {
                        val change = chronological.last().value - chronological.first().value
                        Text(
                            stringResource(
                                R.string.measurements_total_change,
                                MeasurementFormat.delta(change, type.unit, unit) + " " + MeasurementFormat.unitLabel(type.unit, unit),
                                Fmt.shortDate(chronological.first().date),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item(key = "history-title") { Text(stringResource(R.string.progress_history), style = MaterialTheme.typography.titleMedium) }
            item(key = "history") {
                SectionCard {
                    state.measurements.forEachIndexed { index, m ->
                        if (index > 0) HorizontalDivider()
                        val previous = state.measurements.getOrNull(index + 1)
                        MeasurementRowItem(m, previous, type, unit) { editingId = m.id }
                    }
                }
            }
        }
    }

    if (adding && type != null) {
        AddMeasurementsSheet(
            types = listOf(type),
            today = state.today,
            weightUnit = unit,
            onlyTypeId = type.id,
            onSave = { date, values ->
                adding = false
                values[type.id]?.let { viewModel.add(date, it) }
            },
            onDismiss = { adding = false },
        )
    }
    val editing = editingId?.let { id -> state.measurements.firstOrNull { it.id == id } }
    if (editing != null && type != null) {
        EditMeasurementDialog(
            measurement = editing,
            type = type,
            weightUnit = unit,
            today = state.today,
            onSave = {
                editingId = null
                viewModel.update(it)
            },
            onDelete = {
                editingId = null
                viewModel.delete(editing.id)
            },
            onDismiss = { editingId = null },
        )
    }
    if (confirmDeleteType) {
        ConfirmDialog(
            title = stringResource(R.string.measurements_delete_type_title),
            text = stringResource(R.string.measurements_delete_type_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDeleteType = false
                viewModel.deleteType()
            },
            onDismiss = { confirmDeleteType = false },
            destructive = true,
        )
    }
}

@Composable
private fun MeasurementRowItem(
    measurement: Measurement,
    previous: Measurement?,
    type: MeasurementType,
    unit: WeightUnit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.shortDate(measurement.date), style = MaterialTheme.typography.bodyLarge)
            if (measurement.note.isNotBlank()) {
                Text(measurement.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(MeasurementFormat.value(measurement.value, type.unit, unit), style = MaterialTheme.typography.titleMedium.merge(NumberTextStyle))
            previous?.let {
                Text(
                    MeasurementFormat.delta(measurement.value - it.value, type.unit, unit),
                    style = MaterialTheme.typography.bodySmall.merge(NumberTextStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EditMeasurementDialog(
    measurement: Measurement,
    type: MeasurementType,
    weightUnit: WeightUnit,
    today: LocalDate,
    onSave: (Measurement) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(Fmt.plain(MeasurementFormat.toDisplay(measurement.value, type.unit, weightUnit))) }
    var note by rememberSaveable { mutableStateOf(measurement.note) }
    var date by rememberSaveable { mutableStateOf(measurement.date) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val parsed = parseDecimal(value)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(type.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Text(Fmt.dayMonthYear(date), Modifier.padding(start = 8.dp))
                }
                OutlinedTextField(
                    value,
                    { value = it },
                    label = { Text(MeasurementFormat.unitLabel(type.unit, weightUnit)) },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.set_note)) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parsed?.let {
                        onSave(measurement.copy(value = MeasurementFormat.fromDisplay(it, type.unit, weightUnit), date = date, note = note))
                    }
                },
                enabled = parsed != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
