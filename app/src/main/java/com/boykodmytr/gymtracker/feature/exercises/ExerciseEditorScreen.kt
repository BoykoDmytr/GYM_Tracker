package com.boykodmytr.gymtracker.feature.exercises

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.ui.components.ConfirmDialog
import com.boykodmytr.gymtracker.ui.components.SecondaryButton
import com.boykodmytr.gymtracker.ui.components.SectionCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(
    onBack: () -> Unit,
    onOpenProgress: (String) -> Unit,
    viewModel: ExerciseEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }

    val details = state.details
    LaunchedEffect(details?.exercise?.id) {
        if (!initialized && details != null) {
            name = details.exercise.name
            notes = details.exercise.notes
            initialized = true
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ExerciseEditorEvent.Saved -> snackbar.showSnackbar(context.getString(R.string.exercise_saved))
                ExerciseEditorEvent.Deleted -> onBack()
                ExerciseEditorEvent.ImageFailed -> snackbar.showSnackbar(context.getString(R.string.exercise_image_failed))
                is ExerciseEditorEvent.InUse -> snackbar.showSnackbar(
                    context.getString(R.string.exercise_in_use, event.templates, event.history),
                )
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addImage(uri.toString())
    }
    val isNew = state.exerciseId == null
    val changed = name.trim() != details?.exercise?.name.orEmpty() || notes.trim() != details?.exercise?.notes.orEmpty()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.exercise_new else R.string.exercise_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { onOpenProgress(state.exerciseId!!) }) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, stringResource(R.string.session_exercise_progress))
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                    TextButton(onClick = { viewModel.save(name, notes) }, enabled = name.isNotBlank() && (isNew || changed)) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.exercise_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.exercise_notes)) },
                placeholder = { Text(stringResource(R.string.exercise_notes_hint)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionCard(title = stringResource(R.string.exercise_photos)) {
                if (isNew) {
                    Text(stringResource(R.string.exercise_photos_save_first), style = MaterialTheme.typography.bodyMedium)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        details?.images.orEmpty().forEach { image ->
                            Box(Modifier.size(104.dp)) {
                                AsyncImage(
                                    model = File(image.path),
                                    contentDescription = stringResource(R.string.exercise_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { viewing = image.path },
                                )
                                FilledTonalIconButton(
                                    onClick = { viewModel.deleteImage(image.id) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                                ) { Icon(Icons.Filled.Close, stringResource(R.string.exercise_photo_delete)) }
                            }
                        }
                        if (state.importingImage) {
                            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                    SecondaryButton(
                        stringResource(R.string.exercise_add_photo),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Filled.AddPhotoAlternate,
                    )
                }
            }
        }
    }

    viewing?.let { path ->
        Dialog(onDismissRequest = { viewing = null }) {
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.exercise_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { viewing = null },
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.exercise_delete_title),
            text = stringResource(R.string.exercise_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
