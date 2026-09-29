package com.boykodmytr.gymtracker.feature.exercises

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.boykodmytr.gymtracker.domain.model.ExerciseDetails
import com.boykodmytr.gymtracker.domain.repository.DataTransferRepository
import com.boykodmytr.gymtracker.domain.repository.DeleteResult
import com.boykodmytr.gymtracker.domain.repository.ExerciseRepository
import com.boykodmytr.gymtracker.domain.repository.ImageImportException
import com.boykodmytr.gymtracker.domain.repository.MergePreview
import com.boykodmytr.gymtracker.ui.navigation.ExerciseEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseEditorUiState(
    val loading: Boolean = true,
    /** Null until a new exercise is saved for the first time. */
    val exerciseId: String? = null,
    val details: ExerciseDetails? = null,
    val importingImage: Boolean = false,
    /** Waiting for the user to confirm merging this exercise into another one. */
    val mergePreview: MergeRequest? = null,
)

data class MergeRequest(val targetId: String, val preview: MergePreview)

sealed interface ExerciseEditorEvent {
    data object Saved : ExerciseEditorEvent
    data object Deleted : ExerciseEditorEvent
    data class InUse(val templates: Int, val history: Int) : ExerciseEditorEvent
    data class Merged(val targetName: String, val entryId: String) : ExerciseEditorEvent
    data object MergeFailed : ExerciseEditorEvent

    /** [reason] is null for an unexpected error. */
    data class ImageFailed(val reason: ImageImportException.Reason?, val mimeType: String? = null) : ExerciseEditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val transferRepository: DataTransferRepository,
) : ViewModel() {

    private val exerciseId = MutableStateFlow(savedStateHandle.toRoute<ExerciseEditorRoute>().exerciseId)
    private val importing = MutableStateFlow(false)
    private val mergeRequest = MutableStateFlow<MergeRequest?>(null)

    /** The exercise that was merged away, so "undo" can show it again. */
    private var mergedFrom: String? = null

    private val _events = Channel<ExerciseEditorEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseEditorEvent> = _events.receiveAsFlow()

    val state: StateFlow<ExerciseEditorUiState> = combine(
        exerciseId.flatMapLatest { id -> if (id == null) flowOf(null) else exerciseRepository.observeExercise(id) },
        exerciseId,
        importing,
        mergeRequest,
    ) { details, id, importing, merge ->
        ExerciseEditorUiState(loading = false, exerciseId = id, details = details, importingImage = importing, mergePreview = merge)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseEditorUiState())

    fun save(name: String, notes: String) {
        viewModelScope.launch {
            val id = exerciseId.value
            if (id == null) {
                exerciseId.value = exerciseRepository.createExercise(name, notes)
            } else {
                exerciseRepository.updateExercise(id, name, notes)
            }
            _events.send(ExerciseEditorEvent.Saved)
        }
    }

    fun addImage(uri: String) {
        val id = exerciseId.value ?: return
        viewModelScope.launch {
            importing.value = true
            try {
                exerciseRepository.addImage(id, uri)
            } catch (e: ImageImportException) {
                Log.w(TAG, "Image import failed", e)
                _events.send(ExerciseEditorEvent.ImageFailed(e.reason, e.mimeType))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected image import error", e)
                _events.send(ExerciseEditorEvent.ImageFailed(reason = null))
            } finally {
                importing.value = false
            }
        }
    }

    /** Shows what merging into [targetId] would move, before anything changes. */
    fun requestMerge(targetId: String) {
        val id = exerciseId.value ?: return
        if (id == targetId) return
        viewModelScope.launch {
            try {
                mergeRequest.value = MergeRequest(targetId, transferRepository.previewMerge(id, targetId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Merge preview failed", e)
                _events.send(ExerciseEditorEvent.MergeFailed)
            }
        }
    }

    fun cancelMerge() {
        mergeRequest.value = null
    }

    fun confirmMerge() {
        val sourceId = exerciseId.value ?: return
        val request = mergeRequest.value ?: return
        mergeRequest.value = null
        viewModelScope.launch {
            try {
                val entry = transferRepository.mergeExercises(sourceId, request.targetId)
                // The source no longer exists: keep editing the exercise that now holds the history.
                exerciseId.value = request.targetId
                mergedFrom = sourceId
                _events.send(ExerciseEditorEvent.Merged(request.preview.targetName, entry.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Merge failed", e)
                _events.send(ExerciseEditorEvent.MergeFailed)
            }
        }
    }

    fun undoMerge(entryId: String) {
        viewModelScope.launch {
            try {
                transferRepository.undo(entryId)
                mergedFrom?.let { exerciseId.value = it }
                mergedFrom = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Undo merge failed", e)
                _events.send(ExerciseEditorEvent.MergeFailed)
            }
        }
    }

    fun deleteImage(imageId: String) {
        viewModelScope.launch { exerciseRepository.deleteImage(imageId) }
    }

    fun delete() {
        val id = exerciseId.value ?: return
        viewModelScope.launch {
            when (val result = exerciseRepository.deleteExercise(id)) {
                DeleteResult.Deleted -> _events.send(ExerciseEditorEvent.Deleted)
                is DeleteResult.InUse -> _events.send(ExerciseEditorEvent.InUse(result.templateUsages, result.historyUsages))
            }
        }
    }
}

private const val TAG = "ExerciseEditor"
