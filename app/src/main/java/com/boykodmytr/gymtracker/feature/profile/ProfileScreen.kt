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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.BuiltInMeasurementTypes
import com.boykodmytr.gymtracker.ui.components.BigTonalButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.format.Fmt
import com.boykodmytr.gymtracker.ui.format.MeasurementFormat
import com.boykodmytr.gymtracker.ui.theme.NumberTextStyle

private enum class ProfileDialog { EDIT, MEASURE, NEW_TYPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenMeasurement: (typeId: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<ProfileDialog?>(null) }
    val unit = state.weightUnit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.home_settings)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile") {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.profile.name.ifBlank { stringResource(R.string.profile_no_name) },
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { dialog = ProfileDialog.EDIT }) { Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit)) }
                    }
                    val facts = buildList {
                        state.age?.let { add(pluralStringResource(R.plurals.years_count, it, it)) }
                        state.profile.heightCm?.let { add(stringResource(R.string.profile_height_value, Fmt.oneDecimal(it))) }
                        state.rows.firstOrNull { it.type.id == BuiltInMeasurementTypes.WEIGHT }?.latest?.let {
                            add(Fmt.weightWithUnit(it.value, unit))
                        }
                    }
                    Text(
                        facts.joinToString(" · ").ifEmpty { stringResource(R.string.profile_fill_hint) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item(key = "measure") {
                BigTonalButton(stringResource(R.string.measurements_add), { dialog = ProfileDialog.MEASURE }, icon = Icons.Outlined.Straighten)
            }
            item(key = "params-title") {
                Text(stringResource(R.string.measurements_title), style = MaterialTheme.typography.titleMedium)
            }
            item(key = "params") {
                SectionCard {
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { onOpenMeasurement(row.type.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.type.name, style = MaterialTheme.typography.titleMedium)
                                row.latest?.let {
                                    Text(Fmt.dayMonthYear(it.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    row.latest?.let { MeasurementFormat.value(it.value, row.type.unit, unit) } ?: "—",
                                    style = MaterialTheme.typography.titleMedium.merge(NumberTextStyle),
                                )
                                row.delta?.let {
                                    Text(
                                        MeasurementFormat.delta(it, row.type.unit, unit),
                                        style = MaterialTheme.typography.bodySmall.merge(NumberTextStyle),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "new-type") {
                TextButton(onClick = { dialog = ProfileDialog.NEW_TYPE }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.measurements_new_type), Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    when (dialog) {
        ProfileDialog.EDIT -> EditProfileDialog(
            profile = state.profile,
            today = state.today,
            onSave = {
                dialog = null
                viewModel.saveProfile(it)
            },
            onDismiss = { dialog = null },
        )
        ProfileDialog.MEASURE -> AddMeasurementsSheet(
            types = state.rows.map { it.type },
            today = state.today,
            weightUnit = unit,
            onSave = { date, values ->
                dialog = null
                viewModel.addMeasurements(date, values)
            },
            onDismiss = { dialog = null },
        )
        ProfileDialog.NEW_TYPE -> AddMeasurementTypeDialog(
            onSave = { name, u ->
                dialog = null
                viewModel.addType(name, u)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
