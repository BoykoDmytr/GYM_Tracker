package com.boykodmytr.gymtracker.feature.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.transfer.CsvDialect
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportScreen(
    onBack: () -> Unit,
    viewModel: DataExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var pending by rememberSaveable { mutableStateOf<ExportKind?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val kind = pending
        pending = null
        if (uri != null && kind != null) viewModel.export(kind, uri)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                when (event) {
                    is ExportEvent.Saved -> resources.getString(R.string.export_done, event.rows)
                    ExportEvent.Failed -> resources.getString(R.string.export_failed)
                },
            )
        }
    }
    fun save(kind: ExportKind) {
        pending = kind
        saver.launch(viewModel.fileName(kind))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionCard(title = stringResource(R.string.export_format)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CsvDialect.entries.forEachIndexed { index, dialect ->
                        SegmentedButton(
                            selected = state.dialect == dialect,
                            onClick = { viewModel.setDialect(dialect) },
                            shape = SegmentedButtonDefaults.itemShape(index, CsvDialect.entries.size),
                        ) {
                            Text(stringResource(if (dialect == CsvDialect.SEMICOLON) R.string.export_format_semicolon else R.string.export_format_comma))
                        }
                    }
                }
                Hint(stringResource(R.string.export_format_hint))
            }
            ExportCard(stringResource(R.string.export_workouts), stringResource(R.string.export_workouts_hint), enabled = !state.busy) { save(ExportKind.WORKOUTS) }
            ExportCard(stringResource(R.string.export_body), stringResource(R.string.export_body_hint), enabled = !state.busy) { save(ExportKind.BODY) }
        }
    }
}

@Composable
private fun ExportCard(title: String, hint: String, enabled: Boolean, onSave: () -> Unit) {
    SectionCard(title = title) {
        Hint(hint)
        SecondaryButton(stringResource(R.string.export_save), onSave, Modifier.fillMaxWidth(), icon = Icons.Outlined.FileDownload, enabled = enabled)
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
