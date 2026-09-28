package com.boykodmytr.gymtracker.feature.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.ui.components.SectionCard
import com.boykodmytr.gymtracker.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    onOpenProgram: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.created.collect(onOpenProgram) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_programs)) },
                actions = {
                    TextButton(onClick = onOpenLibrary) {
                        Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = null)
                        Text(stringResource(R.string.programs_library), Modifier.padding(start = 8.dp))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.programs_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(programs.orEmpty(), key = { it.id }) { program ->
                SectionCard(
                    onClick = { onOpenProgram(program.id) },
                    containerColor = if (program.isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(program.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (program.isActive) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.programs_active), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (program.description.isNotBlank()) Text(program.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        program.workouts.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.programs_new),
            label = stringResource(R.string.programs_name),
            secondaryLabel = stringResource(R.string.programs_description),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { name, description ->
                creating = false
                viewModel.createProgram(name, description)
            },
            onDismiss = { creating = false },
        )
    }
}
