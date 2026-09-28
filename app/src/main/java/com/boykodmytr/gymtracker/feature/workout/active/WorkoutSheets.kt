package com.boykodmytr.gymtracker.feature.workout.active

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.boykodmytr.gymtracker.R
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import com.boykodmytr.gymtracker.domain.model.ExerciseStatus
import com.boykodmytr.gymtracker.domain.model.SessionExercise
import java.io.File

/** All exercises of the workout with their status; tapping one jumps to it (e.g. the rack is busy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListSheet(
    exercises: List<SessionExercise>,
    currentId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.workout_exercises),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(exercises, key = { it.id }) { exercise ->
                val isCurrent = exercise.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(exercise.id) }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val (icon, tint) = when {
                        isCurrent -> Icons.Filled.PlayCircle to MaterialTheme.colorScheme.primary
                        exercise.status == ExerciseStatus.SKIPPED -> Icons.Outlined.RemoveCircleOutline to MaterialTheme.colorScheme.outline
                        exercise.isClosed -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                        else -> Icons.Outlined.Circle to MaterialTheme.colorScheme.outline
                    }
                    Icon(icon, contentDescription = null, tint = tint)
                    Column(Modifier.weight(1f)) {
                        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
                        val status = when {
                            isCurrent -> stringResource(R.string.workout_status_current)
                            exercise.status == ExerciseStatus.SKIPPED -> stringResource(R.string.workout_status_skipped)
                            exercise.isClosed -> stringResource(R.string.workout_status_done)
                            else -> null
                        }
                        if (status != null) {
                            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        stringResource(R.string.workout_sets_progress, exercise.sets.size, exercise.target.setsMax),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Technique reminder: the user's own notes and photos for the exercise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechniqueSheet(
    details: ExerciseDetails?,
    onEdit: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    details?.exercise?.name ?: stringResource(R.string.workout_technique),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            val notes = details?.exercise?.notes.orEmpty()
            val images = details?.images.orEmpty()
            if (notes.isBlank() && images.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.workout_no_technique),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            if (notes.isNotBlank()) {
                item { Text(notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp)) }
            }
            items(images, key = { it.id }) { image ->
                AsyncImage(
                    model = File(image.path),
                    contentDescription = details?.exercise?.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            if (onEdit != null) {
                item {
                    TextButton(onClick = onEdit, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(stringResource(R.string.workout_edit_exercise))
                    }
                }
            }
        }
    }
}
